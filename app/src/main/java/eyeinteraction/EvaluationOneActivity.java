package eyeinteraction;


import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import eyeinteraction.ui.camera.CameraSourcePreview;
import eyeinteraction.ui.camera.GraphicOverlay;
import eyeinteraction.utils.Classifier2D;
import eyeinteraction.utils.CountDownAnimation;

/**
 * Activity for the face tracker app.  This app detects faces with the rear facing camera, and draws
 * overlay graphics to indicate the position, size, and ID of each face.
 */
public final class EvaluationOneActivity extends AppCompatActivity implements View.OnClickListener {
    private final boolean chineseStr = false;
    private boolean initCounter = false;

    private final String TAG = EvaluationOneActivity.this.getClass().getSimpleName();

    private CameraSource mCameraSource = null;
    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;
    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;
    private float currLeftEyeScore = 0;
    private float currRightEyeScore = 0;
    private float prevLeftEyeScore = 0;
    private float prevRightEyeScore = 0;
    private String currentGesture = "";

    Button mBtEvaluation;
    Button mBtRedo;
    Button mBtPause;
    TextView mTvDetail;
    TextView mTvCntDown;
    TextView mTvFace;
//    CountDownAnimation mCntDownAnimation;

    private long timeStarted = 0;
    private static final long MAX_TIME_TRAINING = 20000;
    private int validCounter = 0;
    private static final int MAX_VALID_POINTS = 200;
    private ToneGenerator tone;

    // Major classifier used for training & testing.
    private Classifier2D mClassifier;
    // Temporary list for training data. Maximum length controlled by time & valid points.
    private ArrayList<Float> mLeftEyeList = new ArrayList<>();
    private ArrayList<Float> mRightEyeList = new ArrayList<>();
    private boolean mHasLoadedModel = false;
    private boolean mEvaluationTag = false;
    // Recent history of gestures, only for debugging.
    private ArrayList<String> mGestureResults = new ArrayList<>();

    final String RAW_DATA_FILE = "evaluation_one_detail.csv";
    File mRawDataFile;
    StringBuilder mRawDataBuilder = new StringBuilder();
    private PrintWriter mRawDataWriter = null;

    ArrayList<String> eyeGestures = new ArrayList<String>();
    HashMap<String,String> eyeGesturesCmdDetail = new HashMap<String,String>();

    String participantCode;
    int repeatTimes = 1;  // sets of repetitions of the eyelid gestures
    int trialCnt = 0;
    final int startCnt = 1; //3; //count down timer

    // Not allowed to redo more than once.
    private boolean mRedoAllowed = true;
    // marking the real trial number including the redo.
    private int realTrialCnt = 0;

    private final String[] EYE_GESTURES = {"cLo", "cRo", "cL-o", "cR-o", "cB-o","cB-RLo", "cB-LRo", "dB", "sB"};
    private HashMap<String, Integer> mErrorCounter = new HashMap<>();
    private HashMap<String, Integer> mCorrectCounter = new HashMap<>();

    // Evaluation process
    final int MAX_ERROR_REPEAT = 3;
    private int mCurrentError = 0;
    // Logic switch: always allow next, but add more at the end.
    private boolean mAllowNext = true;
    // Format: trial num, time stamp, task gesture name, detected gesture name (xx;yy;zz, 1+ gesture)

    final String EVALUATION_RESULT_FILE = "evaluation_one_result.csv";
    private File mResultFile;
    private StringBuilder mResultBuilder = new StringBuilder();
    private PrintWriter mResultWriter;

    // All the predicted gestures (correct or wrong) during this current session.
    // Reset for each 'actionStart'.
    private ArrayList<String> mPredictedGestures = new ArrayList<>();

    // Lock for button.
    private long mLastPressedTime = 0;

    private Map<String, String> EN2CN = new HashMap<String, String>();

