package eyeinteraction;


import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import eyeinteraction.ui.camera.CameraSourcePreview;
import eyeinteraction.ui.camera.GraphicOverlay;

import eyeinteraction.utils.*;

/**
 * Activity for the face tracker app.  This app detects faces with the rear facing camera, and draws
 * overlay graphics to indicate the position, size, and ID of each face.
 */
public final class EvaluationThreeMethodTwoActivity extends AppCompatActivity implements View.OnClickListener, View.OnSystemUiVisibilityChangeListener {
    private final String TAG = EvaluationThreeMethodTwoActivity.this.getClass().getSimpleName();

    private CameraSource mCameraSource = null;
    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;
    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    String participantCode;
//    String glassCode;
    int numOfPhrases = 10;  // number of phrases to type
    int targetPhraseIndex = 0;

    Button mBtConfirm;
    TextView mTvPresented;
    EditText mEtTranscribed;
    String presentedText = "";
    String transcribedText = "";
    RelativeLayout layer1;
    RelativeLayout layer3;
    RelativeLayout currentLayer;


    String[] phrases;
    long startTime = 0;
    int timeForPhrase;
    BufferedWriter sd2;
    File f2;
    String sd2Leader;
    final String SD2_HEADER = "Participant, Condition, Presented_char,Transcribed_char,Time(s),MSD,"
            + "Entry_speed(wpm),Error_rate(%)\n";
    String condition = "iBlink";

    File mDetailFile;
    PrintWriter mDetailWriter;
    StringBuilder mDetailBuilder = new StringBuilder();

    View decorView;

    private Classifier2D mClassifier;
    private String mGesture;

    boolean modelReady = false;

    ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

    //==============================================================================================
    // Activity Methods
    //==============================================================================================

