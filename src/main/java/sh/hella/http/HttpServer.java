package sh.hella.http;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import sh.blake.niouring.IoUring;
import sh.blake.niouring.IoUringServerSocket;
import sh.hella.http.codec.RequestDecoder;
import sh.hella.http.codec.ResponseEncoder;
import sh.hella.http.util.ConnectionReaper;
import sh.hella.http.util.ObjectPool;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@AllArgsConstructor
@RequiredArgsConstructor
public class HttpServer {
    private Options options = Options.builder().build();
    private final Function<Request, Response> handler;
    private final ExecutorService pool = Executors.newFixedThreadPool(options.getThreads());
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConnectionReaper connectionReaper = new ConnectionReaper();
    private final ObjectPool<ByteBuffer> inBufferPool = new ObjectPool<>(
        () -> ByteBuffer.allocateDirect(options.getRequestBufferSize()));
    private final ObjectPool<ByteBuffer> outBufferPool = new ObjectPool<>(
        () -> ByteBuffer.allocateDirect(options.getResponseBufferSize()));

    public HttpServer start() {
        scheduler.scheduleAtFixedRate(connectionReaper, 1, 1, TimeUnit.SECONDS);
        var serverSocket = new IoUringServerSocket(options.getHost(), options.getPort());
        serverSocket.onAccept((ring, socket) -> {
            ring.queueAccept(serverSocket);

            connectionReaper.seen(socket, options.getTtl());
            var inBuffer = inBufferPool.take();
            var outBuffer = outBufferPool.take();
            var requestDecoder = new RequestDecoder();

            socket.onRead(received -> {
                if (received.position() == 0) {
                    socket.close();
                    return;
                }
                connectionReaper.seen(socket, options.getTtl());
                received.flip();
                var request = requestDecoder.decode(received);
                if (request != null) {
                    var response = handler.apply(request);
                    ResponseEncoder.encode(response, outBuffer);
                    ring.queueWrite(socket, outBuffer);
                }
                received.compact();
                ring.queueRead(socket, received);
            });

            socket.onWrite(ByteBuffer::compact);

            socket.onClose(() -> {
                inBufferPool.give(inBuffer.clear());
                outBufferPool.give(outBuffer.clear());
                connectionReaper.remove(socket);
            });

            ring.queueRead(socket, inBuffer);
        });

        for (int i = 0; i < options.getThreads(); i++) {
            pool.execute(new IoUring().queueAccept(serverSocket)::loop);
        }

        return this;
    }

    @SneakyThrows
    public boolean join() {
        return pool.awaitTermination(365 * 1000, TimeUnit.DAYS);
    }
}
