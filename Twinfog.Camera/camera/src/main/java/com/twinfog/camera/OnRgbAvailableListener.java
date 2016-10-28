package com.twinfog.camera;

import android.util.Size;

/**
 * Created by florinc on 10/27/16.
 */

public interface OnRgbAvailableListener {
    /**
     * @param imageBytes
     * @param width
     * @param height
     * @param frameOrder
     */
    void onRgbAvailable(byte[] imageBytes, int width, int height, long frameOrder);
}
