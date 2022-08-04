package sh.hella.http.codec;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import sh.hella.http.Request;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class RequestDecoder {
    private static final int AVERAGE_PATH_LENGTH_HINT = 256;
    private static final String CONTENT_LENGTH = "Content-Length";
    private static final String TRANSFER_ENCODING = "Transfer-Encoding";
    private static final String CHUNKED = "chunked";

    private Request.RequestBuilder requestBuilder = Request.builder();
    private int contentLength = 0;
    private Request request;

    @Getter
    private State state = State.REQUEST_LINE;

    public Request decode(ByteBuffer buffer) {
        if (state == State.DONE) {
            requestBuilder = Request.builder();
            contentLength = 0;
            state = State.REQUEST_LINE;
        }
        try {
            loop: while (buffer.hasRemaining()) {
                buffer.mark();
                switch (state) {
                    case REQUEST_LINE -> {
                        decodeMethod(buffer);
                        decodePathAndParameters(buffer);
                        decodeProtocol(buffer);
                        state = State.HEADERS;
                    }
                    case HEADERS -> {
                        Map<String, String> headers = decodeHeaders(buffer);
                        determineBodyType(headers);
                        requestBuilder.headers(headers);
                        if (state == State.CHUNKED_BODY) {
                            requestBuilder.body(ByteBuffer.allocateDirect(buffer.capacity()));
                            request = requestBuilder.build();
                            break loop;
                        }
                    }
                    case BODY -> {
                        ByteBuffer body = decodeBody(buffer);
                        if (body == null) {
                            buffer.reset();
                            return null;
                        }
                        requestBuilder.body(body);
                        state = State.DONE;
                        break loop;
                    }
                    case CHUNKED_BODY -> {
                        ByteBuffer chunk = decodeChunkedBody(buffer);
                        if (chunk == null) {
                            buffer.reset();
                            return null;
                        }
                        if (request.getChunkHandler() != null) {
                            request.getChunkHandler().accept(chunk);
                        }
                    }
                }
            }
        } catch (BufferUnderflowException ex) {
            buffer.reset();
            return null;
        }
        if (state == State.CHUNKED_BODY) {
            return request;
        } else if (state == State.DONE) {
            request = requestBuilder.build();
            contentLength = 0;
            return request;
        } else {
            return null;
        }
    }

    private void decodeMethod(ByteBuffer buffer) {
        String method = switch (buffer.get(buffer.position())) {
            case 'G' -> "GET";
            case 'H' -> "HEAD";
            case 'P' -> switch (buffer.get(buffer.position() + 1)) {
                case 'O' -> "POST";
                case 'U' -> "PUT";
                case 'P' -> "PATCH";
                default -> throw new RuntimeException("Unable to decode method");
            };
            case 'D' -> "DELETE";
            case 'O' -> "OPTIONS";
            case 'T' -> "TRACE";
            default -> throw new RuntimeException("Unable to decode method");
        };
        skip(buffer, method.length() + 1);
        requestBuilder.method(method);
    }

    private void decodePathAndParameters(ByteBuffer buffer) {
        StringBuilder pathBuilder = new StringBuilder(AVERAGE_PATH_LENGTH_HINT);
        StringBuilder paramKeyBuilder = null, paramValBuilder = null;
        Map<String, List<String>> parameters = new HashMap<>();
        PathState pathState = PathState.PATH;
        loop: while (true) {
            int val = buffer.get();
            switch (pathState) {
                case PATH -> {
                    if (val == ' ') {
                        requestBuilder.path(pathBuilder.toString());
                        break loop;
                    } else if (val == '?') {
                        requestBuilder.path(pathBuilder.toString());
                        paramKeyBuilder = new StringBuilder();
                        pathState = PathState.PARAM_KEY;
                    } else {
                        pathBuilder.append((char) val);
                    }
                }
                case PARAM_KEY -> {
                    if (val == ' ') {
                        break loop;
                    } else if (val == '=') {
                        paramValBuilder = new StringBuilder();
                        pathState = PathState.PARAM_VAL;
                    } else {
                        paramKeyBuilder.append((char) val);
                    }
                }
                case PARAM_VAL -> {
                    if (val == '&' || val == ' ') {
                        parameters
                            .computeIfAbsent(paramKeyBuilder.toString(), k -> new ArrayList<>())
                            .add(paramValBuilder.toString());
                        if (val == ' ') {
                            break loop;
                        }
                        pathState = PathState.PARAM_KEY;
                        paramKeyBuilder = new StringBuilder();
                    } else {
                        paramValBuilder.append((char) val);
                    }
                }
            }
        }
        requestBuilder.parameters(parameters);
    }

    private void decodeProtocol(ByteBuffer buffer) {
        String protocol = switch (buffer.get(buffer.position() + 5)) {
            case '1' -> "HTTP/1." + (char) (buffer.get(buffer.position() + 7));
            case '2' -> "HTTP/2.0";
            default -> throw new RuntimeException("Unable to decode protocol");
        };
        skip(buffer, protocol.length());
        requestBuilder.protocol(protocol);
        int val = buffer.get();
        while (val != '\n') {
            val = buffer.get();
        }
    }

    private Map<String, String> decodeHeaders(ByteBuffer buffer) {
        StringBuilder headerKeyBuilder = new StringBuilder(), headerValBuilder = null;
        Map<String, String> headers = new HashMap<>();
        HeaderState headerState = HeaderState.HEADER_KEY;
        for (int val = buffer.get(); !(val == '\n' && headerState == HeaderState.HEADER_KEY); val = buffer.get()) {
            switch (headerState) {
                case HEADER_KEY -> {
                    if (val == ':') {
                        headerValBuilder = new StringBuilder();
                        headerState = HeaderState.HEADER_VAL;
                    } else {
                        headerKeyBuilder.append((char) val);
                    }
                }
                case HEADER_VAL -> {
                    if (val == '\n') {
                        headers.put(headerKeyBuilder.toString(), headerValBuilder.toString());
                        headerKeyBuilder = new StringBuilder();
                        headerState = HeaderState.HEADER_KEY;
                    } else if (val != '\r') {
                        if (val == ' ' && headerValBuilder.length() == 0) {
                            continue; // skip first whitespace
                        }
                        headerValBuilder.append((char) val);
                    }
                }
            }
        }
        return headers;
    }

    private void determineBodyType(Map<String, String> headers) {
        if (headers.containsKey(CONTENT_LENGTH)) {
            contentLength = Integer.parseInt(headers.get(CONTENT_LENGTH));
            state = State.BODY;
        } else if (headers.containsKey(TRANSFER_ENCODING)
                   && headers.get(TRANSFER_ENCODING).equals(CHUNKED)) {
            state = State.CHUNKED_BODY;
        } else {
            state = State.DONE;
        }
    }

    private ByteBuffer decodeBody(ByteBuffer buffer) {
        if (contentLength != 0 && !hasRemainingExcludingNewline(buffer, contentLength)) {
            return null;
        }
        ByteBuffer body = buffer.slice().limit(contentLength);
        skip(buffer, contentLength);
        return body;
    }

    private ByteBuffer decodeChunkedBody(ByteBuffer buffer) {
        int chunkSize = Integer.parseInt(readLine(buffer));
        if (chunkSize == 0) {
            state = State.DONE;
            return null;
        }
        if (chunkSize > buffer.capacity()) {
            throw new IllegalArgumentException("Chunk size cannot be greater than requestBufferSize");
        }
        if (!hasRemainingExcludingNewline(buffer, chunkSize)) {
            return null;
        }
        ByteBuffer chunk = buffer.slice().limit(chunkSize);
        skip(buffer, chunkSize + 2);
        return chunk;
    }

    private String readLine(ByteBuffer buffer) {
        StringBuilder builder = new StringBuilder();
        for (int val = buffer.get(); val != '\n'; val = buffer.get()) {
            if (val != '\r') {
                builder.append((char) val);
            }
        }
        return builder.toString();
    }

    private boolean hasRemainingExcludingNewline(ByteBuffer buffer, int amount) {
        return buffer.remaining() >= amount + 2; // + 2 for \r\n
    }

    private void skip(ByteBuffer buffer, int amount) {
        buffer.position(buffer.position() + amount);
    }

    public enum State {
        REQUEST_LINE,
        HEADERS,
        BODY,
        CHUNKED_BODY,
        DONE,
    }

    private enum PathState {
        PATH,
        PARAM_KEY,
        PARAM_VAL,
    }

    private enum HeaderState {
        HEADER_KEY,
        HEADER_VAL,
    }
}
