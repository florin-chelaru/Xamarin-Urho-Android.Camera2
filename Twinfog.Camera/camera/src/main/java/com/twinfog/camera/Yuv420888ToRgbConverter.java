package com.twinfog.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.Image;

import java.nio.ByteBuffer;
import android.support.v8.renderscript.*;
import android.util.Log;

/**
 * Created by florinc on 10/27/16.
 */

public class Yuv420888ToRgbConverter {
    private static final String TAG = "Yuv420888ToRgbConverter";

    private Context context;

    private int width = -1;
    private int height = -1;
    private byte[] y;
    private byte[] u;
    private byte[] v;
    private RenderScript rs;
    private ScriptC_yuv420888 yuv420;

    private Allocation yAlloc;

    private Type.Builder typeUcharUV;
    private Allocation uAlloc;
    private Allocation vAlloc;

    private Bitmap outBitmap;
    private Allocation outAlloc;
    private Script.LaunchOptions lo;

    public Yuv420888ToRgbConverter(Context context) {
        this.context = context;

        rs = RenderScript.create(this.context);
        yuv420=new ScriptC_yuv420888 (rs);
    }

    public Bitmap convert(Image image){
        Image.Plane[] planes = image.getPlanes();
        //Log.d(TAG, "*** convert() :: width: " + image.getWidth() + " height: " + image.getHeight() + " yRowStride: " + planes[0].getRowStride() + " uvRowStride: " + planes[1].getRowStride() + " uvPixelStride: " + planes[1].getPixelStride());
        //Log.d(TAG, "*** convert() :: width: " + image.getWidth() + " height: " + image.getHeight() + " y buffer size: " + planes[0].getBuffer().remaining() + " u buffer size: " + planes[1].getBuffer().remaining() + " v buffer size: " + planes[2].getBuffer().remaining());

        if (width != image.getWidth() || height != image.getHeight()) {
            width = image.getWidth();
            height = image.getHeight();

            // Get the three image planes
            ByteBuffer buffer = planes[0].getBuffer();
            y = new byte[buffer.remaining()]; // TODO: We should still check that the buffers always have the same number of bytes

            buffer = planes[1].getBuffer();
            u = new byte[buffer.remaining()];

            buffer = planes[2].getBuffer();
            v = new byte[buffer.remaining()];

            outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        }

        // Get the three image planes
        ByteBuffer buffer = planes[0].getBuffer();
        buffer.rewind();
        buffer.get(y);

        buffer = planes[1].getBuffer();
        buffer.rewind();
        buffer.get(u);

        buffer = planes[2].getBuffer();
        buffer.rewind();
        buffer.get(v);

        int yRowStride = planes[0].getRowStride();
        int uvRowStride = planes[1].getRowStride();
        int uvPixelStride = planes[1].getPixelStride();

        // Y,U,V are defined as global allocations, the out-Allocation is the Bitmap.
        // Note also that uAlloc and vAlloc are 1-dimensional while yAlloc is 2-dimensional.
        Type.Builder typeUcharY = new Type.Builder(rs, Element.U8(rs));
        typeUcharY.setX(yRowStride).setY(height);
        yAlloc = Allocation.createTyped(rs, typeUcharY.create());

        yAlloc.copyFrom(y);
        yuv420.set_ypsIn(yAlloc);

        typeUcharUV = new Type.Builder(rs, Element.U8(rs));
        typeUcharUV.setX(u.length);

        uAlloc = Allocation.createTyped(rs, typeUcharUV.create());
        uAlloc.copyFrom(u);
        yuv420.set_uIn(uAlloc);

        vAlloc = Allocation.createTyped(rs, typeUcharUV.create());
        vAlloc.copyFrom(v);
        yuv420.set_vIn(vAlloc);

        yuv420.set_picWidth(width);
        yuv420.set_uvRowStride (uvRowStride);
        yuv420.set_uvPixelStride (uvPixelStride);

        lo = new Script.LaunchOptions();
        lo.setX(0, width);  // by this we ignore the y’s padding zone, i.e. the right side of x between width and yRowStride
        lo.setY(0, height);

        outAlloc = Allocation.createFromBitmap(rs, outBitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        yuv420.forEach_doConvert(outAlloc,lo);
        outAlloc.copyTo(outBitmap);

        return outBitmap;
    }

    public static Bitmap convert(Context context, Image image){
        int width = image.getWidth();
        int height = image.getHeight();

        // Get the three image planes
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        byte[] y = new byte[buffer.remaining()];
        buffer.get(y);

        buffer = planes[1].getBuffer();
        byte[] u = new byte[buffer.remaining()];
        buffer.get(u);

        buffer = planes[2].getBuffer();
        byte[] v = new byte[buffer.remaining()];
        buffer.get(v);

        // get the relevant RowStrides and PixelStrides
        // (we know from documentation that PixelStride is 1 for y)
        int yRowStride= planes[0].getRowStride();
        int uvRowStride= planes[1].getRowStride();  // we know from   documentation that RowStride is the same for u and v.
        int uvPixelStride= planes[1].getPixelStride();  // we know from   documentation that PixelStride is the same for u and v.


        // rs creation just for demo. Create rs just once in onCreate and use it again.
        RenderScript rs = RenderScript.create(context);
        ScriptC_yuv420888 mYuv420=new ScriptC_yuv420888 (rs);

        // Y,U,V are defined as global allocations, the out-Allocation is the Bitmap.
        // Note also that uAlloc and vAlloc are 1-dimensional while yAlloc is 2-dimensional.
        Type.Builder typeUcharY = new Type.Builder(rs, Element.U8(rs));
        typeUcharY.setX(yRowStride).setY(height);
        Allocation yAlloc = Allocation.createTyped(rs, typeUcharY.create());
        yAlloc.copyFrom(y);
        mYuv420.set_ypsIn(yAlloc);

        Type.Builder typeUcharUV = new Type.Builder(rs, Element.U8(rs));
        // note that the size of the u's and v's are as follows:
        //      (  (width/2)*PixelStride + padding  ) * (height/2)
        // =    (RowStride                          ) * (height/2)
        // but I noted that on the S7 it is 1 less...
        typeUcharUV.setX(u.length);
        Allocation uAlloc = Allocation.createTyped(rs, typeUcharUV.create());
        uAlloc.copyFrom(u);
        mYuv420.set_uIn(uAlloc);

        Allocation vAlloc = Allocation.createTyped(rs, typeUcharUV.create());
        vAlloc.copyFrom(v);
        mYuv420.set_vIn(vAlloc);

        // handover parameters
        mYuv420.set_picWidth(width);
        mYuv420.set_uvRowStride (uvRowStride);
        mYuv420.set_uvPixelStride (uvPixelStride);

        Bitmap outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Allocation outAlloc = Allocation.createFromBitmap(rs, outBitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);

        Script.LaunchOptions lo = new Script.LaunchOptions();
        lo.setX(0, width);  // by this we ignore the y’s padding zone, i.e. the right side of x between width and yRowStride
        lo.setY(0, height);

        mYuv420.forEach_doConvert(outAlloc,lo);
        outAlloc.copyTo(outBitmap);

        return outBitmap;
    }
}
