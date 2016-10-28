package com.twinfog.camera;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.support.annotation.NonNull;

/**
 * Created by florinc on 10/27/16.
 */
public interface CameraPreviewListener {
    void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession);
    void onCreateCameraPreviewSessionFailed(CameraAccessException ex);

    void onCameraDeviceOpened(@NonNull CameraDevice cameraDevice);
    void onCameraDeviceDisconnected(@NonNull CameraDevice cameraDevice);
    void onCameraDeviceError(@NonNull CameraDevice cameraDevice, int error);

    void onCameraPermissionDenied();
    void onOpenCameraError(Exception ex);
}
