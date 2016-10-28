package com.twinfog.camera;

import java.util.concurrent.Semaphore;

/**
 * Created by florinc on 10/28/16.
 */

public class ConcurrentBuffer {
    private OnReleasedListener listener;
    private byte[] data;

    public ConcurrentBuffer(int size) {
        data = new byte[size];
    }

    public byte[] getData() {
        return data;
    }

    public void setReleasedListener(OnReleasedListener listener) {
        this.listener = listener;
    }

    public void release() {
        OnReleasedListener l = listener;
        if (l != null) { l.onReleased(this); }
    }

    public interface OnReleasedListener {
        void onReleased(ConcurrentBuffer buffer);
    }
}
