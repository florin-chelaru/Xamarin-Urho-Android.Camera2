using System;
using System.Threading.Tasks;
using Android.Graphics;
using Android.Media;
using Android.Support.V8.Renderscript;
using Java.Nio;
using Com.Twinfog.Camera;
using RType = Android.Support.V8.Renderscript.Type;

namespace FaceDetection.Droid
{
  public class CameraPreviewProcessor : Java.Lang.Object, ImageReader.IOnImageAvailableListener
  {
    public CameraPreviewProcessor()
    {
    }

    public void OnImageAvailable(ImageReader reader)
    {
      byte[] y;
      byte[] u;
      byte[] v;
      int yRowStride, uvRowStride, uvPixelStride;
      int width, height;
      using (var image = reader.AcquireLatestImage())
      {
        try
        {
          if (image == null) { return; }

          //using (Bitmap bitmap = YUV_420_888_toRGB(image, image.Width, image.Height))
          //{
          //  var rawBuffer = ByteBuffer.Allocate(bitmap.ByteCount);
          //  bitmap.CopyPixelsToBuffer(rawBuffer);
          //  rawBuffer.Rewind();
          //  byte[] rawBytes = new byte[rawBuffer.Remaining()];
          //  rawBuffer.Get(rawBytes);
          //}

          // Get the three image planes
          Image.Plane[] planes = image.GetPlanes();
          ByteBuffer buffer = planes[0].Buffer;
          y = new byte[buffer.Remaining()];
          buffer.Get(y);

          buffer = planes[1].Buffer;
          u = new byte[buffer.Remaining()];
          buffer.Get(u);

          buffer = planes[2].Buffer;
          v = new byte[buffer.Remaining()];
          buffer.Get(v);

          // get the relevant RowStrides and PixelStrides
          // (we know from documentation that PixelStride is 1 for y)
          yRowStride = planes[0].RowStride;
          uvRowStride = planes[1].RowStride;  // we know from   documentation that RowStride is the same for u and v.
          uvPixelStride = planes[1].PixelStride;  // we know from   documentation that PixelStride is the same for u and v.

          width = image.Width;
          height = image.Height;
        }
        finally
        {
          image?.Close();
        }
      }

      HandleYUV(y, u, v, width, height, yRowStride, uvRowStride, uvPixelStride);
    }

    object sync = new object();
    int lastFired = -1;
    int lastStarted = -1;
    public event EventHandler<CameraPreviewEventArgs> PreviewFrameAvailable;

    void HandleYUV(byte[] y, byte[] u, byte[] v, int width, int height, int yRowStride, int uvRowStride, int uvPixelStride)
    {
      int current;
      lock (sync)
      {
        current = ++lastStarted;
      }

      if (PreviewFrameAvailable == null) { return; }

      Task.Run(() =>
      {
        byte[] rawBytes;
        using (Bitmap bitmap = Yuv420888ToRgb(y, u, v, width, height, yRowStride, uvRowStride, uvPixelStride))
        {
          y = u = v = null; // free some of the memory just in case

          var rawBuffer = ByteBuffer.Allocate(bitmap.ByteCount);
          bitmap.CopyPixelsToBuffer(rawBuffer);
          rawBuffer.Rewind();
          rawBytes = new byte[rawBuffer.Remaining()];
          rawBuffer.Get(rawBytes);
        }

        var handler = PreviewFrameAvailable;
        if (handler == null) { return; }

        //if (current <= lastFired) { return; }
        //lock (sync)
        //{
        //  if (current <= lastFired) { return; }
        //  lastFired = current;

        //  handler(this, new CameraPreviewEventArgs { FrameData = rawBytes, Width = width, Height = height });
        //}
        handler(this, new CameraPreviewEventArgs { FrameOrder = current, FrameData = rawBytes, Width = width, Height = height });
      });
    }