    /**
     * Initializes the UI and initiates the creation of a face detector.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_evaluation_three_method_two);

        Bundle b = getIntent().getExtras();
        participantCode = b.getString("participantCode");
//        glassCode = b.getString("glassCode");
        String repeats = b.getString("numPhrases");
        numOfPhrases = Integer.parseInt(repeats);
        String testOrPractice = b.getString("TestOrPractice");

        //String filename = participantCode + "_" + glassCode + "_3_iBlink_" + System.currentTimeMillis() + ".csv";

        phrases = getResources().getStringArray(R.array.phrases2);

        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            requestCameraPermission();
        }

        layer1 = (RelativeLayout)findViewById(R.id.layer1);
        layer3 = (RelativeLayout)findViewById(R.id.layer3);
        currentLayer = layer1;
        layer1.setVisibility(View.VISIBLE);
        layer3.setVisibility(View.INVISIBLE);

        mBtConfirm = (Button)findViewById(R.id.button_textentry);
        mBtConfirm.setOnClickListener(this);
        mTvPresented = (TextView)findViewById(R.id.textView_presented);
        mEtTranscribed = (EditText)findViewById(R.id.editText_Input);
        mEtTranscribed.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId,
                                          KeyEvent event) {
                if (event != null&& (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    InputMethodManager in = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

                    // NOTE: In the author's example, he uses an identifier
                    // called searchBar. If setting this code on your EditText
                    // then use v.getWindowToken() as a reference to your
                    // EditText is passed into this callback as a TextView

                    in.hideSoftInputFromWindow(v.getApplicationWindowToken(), 0);
                            //InputMethodManager.HIDE_NOT_ALWAYS);
//                    try {
//                        doEndOfPhrase(System.currentTimeMillis());
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }

                    // Must return true here to consume event
                    return true;
                }
                return false;
            }
        });


        // ===================
        // File initialization
        // ===================

//        String baseFilename = participantCode + "_" + glassCode + "_3_"  + condition + "_" + numOfPhrases + "_" + testOrPractice + "_" + System.currentTimeMillis();
        String baseFilename = participantCode + "_eval_3_"  + condition + "_" + numOfPhrases + "_" + testOrPractice + "_" + System.currentTimeMillis();


        f2 = new File(getApplicationContext().getFilesDir(), baseFilename + ".csv");
        if(!f2.exists()){
            try {
                f2.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try
        {
            sd2 = new BufferedWriter(new FileWriter(f2));
//            sd2Leader = String.format("%s,%s,%s", participantCode,glassCode,
//                    condition);
            sd2Leader = String.format("%s,%s", participantCode,
                    condition);
            // output header in sd2 file
            sd2.write(SD2_HEADER, 0, SD2_HEADER.length());
            sd2.flush();
        } catch (IOException e)
        {
            super.onDestroy();
            this.finish();
        }

        String detailName = baseFilename + "_detail.csv";
        mDetailFile = new File(getApplicationContext().getFilesDir(), detailName);
        if (!mDetailFile.exists()) {
            try {
                mDetailFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            mDetailWriter = new PrintWriter(new FileOutputStream(mDetailFile));
            mDetailWriter.append("task_id,action_name,curr_result,time_stamp\n");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        // end file initialization

        //shuffle the phrases so that each participant sees different phrases
        phrases = getResources().getStringArray(R.array.phrases2);
        List<String> phrasesList = Arrays.asList(phrases);
        Collections.shuffle(phrasesList);
        phrases = (String[])phrasesList.toArray();
        preparePhrase();

        setupMainWindowDisplayMode();

        mClassifier = new Classifier2D(participantCode, getExternalFilesDir(null), Classifier2D.BufferMethod.ROLLING_WINDOW, 15);
//        mClassifier = new Classifier2D(participantCode, getApplicationContext().getFilesDir(), Classifier2D.BufferMethod.ROLLING_WINDOW, 15);
    }

    void preparePhrase(){
        presentedText = phrases[targetPhraseIndex];
        mTvPresented.setText(presentedText);
        transcribedText = "";
        mEtTranscribed.setText(transcribedText);
        startTime = System.currentTimeMillis();
    }


    @Override
    public void onClick(View view) {
        switch(view.getId()){
            case R.id.button_textentry:
                try {
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 100);

                    mDetailBuilder.append(String.format("%d,%s,%s,%d\n", targetPhraseIndex, "Next",
                            "[" + mEtTranscribed.getText().toString().trim() + "]", System.currentTimeMillis() - startTime));

                    doEndOfPhrase(System.currentTimeMillis());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            default:
                break;
        }
    }

    private void doEndOfPhrase(long timeStampEnd) throws IOException {
        StringBuilder resultsString = new StringBuilder(500);
        resultsString.append("Thank you!\n");

        timeForPhrase = (int) (timeStampEnd - startTime);

        String s1 = presentedText;
        transcribedText = mEtTranscribed.getText().toString();
        String s2 = transcribedText.trim();
        s1 = s1.toLowerCase(Locale.US);
        s2 = s2.toLowerCase(Locale.US);

        resultsString.append(String.format("Presented...\n   %s\n", s1));
        resultsString.append(String.format("Transcribed...\n   %s\n", s2));

        StringBuilder sd2Stuff = new StringBuilder(500);
        sd2Stuff.append(String.format("%s,", sd2Leader)); // begin with the leader created earlier

        // PRESENTED CHARACTERS (PHRASE LENGTH)
        sd2Stuff.append(String.format("%s,", s1));

        // NUMBER OF CHARACTERS OF TEXT PRODUCED
        sd2Stuff.append(String.format("%s,", s2));

        // TIME (SECONDS)
        float d = timeForPhrase / 1000.0f;
        sd2Stuff.append(String.format("%.2f,", d));

        // MSD
        MSD s1s2 = new MSD(s1.toLowerCase(Locale.US).trim(), s2.toLowerCase(Locale.US).trim());
        int msd = s1s2.getMSD();
        sd2Stuff.append(String.format("%d,", msd));

        // ENTRY SPEED (WPM)
        d = wpm(s2, timeForPhrase);
        sd2Stuff.append(String.format("%.2f,", d));
        resultsString.append(String.format("Entry speed = %.2f wpm\n", d));

        // ERROR RATE (%)
        d = (float)s1s2.getErrorRateNew();
        sd2Stuff.append(String.format("%.2f\n", d));
        resultsString.append(String.format("Error rate = %.2f%%\n", d));

        // dump data to sd1 and sd2 files
        try
        {
            sd2.write(sd2Stuff.toString(), 0, sd2Stuff.length());
            sd2.flush();
        } catch (IOException e)
        {
            // Log.d("MYDEBUG", "ERROR WRITING TO DATA FILE! e = " + e);
            //this.finish();
        }

        targetPhraseIndex ++;
        //Log.i(TAG, "target phrase index: " + targetPhraseIndex);
        if(targetPhraseIndex < numOfPhrases) {
            preparePhrase();
        }
        else {
            sd2.close();

            // Write detail
            mDetailWriter.append(mDetailBuilder.toString());
            mDetailWriter.flush();
            mDetailWriter.close();

            //mTvPresented.setText(resultsString.toString());
            //this.finish();
            Toast.makeText(getApplicationContext(), "Task completed!", Toast.LENGTH_LONG).show();
        }
    }


    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }


    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     */
    private void createCameraSource() {

        Context context = getApplicationContext();
        FaceDetector detector = new FaceDetector.Builder(context)
                .setProminentFaceOnly(true)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS) //.ALL_LANDMARKS)
                .build();

