package org.project.guardianangel;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings.Secure;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.client.Firebase;

//audio tracking
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;

public class GuardianAngelMain extends Activity implements 
                                                    GestureDetector.OnGestureListener,
                                                    Camera.OnZoomChangeListener,
                                                    SensorEventListener,
                                                    LocationListener {
	//audio variables
	private static final int SAMPLING_RATE = 44100;
	//private WaveformView mWaveformView;
	
	//private RecordingThread mRecordingThread;
    private int mBufferSize;
    private short[] mAudioBuffer;
    private String mDecibelFormat;
	
	//text to speech
	public TextToSpeech ttobj;

    public static String TAG = "GuardianCam";
    public static float FULL_DISTANCE = 8000.0f;
    //unique android ID
    private String android_id;

    //Sound and Noise Metric
    private NoiseMetric nMetric;
    
    //sensor variables
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private float[] mLastValues = new float[] { 0.0f, 0.0f, 0.0f };
    private float mLowPassFilter = 0.09f;
    //location variables
    private LocationManager mLocationManager;
    private String mLocationProvider;
    private double mLatitude, mLongitude, mAltitude;
    //danger level
    private int dangerLevel = 1;
    
    //motion queue
    private int runQueueSize = 0;
    private int walkQueueSize = 0;
    private static int SIZELIMIT = 200;
    private static int QUOTIENT = 80;
    
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

    private class AsyncHTTPRequest extends AsyncTask<URL, String, Integer> {

		protected Integer doInBackground(URL... url) {
			int rCode = -1;
			try {
				HttpURLConnection con = (HttpURLConnection) url[0].openConnection();
				con.setRequestMethod("POST");
		        
		        //add request header
		        String USER_AGENT = "Mozilla/5.0";
		        con.setRequestProperty("User-Agent", USER_AGENT);
				
		        int responseCode = con.getResponseCode();
		        System.out.println("\nSending 'POST' request to URL : " + url);
		        System.out.println("Response Code : " + responseCode);
		        rCode = responseCode;
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			return rCode;
		}
    	
		protected void onPostExecute(Integer result) {
	        super.onPostExecute(result);
	        Log.d("HTTP Request","Request made!!!");
	    }
		
    }
    
    //send request to the server
    private void sendGet() throws Exception {
        String url = "http://guardianangel.herokuapp.com/inform/"+android_id.toString();
 
        Toast.makeText(this	,"Emergency Contact Initialized!", Toast.LENGTH_SHORT).show();
        
        URL obj = new URL(url);
        
        new AsyncHTTPRequest().execute(obj);
		
    }
    
    //process the recognized texts
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                        Intent data) {
        if (requestCode == SPEECH_REQUEST && resultCode == RESULT_OK) {
            List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            String spokenText = results.get(0);
            // Do something with spokenText.
            //Toast.makeText(this, spokenText+spokenText+spokenText, Toast.LENGTH_SHORT).show();
            
            if (spokenText.matches(".*danger.*|.*stay away.*|.*help.*|.*save.*")) {
            	Toast.makeText(this, "Speeech Message Sent!!", Toast.LENGTH_SHORT).show();
            	dangerLevel = 5;
            	sendToDatabase(spokenText, true);
            	try {
            		
                    sendGet();
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            	ttobj.speak("Emergency Contact Message Sent.", TextToSpeech.QUEUE_FLUSH, null);
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
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
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
        
        //set up text to speech
        ttobj=new TextToSpeech(getApplicationContext(), 
        	      new TextToSpeech.OnInitListener() {
        	      @Override
        	      public void onInit(int status) {
        	         if(status != TextToSpeech.ERROR){
        	             ttobj.setLanguage(Locale.UK);
        	            }				
        	         }
        	      });
    }

    //activity status callbacks
    @Override
    public void onResume() 
    {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
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
        mSensorManager.unregisterListener(this);
        mLocationManager.removeUpdates(this);
        if(ttobj !=null) {
            ttobj.stop();
            ttobj.shutdown();
        }
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
        //start speech recognition
    	Log.d("x swipe:",Float.toString(velocityX));
    	Log.d("y swipe:",Float.toString(velocityY));
    	
    	//detect swipe forward:speech
    	
    	if (velocityX > 0 && velocityX > velocityY) {
    		if (dangerLevel != 5) {
    			displaySpeechRecognizer();
    		} else {
    			//toggle to safe mode
    			dangerLevel = 1;
    			sendToDatabase("I'm safe and happy!", true);
    		}
    	} else if (velocityX < 0 && (-velocityX) > velocityY) {
    		if (dangerLevel != 5) {
    			dangerLevel = 5;
    			sendToDatabase("This is a hostile area.", true);
    			try {
    				sendGet();
    			} catch (Exception e) {
    				// TODO Auto-generated catch block
    				e.printStackTrace();
    			}
    			ttobj.speak("Emergency Contact Message Sent.", TextToSpeech.QUEUE_FLUSH, null);
    		} else {
    			dangerLevel = 1;
    			sendToDatabase("I'm safe and happy!", true);
    		}
    	}
    	
        return true;
        
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

    private void sendToDatabase(String text, boolean needText) {
        try {
            Map<String, Object> val = new HashMap<String, Object>();
            val.put("dangerLevel", dangerLevel);
            val.put("latitude", mLatitude);
            val.put("longitude", mLongitude);
            if (needText) val.put("text", text); 
            Toast.makeText(this, "Syncing...", Toast.LENGTH_SHORT).show();
            fireref.updateChildren(val);
        } catch (Throwable t) {
            Toast.makeText(this, "sending data failed", Toast.LENGTH_SHORT).show();
        }
    };

    @Override
	public boolean onSingleTapUp(MotionEvent e) 
    {
        //tap to show user ID
        String tx = "Your User ID is:"+android_id;
    	Toast toast = Toast.makeText(this, 
                                     tx
                                     , Toast.LENGTH_LONG);
        toast.show();
        ttobj.speak(tx, TextToSpeech.QUEUE_FLUSH, null);
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
        Log.d("Location Update","Location Updated");
        this.sendToDatabase("", false);
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
	public void onSensorChanged(SensorEvent event) {
    	//Log.d("Sensor","Sensor Updated!!!!");
		float diffX = mLastValues[0]-event.values[0];
		if ( diffX < mLowPassFilter )
			diffX = 0.0f;
		
		float diffY = mLastValues[1]-event.values[1];
		if ( diffY < mLowPassFilter )
			diffY = 0.0f;
		
		float diffZ = mLastValues[2]-event.values[2];
		if ( diffZ < mLowPassFilter )
			diffZ = 0.0f;
		
		mLastValues[0] = event.values[0];
		mLastValues[1] = event.values[1];
		mLastValues[2] = event.values[2];
    	
		//Log.d("AccelerationX",Float.toString(diffX));
		
		if ((this.runQueueSize + this.walkQueueSize) < this.SIZELIMIT) {
			if (Math.abs(diffX) > 1) {
				this.runQueueSize++;
			} else {
				this.walkQueueSize++;
			}
		} else {
			this.runQueueSize = 0;
			this.walkQueueSize = 0;
			//if 80% of the time the person is running, send out the warning
			if ((this.runQueueSize - this.walkQueueSize) >= this.QUOTIENT) {
				dangerLevel = 5;
	        	sendToDatabase("I'm running from danger! Help me!",true);
	        	try {
	                sendGet();
	            } catch (Exception e) {
	                e.printStackTrace();
	            }
	        	ttobj.speak("Emergency Contact Message Sent.", TextToSpeech.QUEUE_FLUSH, null);
			} else {
				Log.d("Danger Level","Within Safe Range");
			}
			
		}
		//Log.d("AccelerationX",Float.toString(diffY));
		//Log.d("AccelerationX",Float.toString(diffZ));
		//Log.d("AccelerationY",Float.toString(mLastValues[0]));
		//Log.d("AccelerationY",Float.toString(mLastValues[1]));
		//Log.d("AccelerationZ",Float.toString(mLastValues[2]));
		
		/*if ((mLastValues[0] > 6 && mLastValues[0] < 9) || (mLastValues[1] > 6 && mLastValues[1] < 9)) {
			dangerLevel = 5;
        	sendToDatabase();
        	try {
                sendGet();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
		}*/
		
    	//acquire sound level
    	/*
		try {
			nMetric.start();
			//double db = nMetric.getAmplitude();
			//Log.d("Noise Val", Double.toString(db));
			//nMetric.stop();
    	} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}*/
    	
    }

    
}