    private boolean counterPaused = false;
    private CountDownTimer countdown = new CountDownTimer(7000, 1000) {
        @Override
        public void onTick(long l) {
            mBtEvaluation.setText("Ready for this gesture?\nTimer: " + l / 1000);
//            mBtEvaluation.setText("请准备好做以上动作\n倒计时：" + l / 1000);
        }

        @Override
        public void onFinish() {
            clickOnMainBtn();
        }
    };

    //  Collection Timer: Short; Medium; Long
    private final int[] collectLength = {4500, 7000, 9000};

    private CountDownTimer collectTimerShort = new CountDownTimer(collectLength[0], 1000) {
        @Override
        public void onTick(long l) {
            commonTick(l);
        }

        @Override
        public void onFinish() {
            commonFinish();
        }
    };

    private CountDownTimer collectTimerMedium = new CountDownTimer(collectLength[1], 1000) {
        @Override
        public void onTick(long l) {
            commonTick(l);
        }

        @Override
        public void onFinish() {
            commonFinish();
        }
    };

    private CountDownTimer collectTimerLong = new CountDownTimer(collectLength[2], 1000) {
        @Override
        public void onTick(long l) {
            commonTick(l);
        }

        @Override
        public void onFinish() {
            commonFinish();
        }
    };

    private final CountDownTimer[] collectTimers = {collectTimerShort, collectTimerMedium, collectTimerLong};
    private HashMap<String, Integer> gesture2length = new HashMap<>();

    private boolean collectCounting = false;

    // Count down unified callback:
    private void commonTick(long l) {
        collectCounting = true;
        mBtEvaluation.setText("Start! Please wait for a few seconds if you have finished..." + l / 1000);
//        mBtEvaluation.setText("开始！做完动作后请等待程序自动结束..." + l / 1000);
    }

    private void commonFinish() {
        // as if I pressed the button to stop.
        collectCounting = false;
        clickOnMainBtn();
    }

    private void initGestureDict() {
        gesture2length.put("cLo", 0);
        gesture2length.put("cRo", 0);
        gesture2length.put("sB", 0);
        gesture2length.put("dB", 0);

        gesture2length.put("cL-o", 1);
        gesture2length.put("cR-o", 1);
        gesture2length.put("cB-o", 1);

        gesture2length.put("cB-LRo", 2);
        gesture2length.put("cB-RLo", 2);
    }

    //==============================================================================================
    // Activity Methods
    //==============================================================================================

