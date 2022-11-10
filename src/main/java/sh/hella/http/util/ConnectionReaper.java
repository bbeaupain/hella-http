package sh.hella.http.util;

import lombok.Data;
import sh.blake.niouring.IoUringSocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionReaper implements Runnable {
    private final Map<IoUringSocket, TimestampTtl> map = new ConcurrentHashMap<>();
    private long currentTime = System.currentTimeMillis();

    @Override
    public void run() {
        System.out.println("Seen: " + map.size());
        currentTime = System.currentTimeMillis();
        var iter = map.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            long elapsed = currentTime - entry.getValue().seenAt;
            if (elapsed > entry.getValue().ttl) {
                entry.getKey().close();
                iter.remove();
            }
        }
    }

    public void seen(IoUringSocket socket, long ttl) {
        map.computeIfAbsent(socket, s -> new TimestampTtl(ttl)).setSeenAt(currentTime);
    }

    public void remove(IoUringSocket socket) {
        map.remove(socket);
    }

    @Data
    public static class TimestampTtl {
        private final long ttl;
        private long seenAt;
    }
}
