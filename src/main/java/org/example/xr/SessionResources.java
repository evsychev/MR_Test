package org.example.xr;

import java.util.ArrayDeque;
import java.util.Deque;

final class SessionResources implements AutoCloseable {
    private final Deque<Runnable> releases = new ArrayDeque<>();

    void defer(Runnable release) {
        releases.push(release);
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        while (!releases.isEmpty()) {
            try {
                releases.pop().run();
            } catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }
}
