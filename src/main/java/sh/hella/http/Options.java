package sh.hella.http;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

@Data
@Builder
public class Options {
    private final String host;
    private final int port;
    private final int requestBufferSize;
    private final int responseBufferSize;
}
