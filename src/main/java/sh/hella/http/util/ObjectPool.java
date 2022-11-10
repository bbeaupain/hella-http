package sh.hella.http.util;

import lombok.RequiredArgsConstructor;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

@RequiredArgsConstructor
public class ObjectPool<T> {
    private final Supplier<T> supplier;
    private final Queue<T> q = new ConcurrentLinkedQueue<>();

    public T take() {
        T obj = q.poll();
        return obj == null
            ? supplier.get()
            : obj;
    }

    public void give(T obj) {
        q.add(obj);
    }
}
