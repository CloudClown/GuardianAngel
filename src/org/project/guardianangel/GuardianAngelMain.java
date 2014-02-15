package org.project.guardianangel;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings.Secure;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.client.Firebase;

public class GuardianAngelMain extends Activity implements 
                                                    GestureDetector.OnGestureListener, 
                                                    Camera.OnZoomChangeListener,
                                                    SensorEventListener,
                                                    LocationListener {

    public static String TAG = "GuardianCam";
    public static float FULL_DISTANCE = 8000.0f;
    //unique android ID
    private String android_id;

    //Sound and Noise Metric
    private NoiseMetric nMetric;
    
    //sensor and location variables
    private SensorManager mSensorManager;
    private LocationManager mLocationManager;
    private String mLocationProvider;
    private double mLatitude, mLongitude, mAltitude;
    //danger level
    private int dangerLevel = 1;
    
    //camera variables
    private SurfaceView mPreview;
    private SurfaceHolder mPreviewHolder;
    private Camera mCamera;
    private boolean mInPreview = false;
    private boolean mCameraConfigured = false;
    private TextView mZoomLevelView;
    private int surfWidth;
    private int surfHeight;
    
    //gestures
    private GestureDetector mGestureDetector;
    
    //firebase
    Firebase fireref;
    String firebaseIP = "https://guardianangel.firebaseio.com/users";
    
    //speech recognition
    private static final int SPEECH_REQUEST = 0;

    //start the speech recognition process
    private void displaySpeechRecognizer() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        startActivityForResult(intent, SPEECH_REQUEST);
    }

    //send request to the server
    private void sendGet() throws Exception {
        String url = "http://guardianangel.herokuapp.com/inform/"+android_id.toString();
 
        Toast.makeText(this	,"Emergency Contact Initialized!", Toast.LENGTH_SHORT).show();
        
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        //Toast.makeText(this	,"sending request!!", Toast.LENGTH_SHORT).show();
        // optional default is GET
        con.setRequestMethod("GET");
        
        //add request header
        String USER_AGENT = "Mozilla/5.0";
        con.setRequestProperty("User-Agent", USER_AGENT);
		
        int responseCode = con.getResponseCode();
        System.out.println("\nSending 'GET' request to URL : " + url);
        System.out.println("Response Code : " + responseCode);
        
        Toast.makeText(this	,"Emergency Contact Status:" + responseCode, Toast.LENGTH_SHORT).show();
		
    }
    
    //process the recognized texts
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                        Intent data) {
        if (requestCode == SPEECH_REQUEST && resultCode == RESULT_OK) {
            List<String> results = data.getStringArrayListExtra(
                                                                RecognizerIntent.EXTRA_RESULTS);
            String spokenText = results.get(0);
            // Do something with spokenText.
            //Toast.makeText(this, spokenText+spokenText+spokenText, Toast.LENGTH_SHORT).show();
            
            if (spokenText.matches(".*danger.*|.*stay away.*|.*help.*|.*save.*")) {
            	//Toast.makeText(this, "Emergency Message Sent!!", Toast.LENGTH_SHORT).show();
            	dangerLevel = 5;
            	sendToDatabase();
            	try {
            		
                    sendGet();
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guardian_angel_main);
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mPreview = (SurfaceView)findViewById(R.id.preview);
        mPreviewHolder = mPreview.getHolder();
        mPreviewHolder.addCallback(surfaceCallback);
        
        android_id = Secure.getString(getBaseContext().getContentResolver(),
                                      Secure.ANDROID_ID);
        
        //mZoomLevelView = (TextView)findViewById(R.id.zoomLevel);
        mGestureDetector = new GestureDetector(this, this);
        
        //sensor manager
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        //location manager
        mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        
        //set up the criteria
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setAltitudeRequired(true);
        criteria.setBearingRequired(true);
        
        //location initialization
        mLocationProvider = mLocationManager.getBestProvider(new Criteria(), false);
        Location location = mLocationManager.getLastKnownLocation(mLocationProvider);
        if (location != null) {
            onLocationChanged(location);
        }
        
        try {
            //firebase
            fireref = new Firebase(firebaseIP+"/"+android_id);
        } catch (Throwable t) {
        	
            Toast.makeText(this, "database connection error!", Toast.LENGTH_SHORT).show();
        }
    }

    //activity status callbacks
    @Override
    public void onResume() 
    {
        super.onResume();
        
        mLocationManager.requestLocationUpdates(mLocationProvider, 400, 0.01f, this);
        
        mCamera = Camera.open();
        if (surfWidth != 0 && surfHeight != 0) initPreview(surfWidth, surfHeight);
        startPreview();
    }

    @Override
    public void onPause() 
    {
        if ( mInPreview )
            mCamera.stopPreview();

        mCamera.release();
        mCamera = null;
        mInPreview = false;
        
        mLocationManager.removeUpdates(this);
        
        super.onPause();
    }

    private void initPreview(int width, int height) 
    {
        if ( mCamera != null && mPreviewHolder.getSurface() != null) {
            try 
                {
                    Camera.Parameters params = mCamera.getParameters();
                    params.setPreviewFpsRange(30000, 30000);
                    mCamera.setParameters(params);
                    mCamera.setPreviewDisplay(mPreviewHolder);
                }
            catch (Throwable t) 
                {
                    Log.e(TAG, "Exception in initPreview()", t);
                    Toast.makeText(GuardianAngelMain.this, t.getMessage(), Toast.LENGTH_LONG).show();
                }

            if ( !mCameraConfigured ) 
                {
                    Camera.Parameters parameters = mCamera.getParameters();
                    parameters.setPreviewSize(1920, 1080); // hard coded the largest size for now
                    mCamera.setParameters(parameters);
                    mCamera.setZoomChangeListener(this);
                    mCameraConfigured = true;
                }
        }
    }

    private void startPreview() 
    {
        if ( mCameraConfigured && mCamera != null ) 
            {
                mCamera.startPreview();
                mInPreview = true;
            
            }
    }

    
    SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
            public void surfaceCreated( SurfaceHolder holder ) 
            {
        	// nothing
            }

            public void surfaceChanged( SurfaceHolder holder, int format, int width, int height ) 
            {
        	surfWidth = width;
        	surfHeight = height;
        	Log.d("Surface Changed!!!!!", "Surface Changed!!!");
        	initPreview(width, height);
                startPreview();
            }

            public void surfaceDestroyed( SurfaceHolder holder ) 
            {
                // nothing
            }
        };

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) 
    {
        mGestureDetector.onTouchEvent(event);
        return true;
    }

    @Override
	public boolean onDown(MotionEvent e) 
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
	public boolean onFling( MotionEvent e1, MotionEvent e2, float velocityX, float velocityY ) 
    {
        Camera.Parameters parameters = mCamera.getParameters();
        int zoom = parameters.getZoom();

        /*
          if ( velocityX < 0.0f )
          {
          zoom -= 10;
          if ( zoom < 0 )
          zoom = 0;
          }
          else if ( velocityX > 0.0f )
          {
          zoom += 10;
          if ( zoom > parameters.getMaxZoom() )
          zoom = parameters.getMaxZoom();
          }
        */

        //start speech recognition
        displaySpeechRecognizer();

        mCamera.startSmoothZoom(zoom);

        return false;
    }

    @Override
	public void onLongPress(MotionEvent e) 
    {
        // TODO Auto-generated method stub
    }

    @Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) 
    {
        //Log.d(TAG, "distanceX: " + distanceX + ", distanceY: " + distanceY);
        return false;
    }

    @Override
	public void onShowPress(MotionEvent e) 
    {
        // TODO Auto-generated method stub
    }

    private void sendToDatabase() {
        try {
            Map<String, Object> toSet = new HashMap<String, Object>();
            Map<String, Object> val = new HashMap<String, Object>();
            val.put("dangerLevel", dangerLevel);
            val.put("latitude", mLatitude);
            val.put("longitude", mLongitude);
            toSet.put(android_id, val);
            fireref.setValue(val);
        } catch (Throwable t) {
            Toast.makeText(this, "sending data failed", Toast.LENGTH_SHORT).show();
        }
    };

    @Override
	public boolean onSingleTapUp(MotionEvent e) 
    {
        //tap to show user ID
        Toast toast = Toast.makeText(this, 
                                     "Your User ID is:"+android_id
                                     , Toast.LENGTH_SHORT);
        toast.show();
        //sendToDatabase();
        return false;
    }

    @Override
	public void onZoomChange(int zoomValue, boolean stopped, Camera camera) {
        //mZoomLevelView.setText("ZOOM: " + zoomValue);

    }

    @Override
	public void onLocationChanged(Location loc) {
        //update longitude status
        mLatitude = loc.getLatitude();
        mLongitude = loc.getLongitude();
        mAltitude = loc.getAltitude();

    }

    @Override
	public void onProviderDisabled(String arg0) {
        // TODO Auto-generated method stub

    }

    @Override
	public void onProviderEnabled(String arg0) {
        // TODO Auto-generated method stub

    }

    @Override
	public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
        // TODO Auto-generated method stub

    }

    @Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
        // TODO Auto-generated method stub

    }

    @Override
	public void onSensorChanged(SensorEvent arg0) {
        // TODO Auto-generated method stub

    }

    
}