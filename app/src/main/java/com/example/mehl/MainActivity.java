package com.example.mehl;

/**
 * Mehl is built on the experimental Google Glass platform. This means that Google Glass could be
 * killed off by Google at any moment and as such development on this app will also stop.
 */

import simplenlg.framework.*;
import simplenlg.lexicon.*;
import simplenlg.realiser.english.*;
import simplenlg.phrasespec.*;
import simplenlg.features.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Random;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


import com.example.api.AlchemyAPI;
import com.example.api.AlchemyAPI_ImageParams;
import com.google.android.glass.media.CameraManager;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.view.LayoutInflater;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

// TODO: Clean up library imports.
// TODO: Handle app activity lifecycle.
// TODO: make sure background threads don't return to a null activity.
// TODO: General polishing pass.

public class MainActivity extends Activity implements OnInitListener
{
	private TextView textview, notificationTextView;
	private ImageView imageview;
	
	private boolean firstPictureTaken = false, cameraLocked;
	private int returnedNodeCount, currentNodeCount;
	
	public File lastPicture;
	public String lastPicturePath;
	public String PictureFileName;
	
	private Handler mHandler = new Handler(Looper.getMainLooper());
	
	NodeList items;
	Element root;

	private Random randomProcessor;

	Camera mCamera;
	private CameraPreview mPreview;
	private String thumbnailPath;
	private String picturePath;
	private boolean cameraLive = false;
	
	private Context mContext;
	
	private Lexicon lexicon;
	private NLGFactory factory;
	private Realiser realiser;

	// nameNouns for testing facial recognition
	private String[] nameNouns = {"Joe", "Dakota", "Tom", "Alexa",
			"Jermaine", "Ted", "Mary", "Jane"};

	// test objectVerbs for scene testing
	private String[] objectVerbs = {"accept","admire","analysed","attack",
			"ban", "bumped", "bury", "blessed", 
			"collect", "carry", "count", "complete",
			"damage", "disapprove", "destroy", "drop",
			"earn", "enjoy", "explain", "extend"};
	
	/** The m gesture detector. */
	private GestureDetector mGestureDetector;
	
	/** The tts. */
	private TextToSpeech tts;

