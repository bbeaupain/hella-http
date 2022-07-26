package sh.hella.http;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.Map;

@Data
@Builder
public class Response {
    private final int status;
    private final String reason;
    @Singular private final Map<String, String> headers;
    private final byte[] body;
}
