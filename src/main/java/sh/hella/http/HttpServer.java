package sh.hella.http;

import lombok.SneakyThrows;
import sh.blake.niouring.IoUring;
import sh.blake.niouring.IoUringServerSocket;
import sh.hella.http.codec.RequestDecoder;
import sh.hella.http.codec.ResponseEncoder;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.time.Duration;
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
        serverSocket.onAccept((ring, socket) -> {
            ring.queueAccept(serverSocket);
            var requestDecoder = new RequestDecoder();
            var responseEncoder = new ResponseEncoder();
            var inBuffer = ByteBuffer.allocateDirect(options.getRequestBufferSize());
            var outBuffer = ByteBuffer.allocateDirect(options.getResponseBufferSize());
            socket.onRead(received -> {
                socket.onWrite(sent -> socket.close());
                try {
                    var request = requestDecoder.accept(received);
                    if (request.getMethod() == null) {
                        throw new RuntimeException("Unable to decode HTTP request: " + request);
                    }
                    var response = handler.handle(request);
                    responseEncoder.encode(response, outBuffer);
                    ring.queueWrite(socket, outBuffer);
                } catch (BufferUnderflowException ex) {
                    ring.queueRead(socket, received);
                }
            }).onException(ex -> {
                ex.printStackTrace();
                socket.close();
            });
            ring.queueRead(socket, inBuffer);
        });
        for (int i = 0; i < threads; i++) {
            var ring = new IoUring()
                .onException(Exception::printStackTrace)
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
