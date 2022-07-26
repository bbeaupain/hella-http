package sh.hella.http;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import sh.hella.http.codec.RequestDecoder;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class RequestParserTest {

    @Test
    public void shouldParseSimpleRequestLine() {
        ByteBuffer buffer = wrap("GET /test HTTP/1.1\r\n\r\n");
        RequestDecoder parser = new RequestDecoder();
        Request request = parser.accept(buffer);
        Assertions.assertEquals("GET", request.getMethod());
        Assertions.assertEquals("HTTP/1.1", request.getProtocol());
        Assertions.assertEquals("/test", request.getPath());
    }

    @Test
    public void shouldParseHeaders() {
        ByteBuffer buffer = wrap("GET /test HTTP/1.1\r\nContent-Length: 0\r\n\r\n");
        RequestDecoder parser = new RequestDecoder();
        Request request = parser.accept(buffer);
        Assertions.assertEquals("GET", request.getMethod());
        Assertions.assertEquals("HTTP/1.1", request.getProtocol());
        Assertions.assertEquals("/test", request.getPath());
        Assertions.assertEquals("0", request.getHeaders().get("Content-Length"));
    }

    @Test
    public void shouldParseParameters() {
        ByteBuffer buffer = wrap("GET /test?foo=bar HTTP/1.1\r\n\r\n");
        RequestDecoder parser = new RequestDecoder();
        Request request = parser.accept(buffer);
        Assertions.assertEquals("GET", request.getMethod());
        Assertions.assertEquals("HTTP/1.1", request.getProtocol());
        Assertions.assertEquals("/test", request.getPath());
        Assertions.assertEquals("bar", request.getParameters().get("foo").get(0));
    }

    @Test
    public void shouldParseBody() {
        ByteBuffer buffer = wrap("POST /test?foo=bar HTTP/1.1\r\nContent-Length: 12\r\n\r\nHello, world!");
        RequestDecoder parser = new RequestDecoder();
        Request request = parser.accept(buffer);
        Assertions.assertEquals("POST", request.getMethod());
        Assertions.assertEquals("HTTP/1.1", request.getProtocol());
        Assertions.assertEquals("/test", request.getPath());
        Assertions.assertEquals("bar", request.getParameters().get("foo").get(0));
        Assertions.assertEquals("Hello, world!", StandardCharsets.UTF_8.decode(request.getBody()).toString());
    }

    @Test
    public void shouldNotParseMalformedRequestLine() {
        ByteBuffer buffer = wrap("GET HTTP/1.1");
        RequestDecoder parser = new RequestDecoder();
        Assertions.assertThrows(BufferUnderflowException.class, () -> parser.accept(buffer));
    }

    private ByteBuffer wrap(String utf8) {
        byte[] data = utf8.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocateDirect(data.length).put(data);
    }
}