    /**
     * Initializes the UI and initiates the creation of a face detector.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        EN2CN.put("Open","检测结果：双眼睁开");
        EN2CN.put("Close", "检测结果：单眼闭合/双眼闭合");

        EN2CN.put("cLo", "只闭左眼（短）");
        EN2CN.put("cRo", "只闭右眼（短）");
        EN2CN.put("cL-o", "只闭左眼（长）");
        EN2CN.put("cR-o", "只闭右眼（长）");
        EN2CN.put("cB-o", "闭双眼（长）");
        EN2CN.put("cB-RLo", "闭双眼-开右眼（长）");
        EN2CN.put("cB-LRo", "闭双眼-开左眼（长）");
        EN2CN.put("dB", "闭双眼-闭双眼");
        EN2CN.put("sB", "闭双眼");

        initGestureDict();

        setContentView(R.layout.activity_evaluation_one);

        Bundle b = getIntent().getExtras();
        participantCode = b.getString("participantCode");

        String repeats = b.getString("repeatsCode");
        repeatTimes = Integer.parseInt(repeats);

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

        mBtEvaluation = (Button)findViewById(R.id.bt_evaluation_one_ctrl);
        mBtEvaluation.setBackgroundColor(Color.rgb(20, 204, 54));
        mBtEvaluation.setOnClickListener(this);

        mBtRedo = (Button)findViewById(R.id.bt_redo);
        mBtRedo.setBackgroundColor(Color.rgb(255,165,0));
        mBtRedo.setOnClickListener(this);

        mBtPause = (Button)findViewById(R.id.bt_pause);
        mBtPause.setOnClickListener(this);

        mTvDetail = (TextView)findViewById(R.id.tv_detail);
        mTvCntDown = (TextView)findViewById(R.id.tv_countdown);
        mTvFace = (TextView)findViewById(R.id.tv_face);

        int[] shuffle_fixed = {0, 6, 4, 3, 5, 7, 2, 8, 1};
        assert shuffle_fixed.length == EYE_GESTURES.length;

        for(int i = 0; i < EYE_GESTURES.length; i++){
            String ges = EYE_GESTURES[i];
            mErrorCounter.put(ges, 0);
            mCorrectCounter.put(ges, 0);
        }

//        Collections.shuffle(eyeGestures);

        // instead of repeat aaa bbb ccc, we use abc abc abc, without shuffle.

        for (int j = 0; j < repeatTimes; ++j){
            for (int i = 0; i < shuffle_fixed.length; ++i){
                eyeGestures.add(EYE_GESTURES[shuffle_fixed[i]]);
            }
        }

        if (chineseStr) {
            eyeGesturesCmdDetail.put("cLo",    "只闭左眼（短）：\n闭一下<b>左</b>眼，然后睁开                ");
            eyeGesturesCmdDetail.put("cRo",    "只闭右眼（短）：\n闭一下<b>右</b>眼，然后睁开                ");
            eyeGesturesCmdDetail.put("cL-o",   "只闭左眼（长）：\n长闭<b>左</b>眼，<i>3秒</i>后睁开                 ");
            eyeGesturesCmdDetail.put("cR-o",   "只闭右眼（长）：\n长闭<b>右</b>眼，<i>3秒</i>后睁开                 ");
            eyeGesturesCmdDetail.put("cB-o",   "闭双眼（长）：\n长闭<b>双</b>眼，<i>3秒</i>后睁开                 ");
            eyeGesturesCmdDetail.put("cB-RLo", "闭双眼-开右眼（长）：\n长闭<b>双</b>眼，<i>3秒</i>后先睁开<b>右</b>眼，再过3秒后全部睁开");
            eyeGesturesCmdDetail.put("cB-LRo", "闭双眼-开左眼（长）：\n长闭<b>双</b>眼，<i>3秒</i>后先睁开<b>左</b>眼，再过3秒后全部睁开");
            eyeGesturesCmdDetail.put("dB",     "闭双眼（短，2次）：\n闭一下<b>双</b>眼，睁开，再闭一下<b>双</b>眼，睁开");
            eyeGesturesCmdDetail.put("sB",     "闭双眼（短）：\n闭一下<b>双</b>眼，睁开              ");
        }
        else {
            eyeGesturesCmdDetail.put("cLo",    "close <b>Left</b> eye and open                ");
            eyeGesturesCmdDetail.put("cRo",    "close <b>Right</b> eye and open               ");
            eyeGesturesCmdDetail.put("cL-o",   "close <b>Left</b> eye, <i>Hold</i>, and open         ");
            eyeGesturesCmdDetail.put("cR-o",   "close <b>Right</b> eye, <i>Hold</i>, and open        ");
            eyeGesturesCmdDetail.put("cB-o",   "close <b>Both</b> eyes, <i>Hold</i>, and open        ");
            eyeGesturesCmdDetail.put("cB-RLo", "close <b>Both</b> eyes, <i>Hold</i>, open <b>Right</b>, <i>Hold</i>, and open Both");
            eyeGesturesCmdDetail.put("cB-LRo", "close <b>Both</b> eyes, <i>Hold</i>, open <b>Left</b>, <i>Hold</i>, and open Both");
            eyeGesturesCmdDetail.put("dB",     "(close Both eyes, open) <b>twice</b>          ");
            eyeGesturesCmdDetail.put("sB",     "close <b>Both</b> eyes, and open              ");
        }

        currentGesture = eyeGestures.get(trialCnt);
        mTvDetail.setText(Html.fromHtml(String.format("[%d] %s", trialCnt,
                eyeGesturesCmdDetail.get(currentGesture))));

        mClassifier = new Classifier2D(participantCode, getExternalFilesDir(null),
                Classifier2D.BufferMethod.ROLLING_WINDOW, 5);
        tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        long time = System.currentTimeMillis();
        mRawDataFile = new File(getExternalFilesDir(null), String.format("%s_%d_%06d_%s",
                participantCode, repeatTimes, time % 1000000, RAW_DATA_FILE));

        if(!mRawDataFile.exists()){
            try {
                mRawDataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else{
            try {
                BufferedReader br = new BufferedReader((new FileReader(mRawDataFile)));
                String textFromFile = "";
                String line = "";
                while ((line = br.readLine()) != null) {
                    textFromFile += line.toString();
                    textFromFile += "\n";
                }
                br.close();

                Log.i("onCreate", "content from the previous file: \n" + textFromFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            mRawDataWriter = new PrintWriter( new FileOutputStream(mRawDataFile), true);

            mRawDataWriter.write("task_gesture,left_eye,right_eye,time_stamp,real_trial_num,trial_cnt\n");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try {
            mResultFile = new File(getExternalFilesDir(null),
                    String.format("%s_%06d_%s", participantCode, time % 1000000, EVALUATION_RESULT_FILE));
            if (!mResultFile.exists()) {
                mResultFile.createNewFile();
            }
            mResultWriter = new PrintWriter(new FileOutputStream(mResultFile), true);

            // Note: if the current session is redo, then trial num unchanged, but real trial num changed.
            mResultWriter.write("real_trial_num,trial_cnt,time_stamp,task_gesture,detected_gesture,result\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

//        Log.i("file dir: ", getApplicationContext().getFilesDir().toString());
        Log.i("file dir: ", getExternalFilesDir(null).toString());
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
        if (flag)
        {
            if (left > -0.1f && right > -0.1f)
            {
                // Normal
                if (chineseStr) {
                    mTvFace.post(new Runnable() {
                        @Override
                        public void run() {
                            mTvFace.setText("轮廓检测成功");
                            mTvFace.setBackgroundColor(Color.rgb(65, 173, 85));
                        }
                    });
                }
                else {
                    mTvFace.post(new Runnable() {
                        @Override
                        public void run() {
                            mTvFace.setText("Face detected.");
                            mTvFace.setBackgroundColor(Color.rgb(65, 173, 85));
                        }
                    });
                }
            }
            else
            {
                // Something wrong
                String info_prefix = "";
                if (chineseStr) {
                    info_prefix = String.format("轮廓检测不稳定 (%f, %f)", left, right);
                }
                else {
                    info_prefix = String.format("Face detected (%f, %f)", left, right);
                }
                final String strInfo = info_prefix;
                mTvFace.post(new Runnable() {
                    @Override
                    public void run() {
                        mTvFace.setText(strInfo);
                        mTvFace.setBackgroundColor(Color.rgb(216, 132, 43));
                    }
                });
            }

        }
        else
        {
            // Missing
            mTvFace.post(new Runnable() {
                @Override
                public void run() {
                    if (chineseStr) {
                        mTvFace.setText("轮廓检测失败");
                    }
                    else {
                        mTvFace.setText("Face detection failed.");
                    }
                    mTvFace.setBackgroundColor(Color.rgb(173, 65, 65));
                }
            });
        }
    }

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();
        View decorView = getWindow().getDecorView();
        // Hide both the navigation bar and the status bar.
        // SYSTEM_UI_FLAG_FULLSCREEN is only available on Android 4.1 and higher, but as
        // a general rule, you should design your app to hide the status bar whenever you
        // hide the navigation bar.
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN| View.SYSTEM_UI_FLAG_IMMERSIVE;
        decorView.setSystemUiVisibility(uiOptions);

        // Check the existence of models
        mHasLoadedModel = mClassifier.buildModels();
        if (mHasLoadedModel) {
            Toast.makeText(getApplicationContext(), "Models are loaded.", Toast.LENGTH_SHORT).show();
        }
        else {
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle("Warning: models missing")
                    .setMessage(String.format("%s: Models not found. Please check the ID.", participantCode))
                    .setPositiveButton("OK", new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setNegativeButton("Continue anyway", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            mTvDetail.setText("Models not found.");
                        }
                    })
                    .show();
        }

        startCameraSource();
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
        mResultWriter.append(mResultBuilder.toString());
        mResultWriter.flush();
        mResultWriter.close();

        mRawDataWriter.flush();
        mRawDataWriter.close();

        mClassifier.saveLogToFile(mRawDataFile.getName());
        if (chineseStr) {
            Toast.makeText(getApplicationContext(), "数据保存至:" + mRawDataFile.getName(), Toast.LENGTH_SHORT).show();
        }
        else {
            Toast.makeText(getApplicationContext(), "File saved to:" + mRawDataFile.getName(), Toast.LENGTH_SHORT).show();
        }

        countdown.cancel();
        if (gesture2length.containsKey(currentGesture))
            collectTimers[gesture2length.get(currentGesture)].cancel();

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
    public void onClick(View view) {
        long currTime = System.currentTimeMillis();
        long btnGapTime = currTime - mLastPressedTime;
        if (btnGapTime <= 500) {
            return;
        }
        mLastPressedTime = currTime;

        switch(view.getId()){
            case R.id.bt_evaluation_one_ctrl:
                clickOnMainBtn();
                break;
            case R.id.bt_redo:
                if (!mRedoAllowed) {
                    return;
                }

                mRedoAllowed = false;
                if (!mEvaluationTag) {
                    // Current collection finished, go to previous one.
                    if (trialCnt > 0) {
                        trialCnt--;
                        currentGesture = eyeGestures.get(trialCnt);
                        mTvDetail.setText(Html.fromHtml(String.format("[%d] %s", trialCnt,
                                eyeGesturesCmdDetail.get(currentGesture))));

                        countdown.cancel();
                        countdown.start();
                    }
                }
                else {
                    // Stop current one and resume the status.
                    mEvaluationTag = false;

                    mRawDataWriter.append(mRawDataBuilder.toString());
                    mRawDataBuilder = new StringBuilder();

                    // Clear the result
                    mResultBuilder = new StringBuilder();

                    realTrialCnt++;

                    if (chineseStr) {
                        mBtEvaluation.setText("开始");
                    }
                    else {
                        mBtEvaluation.setText("Start");
                    }
                    mBtEvaluation.setBackgroundColor(Color.rgb(20, 204, 54));

                    countdown.cancel();
                    countdown.start();

                    // stop  the silent counter as well (which was activated when data collection starts)
                    if (collectCounting) {
                        assert gesture2length.containsKey(currentGesture);
                        collectTimers[gesture2length.get(currentGesture)].cancel();
                    }
                }
                break;
            case R.id.bt_pause:
                if (!collectCounting) {
                    // only response when training is not ready
                    if (counterPaused) {
                        countdown.start();
                        mBtPause.setText("Pause the timer");
//                        mBtPause.setText("暂停计时器");
                        counterPaused = false;
                    }
                    else {
                        countdown.cancel();
                        mBtPause.setText("Resume the timer");
//                        mBtPause.setText("恢复计时器");
                        counterPaused = true;
                    }
                }
                else {
                    Toast.makeText(getApplicationContext(), "Pause button is not available now; please use the [Redo] button", Toast.LENGTH_SHORT).show();
//                    Toast.makeText(getApplicationContext(), "收集数据过程无法被暂停，请使用[重做]功能", Toast.LENGTH_SHORT).show();
                }
        }

    }

    private void clickOnMainBtn() {
        if (!initCounter) {
            initCounter = true;
            countdown.start();
            return ;
        }

        if(mHasLoadedModel && trialCnt < eyeGestures.size()){
            // Has the model.
            if (!mEvaluationTag) {
                actionStart();
            }
            else {
                actionEnd();
            }

            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
        }
    }

    private void actionStart() {
        // counter starts for this gesture;
        assert gesture2length.containsKey(currentGesture);
        collectTimers[gesture2length.get(currentGesture)].start();

        // Start evaluation.
        timeStarted = System.currentTimeMillis();
        mClassifier.clearBuffer();
        mPredictedGestures.clear();
        mCurrentError = 0;
        mEvaluationTag = true;
        mRedoAllowed = true;

        if (chineseStr) {
            mBtEvaluation.setText("结束");
        } else {
            mBtEvaluation.setText("Stop");
        }

        mBtEvaluation.setBackgroundColor(Color.rgb(219, 134, 43));
    }

    private void actionEnd() {
        String logTag = "actionEnd";
        // Old logic: ignore this 'allow next' for now.
        if (!mAllowNext) {
            // Not finished yet.
            Toast.makeText(getApplicationContext(), "Please try again.", Toast.LENGTH_SHORT).show();
        }
        else {
            // Stop evaluation.
            mEvaluationTag = false;

            int errorForThis = mErrorCounter.get(currentGesture);
            boolean isGood = true;
            long currTime = System.currentTimeMillis() - timeStarted;
            // Evaluate, calculate, and (decide if we need to redo (add more) this session)
            if (currentGesture.equals("sB")) {
                // If no errors, then it is successful.
                if (mCurrentError > 0) {
                    // add one more time. (current the constant is 0, so don't add any)
                    if (errorForThis < MAX_ERROR_REPEAT) {
                        eyeGestures.add(currentGesture);
                        mClassifier.logVerboseMsg(logTag, String.format("Add another sample for: %s, progress: %d/%d",
                                currentGesture, trialCnt, eyeGestures.size()));
                    }
                    // else: keep counting.
                    mErrorCounter.put(currentGesture, errorForThis + 1);
                    isGood = false;
                } else {
                    if (mPredictedGestures.size() == 0) {
                        // Add a special line to the file (or there will be no lines for this 'sB')
                        mResultBuilder.append(String.format("%d,%d,%d,%s,%s,%s\n", realTrialCnt, trialCnt,
                                -1, "sB", "sB", "T"));
                        mClassifier.logVerboseMsg(logTag,"Good! (no gestures detected) [sB]");
                    }
                    // else: 'sB' is detected, which is good and we need nothing else to do.
                    mCorrectCounter.put(currentGesture, mCorrectCounter.get(currentGesture) + 1);
                    isGood = true;
                }
            }
            else {
                // Other gesture: if there are errors OR no results at all, then fail.
                if (mPredictedGestures.size() == 0) {
                    // See if there are any waiting gestures.
                    String waitingGesture = mClassifier.flushResult();
                    if (waitingGesture.length() > 0) {
                        processGestures(waitingGesture, currTime);
                    }
                }

                // Judge now.
                if (mCurrentError > 0 || mPredictedGestures.size() == 0) {
                    if (mPredictedGestures.size() == 0) {
                        // Add a special line
                        mResultBuilder.append(String.format("%d,%d,%d,%s,%s,%s\n", realTrialCnt, trialCnt,
                                -1, currentGesture, "NA", "F"));
                    }
                    // else: error had been logged, no need to worry.
                    // add one more time (if needed)
                    if (errorForThis < MAX_ERROR_REPEAT) {
                        eyeGestures.add(currentGesture);
                        mClassifier.logVerboseMsg(logTag, String.format("Add another sample for: %s, progress: %d/%d",
                                currentGesture, trialCnt, eyeGestures.size()));
                    }
                    mErrorCounter.put(currentGesture, errorForThis + 1);
                    isGood = false;
                }
                else {
                    mCorrectCounter.put(currentGesture, mCorrectCounter.get(currentGesture) + 1);
                    isGood = true;
                }
            }

            if (isGood) {
                mClassifier.logVerboseMsg(logTag, String.format("Result of %s, trial #%d: Good", currentGesture, trialCnt));
            }
            else {
                mClassifier.logVerboseMsg(logTag, String.format("Result of %s, trial #%d: Wrong", currentGesture, trialCnt));
            }

            mRawDataWriter.append(mRawDataBuilder.toString());
            mRawDataBuilder = new StringBuilder();

            mResultWriter.append(mResultBuilder.toString());
            mResultBuilder = new StringBuilder();

            trialCnt++;
            realTrialCnt++;

            if(trialCnt < eyeGestures.size()){

                currentGesture = eyeGestures.get(trialCnt);
                mTvDetail.setText(Html.fromHtml(String.format("[%d] %s", trialCnt, eyeGesturesCmdDetail.get(currentGesture))));

                boolean duringBreak = false;
                // New code: change 10 -> gesture length (9)
                if (trialCnt > 0 && trialCnt % EYE_GESTURES.length == 0) {
                    duringBreak = true;

                    if (chineseStr) {
                        new AlertDialog.Builder(this)
                                .setIcon(android.R.drawable.ic_dialog_info)
                                .setTitle("休息一下")
                                .setMessage("您已经完成了一组实验，准备好进行下一组了吗？")
                                .setPositiveButton("好的", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                    countdown.start();
                                    }
                                })
                                .show();
                    }
                    else {
                        new AlertDialog.Builder(this)
                                .setIcon(android.R.drawable.ic_dialog_info)
                                .setTitle("Take a break")
                                .setMessage("You have finished one group of tasks. Ready for the next one?")
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        countdown.start();
                                    }
                                })
                                .show();
                    }
                }

                if (chineseStr) {
                    if (!duringBreak) {
                        countdown.start();
                    }
                    mBtEvaluation.setText("开始");
                } else {
                    if (!duringBreak) {
                        countdown.start();
                    }
                    mBtEvaluation.setText("Start");
                }
                mBtEvaluation.setBackgroundColor(Color.rgb(20, 204, 54));
            }
            else {
                if (chineseStr) {
                    mTvDetail.setText("完成！");
                    mBtEvaluation.setText("多谢您的帮助");
                }
                else {
                    mTvDetail.setText("Complete!");
                    mBtEvaluation.setText("Thank you!");
                }
                mBtEvaluation.setBackgroundColor(Color.rgb(20, 204, 54));

                for (String kk : EYE_GESTURES) {
                    int correct = mCorrectCounter.get(kk);
                    int error = mErrorCounter.get(kk);
                    Log.i("DEBUG", String.format("Result of %s (correct / total): %d/%d, %f", kk,
                            correct, correct + error, (float)correct / (correct + error)));
                }
            }
        }
    }
//
//    @Override
//    public void onCountDownEnd(CountDownAnimation animation) {
//        //make a beep sound to alert the user to start performing eyelid gesture
//        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
////        timeStarted = System.currentTimeMillis();
////        currentGesture = eyeGestures.get(trialCnt);
//    }

    @Override
    public void onBackPressed() {
        if (trialCnt == eyeGestures.size()) {
            finish();
        }
        else {
            if (chineseStr) {
                new AlertDialog.Builder(this)
                        .setTitle("退出训练过程")
                        .setMessage("训练过程未完成。您确认退出吗？")
                        .setPositiveButton("确认", new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }

                        })
                        .setNegativeButton("取消", null)
                        .show();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Closing Activity")
                        .setMessage("Are you sure you want to leave before training the new model?")
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }

                        })
                        .setNegativeButton("No", null)
                        .show();
            }
        }
    }

    /**
     * Quick helper function: whether this is a valid gesture.
     * @param gesture
     * @return
     */
    private boolean isValidGesture(String gesture) {
        for (String gg : EYE_GESTURES) {
            if (gg.equals(gesture)) {
                return true;
            }
        }
        return false;
    }

