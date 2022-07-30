package sh.hella.http;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
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
import java.util.function.Function;

@AllArgsConstructor
@RequiredArgsConstructor
public class HttpServer {
    private Options options = Options.builder().build();
    private final Function<Request, Response> handler;
    private final ExecutorService pool = Executors.newFixedThreadPool(options.getThreads());

    public HttpServer start() {
        var serverSocket = new IoUringServerSocket(options.getHost(), options.getPort());
        var responseEncoder = new ResponseEncoder();
        serverSocket.onAccept((ring, socket) -> {
            ring.queueAccept(serverSocket);
            var inBuffer = ByteBuffer.allocateDirect(options.getRequestBufferSize());
            var outBuffer = ByteBuffer.allocateDirect(options.getResponseBufferSize());
            var requestDecoder = new RequestDecoder();
            socket.onRead(received -> {
                received.flip();
                try {
                    var request = requestDecoder.decode(received);
                    var response = handler.apply(request);
                    responseEncoder.encode(response, outBuffer);
                    ring.queueWrite(socket, outBuffer);
                } catch (BufferUnderflowException ignored) {}
                received.compact();
                ring.queueRead(socket, received);
            });
            socket.onWrite(ByteBuffer::compact);
            ring.queueRead(socket, inBuffer);
        });
        for (int i = 0; i < options.getThreads(); i++) {
            pool.execute(new IoUring(1024).queueAccept(serverSocket)::loop);
        }
        return this;
    }

    @SneakyThrows
    public boolean join() {
        return pool.awaitTermination(365 * 1000, TimeUnit.DAYS);
    }
}
