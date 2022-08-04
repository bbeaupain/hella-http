package sh.hella.http;

import lombok.Builder;
import lombok.Data;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Data
@Builder
public class Request {
    private final String method;
    private final String path;
    private final Map<String, List<String>> parameters;
    private final String protocol;
    private final Map<String, String> headers;
    private final ByteBuffer body;
    private Consumer<ByteBuffer> chunkHandler;
}
