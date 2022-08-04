package sh.hella.http;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import sh.blake.niouring.util.ByteBufferUtil;
import sh.hella.http.codec.RequestDecoder;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class RequestDecoderTest {

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
        ByteBuffer buffer = ByteBufferUtil.wrapDirect("GET /test HTTP/1.1\r\nAccept: text/plain\r\n\r\n");
        RequestDecoder decoder = new RequestDecoder();
        Request request = decoder.decode(buffer);
        Assertions.assertEquals("GET", request.getMethod());
        Assertions.assertEquals("HTTP/1.1", request.getProtocol());
        Assertions.assertEquals("/test", request.getPath());
        Assertions.assertEquals("text/plain", request.getHeaders().get("Accept"));
    }

    @Test
    public void shouldParseMultipleHeaders() {
        ByteBuffer buffer = ByteBufferUtil.wrapDirect("GET /test HTTP/1.1\r\nAccept: text/plain\r\nKeep-Alive: timeout=5, max=1000\r\n\r\n");
        RequestDecoder decoder = new RequestDecoder();
        Request request = decoder.decode(buffer);
        Assertions.assertEquals("GET", request.getMethod());
        Assertions.assertEquals("HTTP/1.1", request.getProtocol());
        Assertions.assertEquals("/test", request.getPath());
        Assertions.assertEquals("text/plain", request.getHeaders().get("Accept"));
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
        ByteBuffer buffer = ByteBufferUtil.wrapDirect("POST /test?foo=bar HTTP/1.1\r\nContent-Length: 13\r\n\r\nHello, world!\r\n");
        RequestDecoder decoder = new RequestDecoder();
        Request request = decoder.decode(buffer);
        Assertions.assertEquals("POST", request.getMethod());
        Assertions.assertEquals("HTTP/1.1", request.getProtocol());
        Assertions.assertEquals("/test", request.getPath());
        Assertions.assertEquals("bar", request.getParameters().get("foo").get(0));
        Assertions.assertEquals("13", request.getHeaders().get("Content-Length"));
        Assertions.assertEquals("Hello, world!", StandardCharsets.UTF_8.decode(request.getBody()).toString());
    }

    @Test
    public void shouldParseMultipleRequests() {
        RequestDecoder decoder = new RequestDecoder();
        ByteBuffer buffer = ByteBufferUtil.wrapDirect("POST /test?foo=bar HTTP/1.1\r\nContent-Length: 13\r\n\r\nHello, world!\r\n");

        Request request = decoder.decode(buffer);
        Assertions.assertEquals("POST", request.getMethod());
        Assertions.assertEquals("HTTP/1.1", request.getProtocol());
        Assertions.assertEquals("/test", request.getPath());
        Assertions.assertEquals("bar", request.getParameters().get("foo").get(0));
        Assertions.assertEquals("13", request.getHeaders().get("Content-Length"));
        Assertions.assertEquals("Hello, world!", StandardCharsets.UTF_8.decode(request.getBody()).toString());

        buffer.position(0);

        Request request2 = decoder.decode(buffer);
        Assertions.assertEquals("POST", request2.getMethod());
        Assertions.assertEquals("HTTP/1.1", request2.getProtocol());
        Assertions.assertEquals("/test", request2.getPath());
        Assertions.assertEquals("bar", request2.getParameters().get("foo").get(0));
        Assertions.assertEquals("13", request2.getHeaders().get("Content-Length"));
        Assertions.assertEquals("Hello, world!", StandardCharsets.UTF_8.decode(request2.getBody()).toString());
    }

    @Test
    public void shouldParseFragmentedRequestLine() {
        RequestDecoder decoder = new RequestDecoder();
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024);

        buffer.put("POST /test?f".getBytes(StandardCharsets.UTF_8));
        buffer.flip();
        Request request = decoder.decode(buffer);
        Assertions.assertNull(request);
        buffer.compact();
        buffer.put("oo=bar HTTP/1.1=\r\nContent-Length: 13\r\n\r\nHello, world!\r\n".getBytes(StandardCharsets.UTF_8));
        buffer.flip();
        request = decoder.decode(buffer);

        Assertions.assertEquals("POST", request.getMethod());
        Assertions.assertEquals("HTTP/1.1", request.getProtocol());
        Assertions.assertEquals("/test", request.getPath());
        Assertions.assertEquals("bar", request.getParameters().get("foo").get(0));
        Assertions.assertEquals("13", request.getHeaders().get("Content-Length"));
        Assertions.assertEquals("Hello, world!", StandardCharsets.UTF_8.decode(request.getBody()).toString());
    }

    @Test
    public void shouldParseFragmentedHeaders() {
        RequestDecoder decoder = new RequestDecoder();
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024);

        buffer.put("POST /test?foo=bar HTTP/1.1\r\nConte".getBytes(StandardCharsets.UTF_8));
        buffer.flip();
        Request request = decoder.decode(buffer);
        Assertions.assertNull(request);
        buffer.compact();
        buffer.put("nt-Length: 13\r\n\r\nHello, world!\r\n".getBytes(StandardCharsets.UTF_8));
        buffer.flip();
        request = decoder.decode(buffer);

        Assertions.assertEquals("POST", request.getMethod());
        Assertions.assertEquals("HTTP/1.1", request.getProtocol());
        Assertions.assertEquals("/test", request.getPath());
        Assertions.assertEquals("bar", request.getParameters().get("foo").get(0));
        Assertions.assertEquals("13", request.getHeaders().get("Content-Length"));
        Assertions.assertEquals("Hello, world!", StandardCharsets.UTF_8.decode(request.getBody()).toString());
    }

    @Test
    public void shouldParseFragmentedBody() {
        RequestDecoder decoder = new RequestDecoder();
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024);

        buffer.put("POST /test?foo=bar HTTP/1.1\r\nContent-Length: 13\r\n\r\nH".getBytes(StandardCharsets.UTF_8));
        buffer.flip();
        Request request = decoder.decode(buffer);
        Assertions.assertNull(request);
        buffer.compact();
        buffer.put("ello, world!\r\n".getBytes(StandardCharsets.UTF_8));
        buffer.flip();
        request = decoder.decode(buffer);

        Assertions.assertEquals("POST", request.getMethod());
        Assertions.assertEquals("HTTP/1.1", request.getProtocol());
        Assertions.assertEquals("/test", request.getPath());
        Assertions.assertEquals("bar", request.getParameters().get("foo").get(0));
        Assertions.assertEquals("13", request.getHeaders().get("Content-Length"));
        Assertions.assertEquals("Hello, world!", StandardCharsets.UTF_8.decode(request.getBody()).toString());
    }

    @Test
    public void shouldParseChunkedBody() {
        RequestDecoder decoder = new RequestDecoder();
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024);

        buffer.put("POST /test?foo=bar HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        buffer.put("7\r\n".getBytes(StandardCharsets.UTF_8));
        buffer.put("Hello, \r\n".getBytes(StandardCharsets.UTF_8));
        buffer.flip();

        Request request = decoder.decode(buffer);
        Assertions.assertNotNull(request);
        Assertions.assertEquals("POST", request.getMethod());
        Assertions.assertEquals("HTTP/1.1", request.getProtocol());
        Assertions.assertEquals("/test", request.getPath());
        Assertions.assertEquals("bar", request.getParameters().get("foo").get(0));
        Assertions.assertEquals("chunked", request.getHeaders().get("Transfer-Encoding"));

        AtomicInteger chunkCount = new AtomicInteger(0);
        request.setChunkHandler(chunk -> {
            chunkCount.incrementAndGet();
            if (chunkCount.get() == 1) {
                Assertions.assertEquals("Hello, ", StandardCharsets.UTF_8.decode(chunk).toString());
            } else if (chunkCount.get() == 2) {
                Assertions.assertEquals("world!", StandardCharsets.UTF_8.decode(chunk).toString());
            }
        });

        buffer.compact();
        buffer.put("6\r\n".getBytes(StandardCharsets.UTF_8));
        buffer.put("world!\r\n0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        buffer.flip();
        decoder.decode(buffer);

        Assertions.assertEquals(2, chunkCount.get());
    }

    @Test
    public void shouldParseFragmentedChunkedBody() {
        RequestDecoder decoder = new RequestDecoder();
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024);

        buffer.put("POST /test?foo=bar HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        buffer.put("7\r\n".getBytes(StandardCharsets.UTF_8));
        buffer.put("Hel".getBytes(StandardCharsets.UTF_8));
        buffer.flip();
        Request request = decoder.decode(buffer);
        buffer.compact();

        Assertions.assertNotNull(request);
        Assertions.assertEquals("POST", request.getMethod());
        Assertions.assertEquals("HTTP/1.1", request.getProtocol());
        Assertions.assertEquals("/test", request.getPath());
        Assertions.assertEquals("bar", request.getParameters().get("foo").get(0));
        Assertions.assertEquals("chunked", request.getHeaders().get("Transfer-Encoding"));

        AtomicInteger chunkCount = new AtomicInteger(0);
        request.setChunkHandler(chunk -> {
            chunkCount.incrementAndGet();
            if (chunkCount.get() == 1) {
                Assertions.assertEquals("Hello, ", StandardCharsets.UTF_8.decode(chunk).toString());
            } else if (chunkCount.get() == 2) {
                Assertions.assertEquals("world!", StandardCharsets.UTF_8.decode(chunk).toString());
            }
        });

        buffer.put("lo, \r\n".getBytes(StandardCharsets.UTF_8));
        buffer.flip();
        decoder.decode(buffer);
        buffer.compact();

        buffer.put("6\r\n".getBytes(StandardCharsets.UTF_8));
        buffer.put("world!\r\n".getBytes(StandardCharsets.UTF_8));
        buffer.put("0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        buffer.flip();
        decoder.decode(buffer);

        Assertions.assertEquals(2, chunkCount.get());
    }
}
