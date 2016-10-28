package com.twinfog.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.Image;
import android.media.ImageReader;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.*;

/**
 * Created by florinc on 10/27/16.
 */

public class CameraPreviewProcessor implements ImageReader.OnImageAvailableListener {

    private static final String TAG = "CameraPreviewProcessor";

    // Sets the amount of time an idle thread waits before terminating
    private static final int KEEP_ALIVE_TIME = 1;
    // Sets the Time Unit to seconds
    private static final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.SECONDS;

    /*
     * Gets the number of available cores
     * (not always the same as the maximum number of cores)
     */
    private static int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();

    // A queue of Runnables
    private final BlockingQueue<Runnable> decodeWorkQueue = new LinkedBlockingQueue<Runnable>();

    // Creates a thread pool manager
    private ThreadPoolExecutor decodeThreadPool;

    private OnRgbAvailableListener rgbListener;
    private OnBitmapAvailableListener bitmapListener;
    private Context context;
    private long lastFrameIndex = -1;

    private Deque<YuvToRgbTask> taskPool = new ArrayDeque<>();
    private Semaphore taskPoolLock = new Semaphore(1);

    public CameraPreviewProcessor(Context context) {
        this(context, NUMBER_OF_CORES);
        this.context = context;
    }

    public CameraPreviewProcessor(Context context, int threadPoolSize) {
        this.context = context;
        decodeThreadPool = new ThreadPoolExecutor(
                threadPoolSize,       // Initial pool size
                threadPoolSize,       // Max pool size
                KEEP_ALIVE_TIME,
                KEEP_ALIVE_TIME_UNIT,
                decodeWorkQueue);
    }

    public void setRgbListener(OnRgbAvailableListener rgbListener) { this.rgbListener = rgbListener; }
    public void setBitmapListener(OnBitmapAvailableListener bitmapListener) { this.bitmapListener = bitmapListener; }

    /**
     * Callback that is called when a new image is available from ImageReader.
     *
     * @param reader the ImageReader the callback is associated with.
     * @see ImageReader
     * @see Image
     */
    @Override
    public void onImageAvailable(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
        } catch (IllegalStateException ex) {
            // This means we're trying to read a new image while the others haven't been closed yet
            // We'll wait until the next frame...
            return;
        }

        YuvToRgbTask task;
        try {
            taskPoolLock.acquire();

            task = taskPool.pollLast();
            if (task != null) {
                //Log.d(TAG, "*** onImageAvailable() :: Recycling task");
                task.setImage(image);
                task.setFrameOrder(++lastFrameIndex);
            } else {
                //Log.d(TAG, "*** onImageAvailable() :: Creating NEW task");
                task = new YuvToRgbTask(context, image, ++lastFrameIndex, taskPool, taskPoolLock, rgbListener, bitmapListener);
            }
        } catch (InterruptedException e) {
            return;
        } finally {
            taskPoolLock.release();
        }

        decodeThreadPool.execute(task);
    }

    private class YuvToRgbTask implements Runnable {
        private Image image;
        private Context context;
        private OnRgbAvailableListener rgbListener;
        private OnBitmapAvailableListener bitmapListener;
        private long frameOrder;
        private Deque<YuvToRgbTask> taskPool;
        private Semaphore taskPoolLock;
        private Yuv420888ToRgbConverter converter;

        public YuvToRgbTask(Context context, Image image, long frameOrder, Deque<YuvToRgbTask> taskPool, Semaphore taskPoolLock) {
            this.context = context;
            this.image = image;
            this.frameOrder = frameOrder;
            this.taskPool = taskPool;
            this.taskPoolLock = taskPoolLock;
        }

        public YuvToRgbTask(Context context, Image image, long frameOrder, Deque<YuvToRgbTask> taskPool, Semaphore taskPoolLock, OnRgbAvailableListener rgbListener) {
            this(context, image, frameOrder, taskPool, taskPoolLock);
            this.rgbListener = rgbListener;
        }

        public YuvToRgbTask(Context context, Image image, long frameOrder, Deque<YuvToRgbTask> taskPool, Semaphore taskPoolLock, OnBitmapAvailableListener bitmapListener) {
            this(context, image, frameOrder, taskPool, taskPoolLock);
            this.bitmapListener = bitmapListener;
        }

        public YuvToRgbTask(Context context, Image image, long frameOrder, Deque<YuvToRgbTask> taskPool, Semaphore taskPoolLock, OnRgbAvailableListener rgbListener, OnBitmapAvailableListener bitmapListener) {
            this(context, image, frameOrder, taskPool, taskPoolLock);
            this.rgbListener = rgbListener;
            this.bitmapListener = bitmapListener;
        }

        ByteBuffer rawBuffer;

        @Override
        public void run() {
            try {
                if (bitmapListener == null && rgbListener == null) {
                    if (image != null) {
                        image.close();
                    }
                    return;
                }

                byte[] rawBytes = null;
                int width, height;

                // Encapsulate everything in a context so at the end it can be garbage collected
                {
                    Bitmap bitmap = null;
                    try {
                        if (image == null) {
                            return;
                        }

                        if (converter == null) { converter = new Yuv420888ToRgbConverter(context); }
                        bitmap = converter.convert(image);
                    } finally {
                        if (image != null) {
                            image.close();
                            image = null;
                        }
                    }

                    // For now, disable this functionality: the bitmap is being recycled so we don't want unexpected behavior
//                    if (bitmapListener != null) {
//                        bitmapListener.onBitmapAvailable(bitmap, frameOrder);
//                    }

                    if (rgbListener == null) {
                        return;
                    }

                    if (rawBuffer == null) {
                        rawBuffer = ByteBuffer.allocate(bitmap.getByteCount());
                    }
                    rawBuffer.rewind();
                    bitmap.copyPixelsToBuffer(rawBuffer);
                    rawBuffer.rewind();
                    rawBytes = new byte[rawBuffer.remaining()];
                    rawBuffer.get(rawBytes);
                    width = bitmap.getWidth();
                    height = bitmap.getHeight();
                }

                rgbListener.onRgbAvailable(rawBytes, width, height, frameOrder);
            } finally {
                try {
                    taskPoolLock.acquire();
                    taskPool.add(this);
                } catch (InterruptedException e) {
                    // Just ignore
                } finally {
                    taskPoolLock.release();
                }
            }
        }

        public void setImage(Image image) {
            this.image = image;
        }

        public void setFrameOrder(long frameOrder) {
            this.frameOrder = frameOrder;
        }
    }
}
