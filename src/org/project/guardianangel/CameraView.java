package org.project.guardianangel;

import java.io.IOException;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

public class CameraView extends SurfaceView implements SurfaceHolder.Callback{
    //common variables
	public static String TAG = "CameraView";
    private SurfaceHolder mHolder;
    private Camera mCamera;
    private Camera.PreviewCallback camPreviewCallback;
    private Context mainActivity;
    private boolean mCameraConfigured = false;
    
    public CameraView(Context context, Camera.PreviewCallback previewCallback) {
        super(context);
        //add surfaceview to the main context layer
        this.camPreviewCallback = previewCallback;
        mainActivity = context;
        //inspect the surface state
        mHolder = this.getHolder();
        mHolder.addCallback(this);
        Log.d("Camera:","Initialization Done!");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                                   int height) {
    	initPreview(holder);
    	
        mCamera.startPreview();
        
    }

    private void initPreview(SurfaceHolder holder) {
    	if ( mCamera != null && holder.getSurface() != null) {
            try 
            {
            	Camera.Parameters params = mCamera.getParameters();
                params.setPreviewFpsRange(30000, 30000);
                mCamera.setParameters(params);
            	mCamera.setPreviewDisplay(holder);
            	if (camPreviewCallback != null) {
                    mCamera.setPreviewCallbackWithBuffer(camPreviewCallback);
                    Camera.Size size = params.getPreviewSize();
                    byte[] data = new byte[size.width*size.height*
                            ImageFormat.getBitsPerPixel(params.getPreviewFormat())/8];
                    mCamera.addCallbackBuffer(data);
                }
            }
            catch (Throwable t) 
            {
                Log.e(TAG, "Exception in initPreview()", t);
            }

            if ( !mCameraConfigured ) 
            {
                Camera.Parameters parameters = mCamera.getParameters();
                parameters.setPreviewSize(1920, 1080); // hard coded the largest size for now
                mCamera.setParameters(parameters);
                mCameraConfigured = true;
            }
            
            
        }
    }
    
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    	mCamera = Camera.open();
		initPreview(holder);
    }
    

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    	 mCamera.stopPreview();
         mCamera.release();
         mCamera = null;
    }
    
}