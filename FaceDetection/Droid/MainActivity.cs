using System.Threading.Tasks;
using Android.App;
using Android.Content.PM;
using Android.Views;
using Android.Widget;
using Android.OS;
using Urho.Droid;
using Camera = Android.Hardware.Camera;
using Android.Hardware.Camera2;
using Android.Content;
using Java.Lang;
using Android.Hardware.Camera2.Params;
using Android.Util;
using Java.Util;
using Android.Graphics;
using Android.Media;
using System;
using Android.Runtime;
using System.Collections.Generic;
using System.Diagnostics;
using Android.Support.V8.Renderscript;
using Com.Twinfog.Camera;

namespace FaceDetection.Droid
{
	[Activity(Label = "FaceDetection.Droid", MainLauncher = true,
		Icon = "@drawable/icon", Theme = "@android:style/Theme.NoTitleBar.Fullscreen",
		ConfigurationChanges = ConfigChanges.KeyboardHidden | ConfigChanges.Orientation,
		ScreenOrientation = ScreenOrientation.Portrait)]
	public class MainActivity : Activity /*, Camera.IPreviewCallback*/
	{
		byte[] lastFrame;
    //Camera camera;
    CameraWrapper camera;
		UrhoApp urhoApp;
    Size previewSize;
    public RenderScript RenderScript { get; private set; }

    public MainActivity()
    {
      camera = new CameraWrapper(this);
      //ScriptC_yuv420888 mYuv420 = new ScriptC_yuv420888(RenderScript);
    }

		protected override void OnCreate(Bundle bundle)
		{
			base.OnCreate(bundle);

      RenderScript = RenderScript.Create(this);

			var layout = new AbsoluteLayout(this);
			Urho.Application.Started += UrhoAppStarted;
			var surface = UrhoSurface.CreateSurface<UrhoApp>(this);
			layout.AddView(surface);
			SetContentView(layout);
		}

		void UrhoAppStarted()
		{
			urhoApp = Urho.Application.Current as UrhoApp;
      //var camera = Camera.Open();
      camera.PreviewFrameAvailable += OnPreviewFrame;
      //camera.StartBackgroundThread();
      //camera.OpenCamera(new Size(320, 240));
			//camera.SetPreviewCallback(this);
			//var parameters = camera.GetParameters();
			//parameters.SetPreviewSize(320, 240);//TODO: check if this size is supported
			//camera.SetParameters(parameters);
			//camera.StartPreview();
			//camera.StartFaceDetection();
			urhoApp.CaptureVideo(OnFrameRequested);
		}

		FrameWithFaces OnFrameRequested()
		{
      return lastFrame == null ? null : new FrameWithFaces {FrameData = lastFrame, FrameWidth = previewSize.Width, FrameHeight = previewSize.Height};
		}

    //DateTime lastTime = DateTime.Now;
    Stopwatch watch = new Stopwatch();

    void OnPreviewFrame(object sender, CameraWrapper.CameraPreviewEventArgs e)
    {
      //if (watch.IsRunning)
      //{
      //  watch.Stop();
      //  System.Diagnostics.Debug.WriteLine($"New preview frame after: {watch.Elapsed}");
      //  watch = new Stopwatch();
      //}
      //watch.Start();

      lastFrame = e.FrameData;
      previewSize = new Size(e.Width, e.Height);
    }

		//public void OnPreviewFrame(byte[] data, Camera camera)
		//{
		//	//TODO: NV21 to RGB?
		//	lastFrame = data;
		//}

		protected override void OnResume()
		{
			UrhoSurface.OnResume();
      camera.StartBackgroundThread();
      //previewSize = camera.OpenCamera(new Size(320, 240));
      previewSize = camera.OpenCamera();
			base.OnResume();
		}

		protected override void OnPause()
		{
			UrhoSurface.OnPause();
      camera.CloseCamera();
      camera.StopBackgroundThread();
			base.OnPause();
		}

		public override void OnLowMemory()
		{
			UrhoSurface.OnLowMemory();
			base.OnLowMemory();
		}

		protected override void OnDestroy()
		{
			UrhoSurface.OnDestroy();

      // TODO: We need to start and stop background thread on Resume and on Pause. 
      //       But now we cannot, because we also need to open a new camera, etc when we do that...
      camera.CloseCamera();
      camera.StopBackgroundThread(); 

      // TODO: Also, we need to close camera when not using the app anymore!
			base.OnDestroy();
		}

		public override bool DispatchKeyEvent(KeyEvent e)
		{
			if (!UrhoSurface.DispatchKeyEvent(e))
				return false;
			return base.DispatchKeyEvent(e);
		}

		public override void OnWindowFocusChanged(bool hasFocus)
		{
			UrhoSurface.OnWindowFocusChanged(hasFocus);
			base.OnWindowFocusChanged(hasFocus);
		}
	}
}

