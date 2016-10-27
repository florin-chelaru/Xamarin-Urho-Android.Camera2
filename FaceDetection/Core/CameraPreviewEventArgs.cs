using System;
namespace FaceDetection
{
  public class CameraPreviewEventArgs : EventArgs
  {
    public int FrameOrder { get; set; }
    public byte[] FrameData { get; set; }
    public int Width { get; set; }
    public int Height { get; set; }
  }
}
