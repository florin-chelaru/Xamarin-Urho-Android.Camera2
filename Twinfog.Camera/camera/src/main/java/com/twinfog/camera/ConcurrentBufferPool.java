package com.twinfog.camera;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Semaphore;

/**
 * Created by florinc on 10/28/16.
 */

public class ConcurrentBufferPool implements ConcurrentBuffer.OnReleasedListener {
    private int bufferSize = -1;
    private Deque<ConcurrentBuffer> pool;
    private Semaphore lock = new Semaphore(1);

    public ConcurrentBuffer getBuffer(int bufferSize) throws InterruptedException {
        try {
            lock.acquire();
            if (bufferSize != this.bufferSize) {
                this.bufferSize = bufferSize;
                pool = new ArrayDeque<>();
            }

            ConcurrentBuffer buffer = pool.pollLast();
            if (buffer == null) {
                buffer = new ConcurrentBuffer(this.bufferSize);
                buffer.setReleasedListener(this);
            }

            return buffer;
        } finally {
            lock.release();
        }
    }

    @Override
    public void onReleased(ConcurrentBuffer buffer) {
        try {
            lock.acquire();

            if (buffer.getData().length == bufferSize) {
                pool.addLast(buffer);
            }
        } catch (InterruptedException e) {
            return;
        } finally {
            lock.release();
        }
    }
}