	private static final int TAKE_PICTURE_REQUEST = 1;
	
	
	/**
	 * 
	 * Put your API Key into the variable below.  Can get key from http://www.alchemyapi.com/api/register.html
	 */
	public String AlchemyAPI_Key = "PLACE ALCHEMY API KEY HERE IF YOU WANT TO TEST IT.";
	private FrameLayout cameraPreview;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_main);
       
        mContext = getBaseContext();
        cameraLocked = false;
        firstPictureTaken = false;
        returnedNodeCount = 0;
        currentNodeCount = 0;
        
        tts = new TextToSpeech(this, this);
        mGestureDetector = createGestureDetector(this);

		// Ensure that Android doesn't kill the display on the user.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        
        /*new Thread(new Runnable() {
            public void run() 
            {
            	lexicon = Lexicon.getDefaultLexicon();
     			factory = new NLGFactory(lexicon);
     			realiser = new Realiser();
            }
          }).start(); */
       
    	    
        cameraPreview = (FrameLayout)findViewById(R.id.camera_view);
        notificationTextView = (TextView) findViewById(R.id.notificationText);
        textview = (TextView) findViewById(R.id.footer);

        notificationTextView.setText("Swipe Towards screen to translate picture");
        textview.setText("This WILL take a picture (use two fingers) "+"\n"+"Created by Dakota Gallimore");
        //cameraPreview = (FrameLayout) rootView.findViewById(R.id.camera_view);
        imageview = (ImageView) findViewById(R.id.image);
    }
    
    @Override
    public void onResume()
    {
    	super.onResume();

		// Due to current Glass API limits we must check if activity is not returning from camera
    	if (!firstPictureTaken)
        {
    		InitializeCameraPreview();
    	}
    }
    
    @Override
    public void onPause()
    {
    	super.onPause();

		// Kill camera preview when app is hidden and free up camera for other apps
    	if(mCamera != null)
		{
			mCamera.stopPreview();
			if(cameraLocked)
			{
				mCamera.unlock();
				cameraLocked = false;
			}
		}
    }
    
    protected void onStop()
	{
		super.onStop();

		// Ensue camera was killed off completely on app exit and kill text to speech engine.
		if(mCamera != null)
		{
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
			cameraLocked = false;
		}
		if(tts != null)
			tts.stop();
	}
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onDestroy()
	 */
	protected void onDestroy()
	{
		super.onDestroy();

		// When garbage is ran, make sure all resources for the app is freed for other services.
		if(tts != null)
			tts.shutdown();
		
		if(mCamera != null)
		{
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
			cameraLocked = false;
		}
	}

	/**
	 * Method to initialize the camera view on the UI thread even if called from a background thread.
	 * Sets camera lock and camera live variables for state tracking.
	 */
	public void InitializeCameraPreview()
	{
		runOnUiThread(new Runnable(){

			@Override
			public void run() 
			{
				
				mCamera = getCameraInstance();
				if (mCamera == null)
					Log.i("Camera", "Camera is null");
				else
					System.out.println("Camera is not null");
				
				mPreview = new CameraPreview(mContext, mCamera);
				
				try
				{
					if(!firstPictureTaken)
						cameraPreview.addView(mPreview);
					else
						mCamera.startPreview();
					
					cameraLive = true;
					cameraLocked = true;	
				}
				catch (Exception e)
				{
					Log.e("adding Camera View", e.toString());
				}	
			}
			
			
		});
		
	}

	/**
	 * Query the Alchemy API outside the main thread to avoid UI hangs.
	 * @param call
	 */
    private void SendAlchemyCall(final String call)
    {
    	Thread thread = new Thread(new Runnable()
    	{
    	    @Override
    	    public void run() {
    	        try {
    	        	SendAlchemyCallInBackground(call);
    	        } catch (Exception e) {
    	            e.printStackTrace();
    	        }
    	    }
    	});

    	thread.start(); 
    }

	/**
	 * Handle the Alchemy API response
	 * @param call
	 */
	private void SendAlchemyCallInBackground(final String call)
    {
		// Update UI components from background thread.
    	runOnUiThread(new Runnable() 
    	{ 
        	@Override
            public void run() 
        	{
		    	textview.setText("Making call: "+call);
        	}
    	});

		// Make sure Alchemy variables are null before retrieving new data.
    	Document doc = null;
    	AlchemyAPI api = null;

		try
    	{
    		api = AlchemyAPI.GetInstanceFromString(AlchemyAPI_Key);
    	}
    	catch( IllegalArgumentException ex )
    	{
    		textview.setText("Error loading API.  Check that you have a valid API key set in the API_Key variable.");
    		return;
    	}

    	//String someString = urlText.getText().toString();
    	try
    	{
			// Alchemy image Classifier specified as desired API call.
    		if( "imageClassify".equals(call))
    		{
				// Grab image taken from camera (displayed on imageview) and convert it into an
				// Alchemy readable format.Keep at 100% quality for better API accuracy.

    			Bitmap bitmap = ((BitmapDrawable)imageview.getDrawable()).getBitmap();
    			ByteArrayOutputStream stream = new ByteArrayOutputStream();
    			bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
    			byte[] imageByteArray = stream.toByteArray();
    			
    	        AlchemyAPI_ImageParams imageParams = new AlchemyAPI_ImageParams();
    	        imageParams.setImage(imageByteArray);
    	        imageParams.setImagePostMode(AlchemyAPI_ImageParams.RAW);
    	        doc = api.ImageGetRankedImageKeywords(imageParams);
    			ShowTagInTextView(doc, "text");
    		}
    	}
    	catch(Exception e)
    	{
    		e.printStackTrace();
    	 	textview.setText("Error: " + e.getMessage());
    	}
    }

	/**
	 * Update UI Textview with returned API values.
	 * @param doc
	 * @param tag
	 */
	private void ShowTagInTextView(final Document doc, final String tag)
    {
    	Log.d(getString(R.string.app_name), doc.toString());

		// Run UI update on main thread as this can be called from a background thread throwing
		// an exception.
    	runOnUiThread(new Runnable() 
    	{ 
        	@Override
            public void run() 
        	{
		    	root = doc.getDocumentElement();
		    	items = root.getElementsByTagName(tag);
				if (items.getLength() == 0)
		    	{
		    		textview.setText("Could not locate an object, please ensure object is clearly positioned in camera view");
		    	}
		    	else
		    	{
					// Grab first result from Alchemy (highest confidence)

			       	Node concept = items.item(currentNodeCount);
			       	String astring = concept.getNodeValue();
			       	astring = concept.getChildNodes().item(0).getNodeValue();


					/*
					 * Commented code handles random sentence generation based around the first returned
					 * Alchemy result. Should apply machine learning and possibly move off platform
					 * instead of handle calculations on the Glass device.
					 */

					//Random rand = new Random();
		            //SPhraseSpec p = factory.createClause();
		    	    //p.setSubject(nameNouns[(rand.nextInt(((nameNouns.length - 1) - 0)+1)+0)].toString());
		    	    //p.setVerb(objectVerbs[(rand.nextInt(((objectVerbs.length - 1) - 0)+1)+0)].toString());
		    	    //p.setObject(astring);
		    	    //String output2 = realiser.realiseSentence(p); // Realiser created earlier.
		    	        
			       	textview.setText(" " + astring + " - "); //+ output2);
		    	}
        	}
    	});
    }

	/**
	 * Handle camera activity request
	 * @param requestCode
	 * @param resultCode
	 * @param data
	 */
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
    { 
    	super.onActivityResult(requestCode, resultCode, data); 
    	
    	if (requestCode == TAKE_PICTURE_REQUEST && resultCode == RESULT_OK) 
    	{
    		thumbnailPath = data.getStringExtra(CameraManager.EXTRA_THUMBNAIL_FILE_PATH);
            picturePath = data.getStringExtra(CameraManager.EXTRA_PICTURE_FILE_PATH);
            
            notificationTextView.setText("Processing...Please wait");
            processPictureWhenReady(thumbnailPath);
        }
    }


	/**
	 * Kill camera if it's displaying and launch camera activity for results
	 */
	private void takePicture()
    {
    	if(mCamera != null)
		{
			mCamera.stopPreview();
			mCamera.unlock();
			cameraLocked = false;
		}
    	Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, TAKE_PICTURE_REQUEST);
    }

	/**
	 * Recursive method to grab saved image from camera activity. We need recursion to account for slow image saving.
	 * If image exists, send to Alchemy and start processing image.
	 * @param picturePath
	 */
	private void processPictureWhenReady(final String picturePath)
    {
    	
        final File pictureFile = new File(picturePath);

        if (pictureFile.exists()) 
        {
            // The picture is ready; process it.
        	lastPicturePath = new String(pictureFile.getAbsolutePath());
        	textview.setText("Done!");
        	imageview.setVisibility(View.VISIBLE);
        	imageview.setImageBitmap(decodeSampledBitmapFromFile(pictureFile.getAbsolutePath(), 1500, 900));
        	//imageview.setImageBitmap(mBitmap);
        	notificationTextView.setText("");
        	firstPictureTaken = true;
        	SendAlchemyCall("imageClassify");
        	
        } else 
        {
            // The file does not exist yet. Before starting the file observer, you
            // can update your UI to let the user know that the application is
            // waiting for the picture (for example, by displaying the thumbnail
            // image and a progress indicator).
        	
        	textview.setText("Verifying Picture integrity...");
        	
            final File parentDirectory = pictureFile.getParentFile();
            FileObserver observer = new FileObserver(parentDirectory.getPath(),
                    FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO) {
                // Protect against additional pending events after CLOSE_WRITE
                // or MOVED_TO is handled.
                private boolean isFileWritten;

                @Override
                public void onEvent(int event, String path) 
                {
                    if (!isFileWritten) {
                        // For safety, make sure that the file that was created in
                        // the directory is actually the one that we're expecting.
                        File affectedFile = new File(parentDirectory, path);
                        isFileWritten = affectedFile.equals(pictureFile);

                        if (isFileWritten) {
                            stopWatching();

                            // Now that the file is ready, recursively call
                            // processPictureWhenReady again (on the UI thread).
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    processPictureWhenReady(picturePath);
                                }
                            });
                        }
                    }
                }
            };
            observer.startWatching();
        }
    }

	/**
	 * Reduce image size to help save data when sending to Alchemy and saving to storage.
	 * @param path
	 * @param reqWidth
	 * @param reqHeight
	 * @return
	 */
    public static Bitmap decodeSampledBitmapFromFile(String path,
            int reqWidth, int reqHeight) { // BEST QUALITY MATCH

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        // Calculate inSampleSize
            // Raw height and width of image
            final int height = options.outHeight;
            final int width = options.outWidth;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            int inSampleSize = 1;

            if (height > reqHeight) {
                inSampleSize = Math.round((float)height / (float)reqHeight);
            }

            int expectedWidth = width / inSampleSize;

            if (expectedWidth > reqWidth) {
                //if(Math.round((float)width / (float)reqWidth) > inSampleSize) // If bigger SampSize..
                inSampleSize = Math.round((float)width / (float)reqWidth);
            }


        options.inSampleSize = inSampleSize;

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeFile(path, options);
      }

	/**
	 * Make sure we can grab the camera.
	 * @return
	 */
	public static Camera getCameraInstance()
    {
	    Camera c = null;
	    try {
	        c = Camera.open(); // attempt to get a Camera instance
	    }
	    catch (Exception e){
	        // Camera is not available (in use or does not exist)
	    	Log.e("Camera.open", e.toString());
	    }
	    return c; // returns null if camera is unavailable
	}
    
    /**
	 * Creates the gesture detector and handles finger motions for the side Glass touch pad.
	 *
	 * @param context the context
	 * @return the gesture detector
	 */
	private GestureDetector createGestureDetector(Context context) {
	    GestureDetector gestureDetector = new GestureDetector(context);
	        //Create a base listener for generic gestures
	        gestureDetector.setBaseListener( new GestureDetector.BaseListener() {
	            @Override
	            public boolean onGesture(Gesture gesture) 
	            {
	                if (gesture == Gesture.TAP) {
	                	openOptionsMenu();
	                    return true;
	                } else if (gesture == Gesture.TWO_TAP) 
	                {
	                    // do something on two finger tap
	                	//displaySpeechRecognizer();
	                    return true;
	                } else if (gesture == Gesture.SWIPE_RIGHT) 
	                {
	                    // do something on right (forward) swipe
	                	if(firstPictureTaken == true)
	                	{
	                		runOnUiThread(new Runnable() 
	                		{
                                @Override
                                public void run() 
                                {
					                if(currentNodeCount < returnedNodeCount)
					                {
					                	currentNodeCount = currentNodeCount + 1;
					                	Node concept = items.item(currentNodeCount);
					                	String astring = concept.getNodeValue();
					                	astring = concept.getChildNodes().item(0).getNodeValue();
					                	textview.setText("   " + astring + " - " + "\n" + "returned: " + returnedNodeCount);
					                }
				                }
			                });
			        	}
	                    return true;
	                } 
	                else if (gesture == Gesture.SWIPE_LEFT)
	                {
	                	if(firstPictureTaken == true)
	                	{
	                		runOnUiThread(new Runnable() 
	                		{
                                @Override
                                public void run() 
                                {
                                	if(currentNodeCount < 0)
				                		currentNodeCount = 0;
                                	if(currentNodeCount != 0 && returnedNodeCount != 0)
				                	{
				                		currentNodeCount = currentNodeCount - 1;
				                		Node concept = items.item(currentNodeCount);
							        	String astring = concept.getNodeValue();
							        	astring = concept.getChildNodes().item(0).getNodeValue();
							        	textview.setText("   " + astring + " - " + "\n" + "returned: " + returnedNodeCount);
				                	}
                                }
                            });
	                		
	                	}
			        	return true;
	                }
	                else if (gesture == Gesture.TWO_SWIPE_LEFT) 
	                {
		                readAloud(textview.getText().toString());
	                    return true;
	                }
	                else if (gesture == Gesture.TWO_SWIPE_RIGHT) {
	                	if(firstPictureTaken == true)
	                	{
	                		runOnUiThread(new Runnable() 
	                		{
                                @Override
                                public void run() 
                                {
        	                		if(cameraLive)
        	                		{
        	                			takePicture();
        	                			cameraLive = false;
        	                		}
        	                		else
        	                		{
        	                			imageview.setImageBitmap(null);
        	                			imageview.setVisibility(View.INVISIBLE);
                                        lastPicture = new File(picturePath);
                                        File lastThumbnail = new File(thumbnailPath);
            	                		boolean pictureDeleted = lastPicture.delete();
            	                		boolean thumbDeleted = lastThumbnail.delete();
            	                		if(!pictureDeleted && !thumbDeleted)
            	                		{
            	                                    textview.setText("ERROR: Picture not fully deleted..");
            	                		}
            	                		else
            	                		{
            	                			lastPicture = null;
            	                		}
            	                		try {
											mCamera.reconnect();
											cameraLocked = true;
											mCamera.startPreview();
										} catch (IOException e) {
											e.printStackTrace();
										}
        	                			
        	                		}
                                }
                            });
	                		
	                	}
	                	else
	                	{
	                		takePicture();
	                		cameraLive = false;
	                	}
	                    return true;
	                }
	                return false;
	            }
	        });
	        gestureDetector.setFingerListener(new GestureDetector.FingerListener() {
	            @Override
	            public void onFingerCountChanged(int previousCount, int currentCount) {

	            }
	        });
	        return gestureDetector;
	    }
	
	/*
     * Send generic motion events to the gesture detector
     */
    /* (non-Javadoc)
	 * @see android.app.Activity#onGenericMotionEvent(android.view.MotionEvent)
	 */
	@Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (mGestureDetector != null) 
        {
            return mGestureDetector.onMotionEvent(event);
        }
        return false;
    }

	/**
	 * Initialize the Text to Speech engine.
	 * @param status
	 */

	@Override
	public void onInit(int status) 
	{
		if(status == TextToSpeech.SUCCESS)
			tts.setLanguage(Locale.ENGLISH);
		else
		{}
	}
	 /**
     * Read text aloud.
     *
     * @param textToRead the text to read
     */
    public void readAloud(String textToRead)
    {
    	tts.speak(textToRead, TextToSpeech.QUEUE_FLUSH, null);
    }

	/**
	 * Generate sentences based off returned Alchemy keywords. AsyncTask to account for language translation off UI thread in the future.
	 */
	private class SetNLGSentence extends AsyncTask<String, Void, String>
    {

        @Override
        protected String doInBackground(String... params) {
        	
            return "Executed";
        }

        @Override
        protected void onPostExecute(String result) {
        	Random rand = new Random();
        	SPhraseSpec p = factory.createClause();
	        p.setSubject(nameNouns[(rand.nextInt((nameNouns.length - 0)+1)+0)].toString());
	        p.setVerb(objectVerbs[(rand.nextInt((objectVerbs.length - 0)+1)+0)].toString());
	        p.setObject("cat");
	        
	        String output2 = realiser.realiseSentence(p); // Realiser created earlier.
	        
	        textview.setText(output2);
            // might want to change "executed" for the returned string passed
            // into onPostExecute() but that is upto you
        }

        @Override
        protected void onPreExecute() {}

        @Override
        protected void onProgressUpdate(Void... values) {}
    }
    
    @Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    if (keyCode == KeyEvent.KEYCODE_CAMERA) {
	        // Stop the preview and release the camera.
	        // Execute your logic as quickly as possible
	        // so the capture happens quickly.
	    	mCamera.stopPreview();
	    	mCamera.release();
	        return false;
	    } else 
	    {
	        return super.onKeyDown(keyCode, event);
	    }
	}
}