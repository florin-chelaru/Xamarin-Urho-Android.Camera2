package com.twinfog.camera;

import android.graphics.Bitmap;

/**
 * Created by florinc on 10/27/16.
 */

public interface OnBitmapAvailableListener {
    /**
     * @param bitmap The bitmap.
     */
    void onBitmapAvailable(Bitmap bitmap, long frameOrder);
}