    /// <summary>
    /// Converts YUV_420_888 raw data to RGB bitmap.
    /// </summary>
    /// <returns>The RGB bitmap.</returns>
    /// <param name="y">Y</param>
    /// <param name="u">U</param>
    /// <param name="v">V</param>
    /// <param name="width">Width</param>
    /// <param name="height">Height</param>
    /// <param name="yRowStride">Y row stride (we know from documentation that PixelStride is 1 for y).</param>
    /// <param name="uvRowStride">UV row stride (we know from   documentation that RowStride is the same for u and v).</param>
    /// <param name="uvPixelStride">Uv pixel stride (we know from   documentation that PixelStride is the same for u and v).</param>
    public Bitmap Yuv420888ToRgb(byte[] y, byte[] u, byte[] v, int width, int height, int yRowStride, int uvRowStride, int uvPixelStride)
    {
      // rs creation just for demo. Create rs just once in onCreate and use it again.
      //RenderScript rs = (activity as MainActivity).RenderScript; // RenderScript.create(this);
      RenderScript rs = RenderScript.Create(Android.App.Application.Context);
      //RenderScript rs = MainActivity.rs;
      ScriptC_yuv420888 mYuv420 = new ScriptC_yuv420888(rs);

      // Y,U,V are defined as global allocations, the out-Allocation is the Bitmap.
      // Note also that uAlloc and vAlloc are 1-dimensional while yAlloc is 2-dimensional.
      RType.Builder typeUcharY = new RType.Builder(rs, Element.U8(rs));
      typeUcharY.SetX(yRowStride).SetY(height);
      Allocation yAlloc = Allocation.CreateTyped(rs, typeUcharY.Create());
      yAlloc.CopyFrom(y);
      mYuv420.Set_ypsIn(yAlloc);

      RType.Builder typeUcharUV = new RType.Builder(rs, Element.U8(rs));
      // note that the size of the u's and v's are as follows:
      //      (  (width/2)*PixelStride + padding  ) * (height/2)
      // =    (RowStride                          ) * (height/2)
      // but I noted that on the S7 it is 1 less...
      typeUcharUV.SetX(u.Length);
      Allocation uAlloc = Allocation.CreateTyped(rs, typeUcharUV.Create());
      uAlloc.CopyFrom(u);
      mYuv420.Set_uIn(uAlloc);

      Allocation vAlloc = Allocation.CreateTyped(rs, typeUcharUV.Create());
      vAlloc.CopyFrom(v);
      mYuv420.Set_vIn(vAlloc);

      // handover parameters
      mYuv420.Set_picWidth(width);
      mYuv420.Set_uvRowStride(uvRowStride);
      mYuv420.Set_uvPixelStride(uvPixelStride);

      Bitmap outBitmap = Bitmap.CreateBitmap(width, height, Bitmap.Config.Argb8888);
      Allocation outAlloc = Allocation.CreateFromBitmap(rs, outBitmap, Allocation.MipmapControl.MipmapNone, Allocation.UsageScript);

      Script.LaunchOptions lo = new Script.LaunchOptions();
      lo.SetX(0, width);  // by this we ignore the y’s padding zone, i.e. the right side of x between width and yRowStride
      lo.SetY(0, height);

      mYuv420.ForEach_doConvert(outAlloc, lo);
      outAlloc.CopyTo(outBitmap);

      return outBitmap;
    }

    public Bitmap Yuv420888ToRgb(Image image, int width, int height)
    {
      // Get the three image planes
      Image.Plane[] planes = image.GetPlanes();
      ByteBuffer buffer = planes[0].Buffer;
      byte[] y = new byte[buffer.Remaining()];
      buffer.Get(y);

      buffer = planes[1].Buffer;
      byte[] u = new byte[buffer.Remaining()];
      buffer.Get(u);

      buffer = planes[2].Buffer;
      byte[] v = new byte[buffer.Remaining()];
      buffer.Get(v);

      // get the relevant RowStrides and PixelStrides
      // (we know from documentation that PixelStride is 1 for y)
      int yRowStride = planes[0].RowStride;
      int uvRowStride = planes[1].RowStride;  // we know from   documentation that RowStride is the same for u and v.
      int uvPixelStride = planes[1].PixelStride;  // we know from   documentation that PixelStride is the same for u and v.

      return Yuv420888ToRgb(y, u, v, width, height, yRowStride, uvRowStride, uvPixelStride);
    }
  }
}
