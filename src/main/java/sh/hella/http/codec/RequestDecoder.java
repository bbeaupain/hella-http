package sh.hella.http.codec;

import lombok.RequiredArgsConstructor;
import sh.hella.http.Request;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@RequiredArgsConstructor
public class RequestDecoder {
    private static final int AVERAGE_PATH_LENGTH_HINT = 256;
    private State parserState = State.REQUEST_LINE;
    private int contentLength = 0;

    public Request accept(ByteBuffer buffer) {
        Request.RequestBuilder requestBuilder = Request.builder();
        try {
            buffer.flip();
            loop: while (buffer.hasRemaining()) {
                switch (parserState) {
                    case REQUEST_LINE -> {
                        buffer.mark();
                        decodeMethod(buffer, requestBuilder);
                        decodePath(buffer, requestBuilder);
                        decodeProtocol(buffer, requestBuilder);
                        parserState = State.HEADERS;
                    }
                    case HEADERS -> {
                        buffer.mark();
                        decodeHeaders(buffer, requestBuilder);
                        parserState = State.BODY;
                    }
                    case BODY -> {
                        if (buffer.remaining() >= contentLength) {
                            requestBuilder.body(buffer.slice());
                            parserState = State.DONE;
                            break loop;
                        }
                    }
                }
            }
        } catch (BufferUnderflowException ex) {
            buffer.reset();
            throw ex;
        }
        return requestBuilder.build();
    }

    private void decodeMethod(ByteBuffer buffer, Request.RequestBuilder requestBuilder) {
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
        buffer.position(buffer.position() + method.length() + 1);
        requestBuilder.method(method);
    }

    private void decodePath(ByteBuffer buffer, Request.RequestBuilder requestBuilder) {
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

    private void decodeProtocol(ByteBuffer buffer, Request.RequestBuilder requestBuilder) {
        String protocol = switch (buffer.get(buffer.position() + 5)) {
            case '1' -> "HTTP/1." + (char) (buffer.get(buffer.position() + 7));
            case '2' -> "HTTP/2.0";
            default -> throw new RuntimeException("Unable to decode protocol");
        };
        buffer.position(buffer.position() + protocol.length());
        requestBuilder.protocol(protocol);
        int val = buffer.get();
        while (val != '\n') {
            val = buffer.get();
        }
    }

    private void decodeHeaders(ByteBuffer buffer, Request.RequestBuilder requestBuilder) {
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
                    } else if (val != ' ' && val != '\r') {
                        headerValBuilder.append((char) val);
                    }
                }
            }
        }
        if (headers.containsKey("Content-Length")) {
            this.contentLength = Integer.parseInt(headers.get("Content-Length"));
        }
        requestBuilder.headers(headers);
    }

    private enum State {
        REQUEST_LINE,
        HEADERS,
        BODY,
        DONE
    }

    private enum PathState {
        PATH,
        PARAM_KEY,
        PARAM_VAL
    }

    private enum HeaderState {
        HEADER_KEY,
        HEADER_VAL
    }
}