        detector.setProcessor(
                new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory())
                        .build());

        if (!detector.isOperational()) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.  Usually this
            // completes before the app is run for the first time.  But if that download has not yet
            // completed, then the above call will not detect any faces.
            //
            // isOperational() can be used to check if the required native library is currently
            // available.  The detector will automatically become operational once the library
            // download completes on device.
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }

        int width = FaceGraphic.CAMERA_WIDTH;
        int height = FaceGraphic.CAMERA_HEIGHT;
        mCameraSource = new CameraSource.Builder(context, detector)
                .setRequestedPreviewSize(width, height)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(30.0f)
                .build();
    }

    /**
     * Set visual feedback for face detector result
     * @param flag
     */
    private void setFaceStatus(boolean flag, float left, float right)
    {
    }

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();
//        View decorView = getWindow().getDecorView();
//        // Hide both the navigation bar and the status bar.
//        // SYSTEM_UI_FLAG_FULLSCREEN is only available on Android 4.1 and higher, but as
//        // a general rule, you should design your app to hide the status bar whenever you
//        // hide the navigation bar.
//        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE;
//        decorView.setSystemUiVisibility(uiOptions);
        setupMainWindowDisplayMode();
        startCameraSource();

        modelReady = mClassifier.buildModels();

        if(!modelReady && ! mClassifier.hasExistingSegmentModel()){
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle("Warning: Segment Model not found")
                    .setMessage("Are you sure you want to proceed without an available segment model?")
                    .setPositiveButton("Yes", null)
                    .setNegativeButton("No", new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .show();
        }
        else {
            modelReady = true;
        }

        Log.i(TAG, "model flag: " + modelReady);
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release();
        }
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            createCameraSource();
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Face Tracker sample")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }

    //==============================================================================================
    // Camera Source Preview
    //==============================================================================================

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    @Override
    public void onSystemUiVisibilityChange(int i) {
        setupMainWindowDisplayMode();
    }


    //==============================================================================================
    // Graphic Face Tracker
    //==============================================================================================

    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, Face item) {

        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            if (face == null) {
                setFaceStatus(false, -1, -1);
                return;
            }

            float leftOpenProb = face.getIsLeftEyeOpenProbability();
            float rightOpenProb = face.getIsRightEyeOpenProbability();
            if(!modelReady){
                return;
            }

           // mGesture = mClassifier.classify(leftOpenProb, rightOpenProb);
            String prevGesture = mGesture;
            mGesture = mClassifier.classifySegment(leftOpenProb, rightOpenProb);
            Log.i(TAG, "gesture: " + mGesture);
            if(mGesture.length() > 0) {
                //String state = mGesture.split(",")[0];
                String state = mGesture;
                if(state.equals("Close")){
                    layer1.post(new Runnable() {
                        @Override
                        public void run() {
                            layer1.setVisibility(View.INVISIBLE);
                            layer3.setVisibility(View.VISIBLE);
                            //layer1.invalidate();
                        }
                    });
                }
                else{
                    layer1.post(new Runnable() {
                        @Override
                        public void run() {
                            layer1.setVisibility(View.VISIBLE);
                            layer3.setVisibility(View.INVISIBLE);
                            //layer1.invalidate();
                        }
                    });
                }

                if (!mGesture.equals(prevGesture)) {
                    mDetailBuilder.append(String.format("%d,%s_%s,%s,%d\n", targetPhraseIndex, prevGesture, mGesture,
                            "[" + mEtTranscribed.getText().toString().trim() + "]", System.currentTimeMillis() - startTime));
                }
            }
        }



        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            setFaceStatus(false, -1, -1);
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {

        }
    }

    private void setupMainWindowDisplayMode() {
        View decorView = setSystemUiVisilityMode();
        decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                setSystemUiVisilityMode(); // Needed to avoid exiting immersive_sticky when keyboard is displayed
            }
        });
    }

    private View setSystemUiVisilityMode() {
        View decorView = getWindow().getDecorView();
        int options;
        options =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                        | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        decorView.setSystemUiVisibility(options);
        return decorView;
    }

    /**
     * Compute text entry speed in words per minute (wpm).
     *
     * @param text
     *            a phrase of text
     * @param msTime
     *            time to enter the phrase in milliseconds
     * @return entry speed in words per minute (wpm) or -1 if time is <= 0
     */
    public static float wpm(String text, long msTime)
    {
        float speed = text.length();
        if (msTime > 0)
            return speed / (msTime / 1000.0f) * 12.0f; // Note: 60 / 5 = 12
        else
            return -1f;
    }
}
