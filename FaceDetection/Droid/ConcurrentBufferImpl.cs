using System;
using Com.Twinfog.Camera;

namespace FaceDetection.Droid
{
  public class ConcurrentBufferImpl : IConcurrentBuffer
  {
    ConcurrentBuffer buffer;

    public ConcurrentBufferImpl(ConcurrentBuffer buffer)
    {
      this.buffer = buffer;
    }

    public byte[] Data { get { return buffer.GetData(); } }

    public void Release() { buffer.Release(); }
  }
}
