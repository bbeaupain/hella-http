package sh.hella.http.codec;

import sh.hella.http.Response;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ResponseEncoder {
    private static final Map<Integer, byte[]> ENCODED_STATUSES = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> ENCODED_HEADER_KEYS = new ConcurrentHashMap<>();
    private static final byte[] HTTP_VERSION = "HTTP/1.1 ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CARRIAGE_RETURN = "\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SERVER_HEADER = "Server: hella-http\r\n".getBytes(StandardCharsets.UTF_8);

    public static void encode(Response response, ByteBuffer buffer) {
        final byte[] statusBytes = ENCODED_STATUSES
                .computeIfAbsent(response.getStatus(), k -> (response.getStatus() + "\r\n").getBytes(StandardCharsets.UTF_8));

        // Encode the response status line
        buffer
            .put(HTTP_VERSION)
            .put(statusBytes);

        // Encode any response headers
        if (!response.getHeaders().isEmpty()) {
            buffer.put(SERVER_HEADER);
            for (Map.Entry<String, String> entry : response.getHeaders().entrySet()) {
                final byte[] encodedKey = ENCODED_HEADER_KEYS
                        .computeIfAbsent(entry.getKey(), k -> (entry.getKey() + ": ").getBytes(StandardCharsets.UTF_8));
                buffer
                    .put(encodedKey)
                    .put(entry.getValue().getBytes(StandardCharsets.UTF_8))
                    .put(CARRIAGE_RETURN);
            }
        } else {
            buffer.put(CARRIAGE_RETURN);
        }

        // And finally encode the body
        buffer
            .put(CARRIAGE_RETURN)
            .put(response.getBody())
            .put(CARRIAGE_RETURN)
            .flip();
    }
}
