package sh.hella.http;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import sh.blake.niouring.util.ByteBufferUtil;
import sh.hella.http.codec.RequestDecoder;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class RequestParserTest {

    @Test
    public void shouldParseSimpleRequestLine() {
        ByteBuffer buffer = ByteBufferUtil.wrapDirect("GET /test HTTP/1.1\r\n\r\n");
        RequestDecoder decoder = new RequestDecoder();
        Request request = decoder.decode(buffer);
        Assertions.assertEquals("GET", request.getMethod());
        Assertions.assertEquals("HTTP/1.1", request.getProtocol());
        Assertions.assertEquals("/test", request.getPath());
    }

    @Test
    public void shouldParseHeaders() {
        ByteBuffer buffer = ByteBufferUtil.wrapDirect("GET /test HTTP/1.1\r\nContent-Length: 0\r\n\r\n");
        RequestDecoder decoder = new RequestDecoder();
        Request request = decoder.decode(buffer);
        Assertions.assertEquals("GET", request.getMethod());
        Assertions.assertEquals("HTTP/1.1", request.getProtocol());
        Assertions.assertEquals("/test", request.getPath());
        Assertions.assertEquals("0", request.getHeaders().get("Content-Length"));
    }

    @Test
    public void shouldParseMultipleHeaders() {
        ByteBuffer buffer = ByteBufferUtil.wrapDirect("GET /test HTTP/1.1\r\nContent-Length: 0\r\nKeep-Alive: timeout=5, max=1000\r\n\r\n");
        RequestDecoder decoder = new RequestDecoder();
        Request request = decoder.decode(buffer);
        Assertions.assertEquals("GET", request.getMethod());
        Assertions.assertEquals("HTTP/1.1", request.getProtocol());
        Assertions.assertEquals("/test", request.getPath());
        Assertions.assertEquals("0", request.getHeaders().get("Content-Length"));
        Assertions.assertEquals("timeout=5, max=1000", request.getHeaders().get("Keep-Alive"));
    }

    @Test
    public void shouldParseParameters() {
        ByteBuffer buffer = ByteBufferUtil.wrapDirect("GET /test?foo=bar HTTP/1.1\r\n\r\n");
        RequestDecoder decoder = new RequestDecoder();
        Request request = decoder.decode(buffer);
        Assertions.assertEquals("GET", request.getMethod());
        Assertions.assertEquals("HTTP/1.1", request.getProtocol());
        Assertions.assertEquals("/test", request.getPath());
        Assertions.assertEquals("bar", request.getParameters().get("foo").get(0));
    }

    @Test
    public void shouldParseMultipleParameters() {
        ByteBuffer buffer = ByteBufferUtil.wrapDirect("GET /test?foo=bar&test=true HTTP/1.1\r\n\r\n");
        RequestDecoder decoder = new RequestDecoder();
        Request request = decoder.decode(buffer);
        Assertions.assertEquals("GET", request.getMethod());
        Assertions.assertEquals("HTTP/1.1", request.getProtocol());
        Assertions.assertEquals("/test", request.getPath());
        Assertions.assertEquals("bar", request.getParameters().get("foo").get(0));
        Assertions.assertEquals("true", request.getParameters().get("test").get(0));
    }

    @Test
    public void shouldParseBody() {
        ByteBuffer buffer = ByteBufferUtil.wrapDirect("POST /test?foo=bar HTTP/1.1\r\nContent-Length: 12\r\n\r\nHello, world!");
        RequestDecoder decoder = new RequestDecoder();
        Request request = decoder.decode(buffer);
        System.out.println(request);
        Assertions.assertEquals("POST", request.getMethod());
        Assertions.assertEquals("HTTP/1.1", request.getProtocol());
        Assertions.assertEquals("/test", request.getPath());
        Assertions.assertEquals("bar", request.getParameters().get("foo").get(0));
        Assertions.assertEquals("Hello, world!", StandardCharsets.UTF_8.decode(request.getBody()).toString());
    }

    @Test
    public void shouldNotParseMalformedRequestLine() {
        ByteBuffer buffer = ByteBufferUtil.wrapDirect("GET HTTP/1.1");
        RequestDecoder decoder = new RequestDecoder();
        Assertions.assertThrows(BufferUnderflowException.class, () -> decoder.decode(buffer));
    }
}