    private void processGestures(String gestureResult, long currTime) {
        if (currentGesture.equals("sB")) {
            // Special judge for single blink
            if (gestureResult.equals("sB")) {
                // Detect single blink in single blink session
                mPredictedGestures.add(gestureResult);
                mResultBuilder.append(String.format("%d,%d,%d,%s,%s,%s\n", realTrialCnt, trialCnt,
                        currTime, currentGesture, gestureResult, "T"));
                Log.i("DEBUG", "Good! [" + gestureResult + "]");
            }
            else if (isValidGesture(gestureResult)) {
                // Detect other gestures in single blink session
                mPredictedGestures.add(gestureResult);
                mResultBuilder.append(String.format("%d,%d,%d,%s,%s,%s\n", realTrialCnt, trialCnt,
                        currTime, currentGesture, gestureResult, "F"));
                mCurrentError++;
                Log.i("DEBUG", "Wrong: error no. #" + mPredictedGestures.size()
                        + ", " + gestureResult + ", " + currentGesture);
            }
            else {
                Log.i("VERBOSE", "Other gestures (ignored):" + gestureResult);
            }
        } else {
            // In other regular gestures, ignore all 'sB'. But log other gestures.
            if (isValidGesture(gestureResult) && !gestureResult.equals("sB")) {
                mPredictedGestures.add(gestureResult);
                if (gestureResult.equals(currentGesture)) {
                    // Success.
                    mResultBuilder.append(String.format("%d,%d,%d,%s,%s,%s\n", realTrialCnt, trialCnt,
                            currTime, currentGesture, gestureResult, "T"));
                    Log.i("DEBUG", "Good! [" + gestureResult + "]");
                }
                else {
                    mResultBuilder.append(String.format("%d,%d,%d,%s,%s,%s\n", realTrialCnt, trialCnt,
                            currTime, currentGesture, gestureResult, "F"));
                    mCurrentError++;
                    Log.i("DEBUG", "Wrong: error no. #" + mPredictedGestures.size()
                            + "," + gestureResult + ", " + currentGesture);
                }
            }
            else {
                Log.i("VERBOSE", "Other gestures (ignored):" + gestureResult);
            }
        }
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

            currLeftEyeScore = face.getIsLeftEyeOpenProbability();
            currRightEyeScore = face.getIsRightEyeOpenProbability();
            setFaceStatus(true, currLeftEyeScore, currRightEyeScore);

            final String segmentResult = mClassifier.classifySegment(currLeftEyeScore, currRightEyeScore);
            mTvCntDown.post(new Runnable() {
                @Override
                public void run() {
                    if (chineseStr) {
                        mTvCntDown.setText(EN2CN.get(segmentResult));
                    } else {
                        mTvCntDown.setText(segmentResult);
                    }
                }
            });

            if (mEvaluationTag) {
                long currTime = System.currentTimeMillis() - timeStarted;

                mRawDataBuilder.append(String.format("%s,%f,%f,%d,%d,%d\n", currentGesture,
                        currLeftEyeScore, currRightEyeScore, currTime, realTrialCnt, trialCnt));

                String resultRT = mClassifier.classify(currLeftEyeScore, currRightEyeScore);
                if (resultRT.length() > 0) {
                    String[] results = resultRT.split(",");
                    String gestureResult = results[1].trim();
//                    Log.d("DEBUG", resultRT);

                    processGestures(gestureResult, currTime);
                }
                else {
                    Log.i("DEBUG", "Empty result from the classifier");
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
}
