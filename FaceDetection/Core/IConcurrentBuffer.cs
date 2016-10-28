using System;
namespace FaceDetection
{
  public interface IConcurrentBuffer
  {
    byte[] Data { get; }
    void Release();
  }
}
