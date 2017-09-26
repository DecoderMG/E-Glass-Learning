package com.example.mehl;

import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/** A basic Camera preview class */

// TODO: Fix Camera activity cycle.

/**
 * Class to handle the camera intent and image processing
 */

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
    private SurfaceHolder mHolder;
    private Camera mCamera;
    private boolean TakePicture = false;
    private String NowPictureFileName;

    public CameraPreview(Context context, Camera camera) {
        super(context);
        mCamera = camera;

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        // deprecated setting, but required on Android versions prior to 3.0
        // Added for compatibility if Glass competitor releases with older version of Android.
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    /**
     * Camera initialized without error and surface was created. We now need to handle displaying
     * the feed to the glass display.
     *
     * @param holder - SurfaceHolder for the System Camera
     */
    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
        } catch (IOException e) {
            Log.d("CAMERA ERROR", "Error setting camera preview: " + e.getMessage());
        }
    }

    /**
     * Method to handle cleanup before Android destroys the camera instance
     * @param holder - SurfaceHolder for System Camera
     */
    public void surfaceDestroyed(SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview.
    }

    /**
     * Default camera method to handle when orientation changes or camera preview size changes.
     * Stop the preview and redisplay with new changes.
     * @param holder - SurefaceHolder for the camera
     * @param format - Camera format
     * @param w - Width of camera preview in pixels
     * @param h - Height of camera preview in pixels
     */
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (mHolder.getSurface() == null){
          // preview surface does not exist
          return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e){
          // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();

        } catch (Exception e){
            Log.d("CAMERA ERROR", "Error starting camera preview: " + e.getMessage());
        }
    }
}