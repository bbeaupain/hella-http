package sh.hella.http;

import lombok.SneakyThrows;
import sh.blake.niouring.IoUring;
import sh.blake.niouring.IoUringServerSocket;
import sh.hella.http.codec.RequestDecoder;
import sh.hella.http.codec.ResponseEncoder;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HttpServer {
    private final Handler handler;
    private final int threads = Runtime.getRuntime().availableProcessors();
    private final ExecutorService pool = Executors.newFixedThreadPool(threads, (r) -> {
        Thread t = Executors.defaultThreadFactory().newThread(r);
        t.setDaemon(true);
        return t;
    });

    private Options options = Options.builder()
        .host("0.0.0.0")
        .port(8080)
        .requestBufferSize(1024 * 64)
        .responseBufferSize(1024 * 64)
        .build();

    public HttpServer(Handler handler) {
        this.handler = handler;
    }

    public HttpServer(Options options, Handler handler) {
        this.options = options;
        this.handler = handler;
    }

    public HttpServer start() {
        var serverSocket = new IoUringServerSocket(options.getHost(), options.getPort());
        var responseEncoder = new ResponseEncoder();
        serverSocket.onAccept((ring, socket) -> {
            ring.queueAccept(serverSocket);
            var inBuffer = ByteBuffer.allocateDirect(options.getRequestBufferSize());
            var outBuffer = ByteBuffer.allocateDirect(options.getResponseBufferSize());
            var requestDecoder = new RequestDecoder();
            socket.onWrite(ByteBuffer::compact);
            socket.onRead(received -> {
                try {
                    received.flip();
                    var request = requestDecoder.decode(received);
                    var response = handler.handle(request);
                    responseEncoder.encode(response, outBuffer);
                    received.compact();
                    ring.queueWrite(socket, outBuffer);
                    ring.queueRead(socket, received);
                } catch (BufferUnderflowException ex) {
                    received.compact();
                    ring.queueRead(socket, received);
                }
            });
            ring.queueRead(socket, inBuffer);
        });
        for (int i = 0; i < threads; i++) {
            var ring = new IoUring()
                .queueAccept(serverSocket);
            pool.execute(ring::loop);
        }
        return this;
    }

    @SneakyThrows
    public boolean join() {
        return pool.awaitTermination(365 * 1000, TimeUnit.DAYS);
    }
}
