package sh.hella.http.codec;

import sh.hella.http.Response;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ResponseEncoder {
    private static final Map<Integer, byte[]> ENCODED_STATUSES = new HashMap<>();
    private static final Map<String, byte[]> ENCODED_HEADER_KEYS = new HashMap<>();
    private static final byte[] HTTP_VERSION = "HTTP/1.1 ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CARRIAGE_RETURN = "\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SERVER_HEADER = "Server: hella-http\r\n".getBytes(StandardCharsets.UTF_8);

    public static void encode(Response response, ByteBuffer buffer) {
        if (!ENCODED_STATUSES.containsKey(response.getStatus())) {
            byte[] statusBytes = (response.getStatus() + "\r\n").getBytes(StandardCharsets.UTF_8);
            ENCODED_STATUSES.put(response.getStatus(), statusBytes);
        }

        // Encode the response status line
        buffer
            .put(HTTP_VERSION)
            .put(ENCODED_STATUSES.get(response.getStatus()));

        // Encode any response headers
        if (!response.getHeaders().isEmpty()) {
            buffer.put(SERVER_HEADER);
            for (Map.Entry<String, String> entry : response.getHeaders().entrySet()) {
                if (!ENCODED_HEADER_KEYS.containsKey(entry.getKey())) {
                    byte[] encodedKey = (entry.getKey() + ": ").getBytes(StandardCharsets.UTF_8);
                    ENCODED_HEADER_KEYS.put(entry.getKey(), encodedKey);
                }
                buffer
                    .put(ENCODED_HEADER_KEYS.get(entry.getKey()))
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
