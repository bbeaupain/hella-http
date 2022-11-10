package sh.hella.http;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Options {
    @Builder.Default private final String host = "0.0.0.0";
    @Builder.Default private final int port = 8080;
    @Builder.Default private final int requestBufferSize = 64 * 1024;
    @Builder.Default private final int responseBufferSize = 64 * 1024;
    @Builder.Default private final int threads = Runtime.getRuntime().availableProcessors();
    @Builder.Default private final int ttl = 60000;
}
