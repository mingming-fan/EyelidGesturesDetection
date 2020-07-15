package eyeinteraction;


import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import eyeinteraction.ui.camera.CameraSourcePreview;
import eyeinteraction.ui.camera.GraphicOverlay;
import eyeinteraction.utils.Classifier2D;

public class EvaluationTwoiBlinkActivity extends AppCompatActivity implements GestureDetector.OnGestureListener,
        GestureDetector.OnDoubleTapListener, TabLayout.OnTabSelectedListener,View.OnSystemUiVisibilityChangeListener{
    private final boolean chineseStr = false;

    private final String TAG = EvaluationTwoiBlinkActivity.this.getClass().getSimpleName();

    private CameraSource mCameraSource = null;
    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;
    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    View decorView;

    private Classifier2D mClassifier;
    private String mGesture;

//    private final String[] AppNames = {"软件 1", "软件 2", "软件 3"};
    private final String[] AppNames = {"APP 1", "APP 2", "APP 3"};

    private final int[] resourceIds = {R.drawable.item1, R.drawable.item2, R.drawable.item3};
    private final int[] appColors = {Color.GREEN, Color.YELLOW, Color.BLUE};
    private final int NumItems = 12;  //12 items in total
    private Item[] items = new Item[NumItems];
//    private final String[] tabTitles = new String[]{
//            "标签 1",
//            "标签 2",
//            "标签 3"
//    }; //three tabs' titles
    private final String[] tabTitles = new String[]{
            "Tab 1",
            "Tab 2",
            "Tab 3"
    }; //three tabs' titles

//    private final String[] itemTitles = new String[]{
//            "按钮 1",
//            "按钮 2",
//            "按钮 3",
//            "按钮 4"
//    };
    public static final int NumItemsPerTab = 4;

    // App/Tab/Item starts from 1.
    private int currentAppID = 1;
    private int currentItemID = 1;
    private int currentTabID = 1;

    private ClickableViewPager mViewPager;
    private MyPagerAdapter mAdapter;
    private TabLayout mTablayout;
    private TextView mTitle;
    private GestureDetectorCompat mDetector;
    private TextView mCommand;

    String participantCode;

    int targetAppID = 1;
    int targetTabID = 1;
    int targetItemID = 1;

    int numOfTasks;
    int currentTaskID = 0;

    boolean modelReady = false;

    Random r = new Random();

    private TextToSpeech mTTS;

    File resultsFile;
    PrintWriter resultsWriter;
    long startTime = 0;
    StringBuilder resultBuilder = new StringBuilder();

    File mDetailFile;
    PrintWriter mDetailWriter;
    StringBuilder mDetailBuilder = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_evaluation_two_iblink);

        Bundle b = getIntent().getExtras();
        participantCode = b.getString("participantCode");
        String repeats = b.getString("numTargets");
        numOfTasks = Integer.parseInt(repeats);
        String testOrPractice = b.getString("TestOrPractice");

        Toolbar mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        mTitle = (TextView) mToolbar.findViewById(R.id.toolbar_title);
        mTitle.setTextColor(appColors[currentAppID - 1]);
        //set Activity title to empty to hide it
        getSupportActionBar().setTitle("");

        mViewPager = (ClickableViewPager) findViewById(R.id.viewpager);
        mAdapter = new MyPagerAdapter(getSupportFragmentManager());

        for(int j = 0; j < NumItems; j++){
            Item mItem = new Item(j, resourceIds[currentAppID - 1]);
            items[j] = mItem;
        }

        for(int j = 0; j < tabTitles.length; j++){
            PageFragment fragment = new PageFragment();
            fragment.setParams(this, items, j);
            mAdapter.addFragment(fragment, tabTitles[j]);
        }
        mViewPager.setAdapter(mAdapter);

        mTablayout = (TabLayout) findViewById(R.id.tab_layout);
        mTablayout.setupWithViewPager(mViewPager);
        mTablayout.addOnTabSelectedListener(this);

        mDetector = new GestureDetectorCompat(this,this);

        mViewPager.setOnTouchListener(new View.OnTouchListener(){
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                //Log.i(TAG, "onTOuch in ViewPager");
                repeatCurrentTask();
                return false;
            }});

        mCommand = (TextView)findViewById(R.id.textview_target);

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

        //set the current item selected
        int currIndex = codeToIndex(currentTabID, currentItemID);
        setSelectedIndex(currIndex);

        MyPagerAdapter myAdapter = (MyPagerAdapter)mViewPager.getAdapter();
        for(PageFragment pf :  myAdapter.getPageFragments()){
            pf.getAdapter().notifyDataSetChanged();
        }

        mClassifier = new Classifier2D(participantCode, getExternalFilesDir(null),
                Classifier2D.BufferMethod.ROLLING_WINDOW, 5);

        String filePrefix = String.format("%s_eval_2_iBlink_%d_%s_%06d", participantCode, numOfTasks, testOrPractice, System.currentTimeMillis() % 1000000);
        String resultFileName = filePrefix + "_result.csv";
        resultsFile = new File(getExternalFilesDir(null), resultFileName);
        if(!resultsFile.exists()){
            try {
                resultsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            resultsWriter = new PrintWriter( new FileOutputStream(resultsFile));
            resultsWriter.write("task_id,target_app,target_tab,target_item,curr_app,curr_tab,curr_item,gesture_time_list\n");
            resultsWriter.flush();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        String detailName = filePrefix + "_detail.csv";
        mDetailFile = new File(getExternalFilesDir(null), detailName);
        if (!mDetailFile.exists()) {
            try {
                mDetailFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            mDetailWriter = new PrintWriter(new FileOutputStream(mDetailFile));
            mDetailWriter.append("task_id,left_eye,right_eye,predicted_gesture,time_stamp\n");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void setSelectedIndex (int indexID){
        for (int j = 0; j < NumItems; j++) {
            if (j != indexID)
                items[j].setIsSelected(false);
            else
                items[j].setIsSelected(true);
        }
        Log.i("Select", "On Index:" + indexID);
    }

    /**
     * Code to index
     * @param tabID tabID (1,2,3)
     * @param itemID itemID (1,2,3,4)
     * @return index (0~11) used by UI process
     */
    private int codeToIndex (int tabID, int itemID) {
        Log.i("Before Select", "tab/item:" + tabID + "," + itemID);
        return (tabID - 1) * NumItemsPerTab + itemID - 1;
    }

    /**
     * Index to item code.
     * @param index (0~11) used by UI process
     * @return item id (1,2,3,4)
     */
    private int indexToItem (int index) {
        return index % NumItemsPerTab + 1;
    }

    public void distributeNewTask(){
        // App: 0~2 -> 1~3. Tab: 0~2 -> 1~3. Item: 0~3 -> 1~4.
        int randomtargetAppID = r.nextInt(AppNames.length);
        int randomtargetTabID = r.nextInt(tabTitles.length);
        int randomtargetItemID = r.nextInt(NumItemsPerTab);

        targetAppID = randomtargetAppID + 1;
        targetTabID = randomtargetTabID + 1;
        targetItemID = randomtargetItemID + 1;

        resultBuilder.append(currentTaskID + "," + targetAppID + "," + targetTabID + "," + targetItemID + "," + currentAppID + "," + currentTabID + "," + currentItemID + ",");

        String target = "App: " + targetAppID + ", tab: " + targetTabID + ", item: " + targetItemID;
        if (chineseStr) {
            target = "软件: " + targetAppID + ", 标签: " + targetTabID + ", 按钮: " + targetItemID;
        }

        // TODO: uncomment after shooting the cover image.
        mCommand.setText("   " + target);
        mTTS.speak(target, TextToSpeech.QUEUE_FLUSH, null);
        startTime = System.currentTimeMillis();
    }

    public void repeatCurrentTask(){
        String target = "App: " + targetAppID + ", tab: " + targetTabID + ", item: " + targetItemID;
        if (chineseStr) {
            target = "软件: " + targetAppID + ", 标签: " + targetTabID + ", 按钮: " + targetItemID;
        }
        mTTS.speak(target, TextToSpeech.QUEUE_FLUSH, null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        if (this.mDetector.onTouchEvent(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onDoubleTap(MotionEvent motionEvent) {
        Log.i(TAG, "double tap");

        //click to change application
        currentAppID = (currentAppID + 1) % AppNames.length + 1;
        mTitle.setTextColor(appColors[currentAppID - 1]);
        mTitle.setText(AppNames[currentAppID - 1]);
        for(int j = 0; j < NumItems; j++){
            items[j].setImageResource(resourceIds[currentAppID - 1]);
            items[j].setIsSelected(false);
        }
        MyPagerAdapter myAdapter = (MyPagerAdapter)mViewPager.getAdapter();
        for(PageFragment pf :  myAdapter.getPageFragments()){
            pf.getAdapter().notifyDataSetChanged();
        }
        TabLayout.Tab tab = mTablayout.getTabAt(0);
        tab.select();

        return true;
    }

    @Override
    public boolean onDown(MotionEvent event) {
        Log.d(TAG,"onDown: " + event.toString());
        return false;
    }

    @Override
    public boolean onFling(MotionEvent event1, MotionEvent event2,
                           float velocityX, float velocityY) {
        Log.d(TAG, "onFling: " + event1.toString() + event2.toString());
        return false;
    }

    @Override
    public void onLongPress(MotionEvent event) {
        Log.d(TAG, "onLongPress: " + event.toString());
    }

    @Override
    public boolean onScroll(MotionEvent event1, MotionEvent event2, float distanceX,
                            float distanceY) {
        Log.d(TAG, "onScroll: " + event1.toString() + event2.toString());
        return false;
    }

    @Override
    public void onShowPress(MotionEvent event) {
        Log.d(TAG, "onShowPress: " + event.toString());
    }

    @Override
    public boolean onSingleTapUp(MotionEvent event) {
        Log.d(TAG, "onSingleTapUp: " + event.toString());
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent event) {
        Log.d(TAG, "onDoubleTapEvent: " + event.toString());
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent event) {
        Log.d(TAG, "onSingleTapConfirmed: " + event.toString());
        return false;
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        currentTabID = tab.getPosition() + 1;
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {

    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {

    }

    public class MyPagerAdapter extends FragmentPagerAdapter {
        private List<PageFragment> mFragmentList = new ArrayList<>();
        private List<String> mFragmentTitleList = new ArrayList<>();

        public MyPagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        public void addFragment(PageFragment fragment, String title) {
            mFragmentList.add(fragment);
            mFragmentTitleList.add(title);
        }

        public List<PageFragment> getPageFragments(){
            return mFragmentList;
        }

        @Override
        public Fragment getItem(int position) {
            return mFragmentList.get(position);
        }

        @Override
        public int getCount() {
            return mFragmentList.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mFragmentTitleList.get(position);
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

        setupMainWindowDisplayMode();
        startCameraSource();

        modelReady = mClassifier.buildModels();
        mTTS = new TextToSpeech(getApplicationContext(),new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = 0;
                    if (chineseStr)
                        result = mTTS.setLanguage(Locale.SIMPLIFIED_CHINESE);
                    else
                        result = mTTS.setLanguage(Locale.ENGLISH);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "This Language is not supported");
                    }
                    distributeNewTask();
                } else {
                    Log.e("TTS", "Initialization Failed!");
                }
            }
        });
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
        if(mTTS != null){
            mTTS.stop();
            mTTS.shutdown();
        }
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
            if(!modelReady)
                return;

            float leftOpenProb = face.getIsLeftEyeOpenProbability();
            float rightOpenProb = face.getIsRightEyeOpenProbability();

            String content = mClassifier.classify(leftOpenProb, rightOpenProb);
            String[] splitContent = content.split(",");
            //Log.i(TAG, "gesture content: " + content);

            if(splitContent.length > 1) {
                mGesture = splitContent[1];
                long timeStamp = System.currentTimeMillis() - startTime;
                // Save to detail file: "task_id,left_eye,right_eye,predicted_gesture,time_stamp\n"
                mDetailBuilder.append(String.format("%d,%f,%f,%s,%d\n", currentTaskID, leftOpenProb, rightOpenProb, mGesture, timeStamp));

                switch(mGesture){
                    case "cLo":
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                currentItemID = Math.max(1, currentItemID - 1);
//                                currentItemID = currentItemID - 1 > currentTabID * NumItemsPerTab ? currentItemID - 1 : currentTabID * NumItemsPerTab;
                                int currIndex = codeToIndex(currentTabID, currentItemID);
                                setSelectedIndex(currIndex);
                                MyPagerAdapter myAdapter = (MyPagerAdapter)mViewPager.getAdapter();
                                for (PageFragment pf : myAdapter.getPageFragments()) {
                                    pf.getAdapter().notifyDataSetChanged();
                                }
                            }});
                        break;
                    case "cRo":
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                currentItemID = Math.min(NumItemsPerTab, currentItemID + 1);
//                                currentItemID = currentItemID + 1 < (currentTabID + 1) * NumItemsPerTab ? currentItemID + 1 : (currentTabID + 1) * NumItemsPerTab - 1;
                                int currIndex = codeToIndex(currentTabID, currentItemID);
                                setSelectedIndex(currIndex);
                                MyPagerAdapter myAdapter = (MyPagerAdapter)mViewPager.getAdapter();
                                for (PageFragment pf : myAdapter.getPageFragments()) {
                                    pf.getAdapter().notifyDataSetChanged();
                                }
                            }
                        });
                        break;
                    case "cBo":
                        break;
                    case "cL-o":
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {

                                currentTabID = Math.max(1, currentTabID - 1);
                                TabLayout.Tab tab = mTablayout.getTabAt(currentTabID - 1);
                                tab.select();
                                currentItemID = 1;
                                int currIndex = codeToIndex(currentTabID, currentItemID);
                                setSelectedIndex(currIndex);

                                MyPagerAdapter myAdapter = (MyPagerAdapter)mViewPager.getAdapter();
                                for(PageFragment pf :  myAdapter.getPageFragments()){
                                    pf.getAdapter().notifyDataSetChanged();
                                }
                            }
                        });
                        break;
                    case "cR-o":
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                currentTabID = Math.min(tabTitles.length, currentTabID + 1);
                                TabLayout.Tab tab = mTablayout.getTabAt(currentTabID - 1);
                                tab.select();
                                currentItemID = 1;
                                int currIndex = codeToIndex(currentTabID, currentItemID);

                                setSelectedIndex(currIndex);

                                MyPagerAdapter myAdapter = (MyPagerAdapter)mViewPager.getAdapter();
                                for(PageFragment pf :  myAdapter.getPageFragments()){
                                    pf.getAdapter().notifyDataSetChanged();
                                }
                            }
                        });
                        break;
                    case "cB-o":
                        break;
                    case "cB-LRo":
                        //click to change application
                        currentAppID = Math.max(1, currentAppID - 1);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mTitle.setTextColor(appColors[currentAppID - 1]);
                                mTitle.setText(AppNames[currentAppID - 1]);
                                for(int j = 0; j < NumItems; j++){
                                    items[j].setImageResource(resourceIds[currentAppID - 1]);
                                    items[j].setIsSelected(false);
                                }

                                currentTabID = 1;
                                TabLayout.Tab tab = mTablayout.getTabAt(currentTabID - 1);
                                tab.select();
                                currentItemID = 1;
                                int currIndex = codeToIndex(currentTabID, currentItemID);

                                setSelectedIndex(currIndex);

                                MyPagerAdapter myAdapter = (MyPagerAdapter)mViewPager.getAdapter();
                                for(PageFragment pf :  myAdapter.getPageFragments()){
                                    pf.getAdapter().notifyDataSetChanged();
                                }
                            }
                        });

                        break;
                    case "cB-RLo":
                        //click to change application
                        currentAppID = Math.min(AppNames.length, currentAppID + 1);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mTitle.setTextColor(appColors[currentAppID - 1]);
                                mTitle.setText(AppNames[currentAppID - 1]);
                                for(int j = 0; j < NumItems; j++){
                                    items[j].setImageResource(resourceIds[currentAppID - 1]);
                                    items[j].setIsSelected(false);
                                }

                                currentTabID = 1;
                                TabLayout.Tab tab = mTablayout.getTabAt(currentTabID - 1);
                                tab.select();
                                currentItemID = 1;
                                int currIndex = codeToIndex(currentTabID, currentItemID);
                                setSelectedIndex(currIndex);

                                MyPagerAdapter myAdapter = (MyPagerAdapter)mViewPager.getAdapter();
                                for(PageFragment pf :  myAdapter.getPageFragments()){
                                    pf.getAdapter().notifyDataSetChanged();
                                }
                            }
                        });
                        break;
                    case "dB":
                        break;
                    default:
                        mGesture = "";
                        break;
                }
                if (mGesture.length() > 0) {
                    resultBuilder.append(mGesture + "_" + timeStamp + ",");
                }

                //determine if the target is reached
                if (currentAppID == targetAppID && currentTabID == targetTabID && currentItemID == targetItemID) {

                    final String result = resultBuilder.toString();
                    resultsWriter.println(result);
                    resultsWriter.flush();
                    resultBuilder.setLength(0);
                    currentTaskID++;
                    if(currentTaskID >= numOfTasks){
                        if (chineseStr)
                            mTTS.speak("恭喜您完成全部实验！",TextToSpeech.QUEUE_FLUSH, null);
                        else
                            mTTS.speak("Congratulations!",TextToSpeech.QUEUE_FLUSH, null);

                        resultsWriter.close();
                        // Write detail
                        mDetailWriter.append(mDetailBuilder.toString());
                        mDetailWriter.flush();
                        mDetailWriter.close();

                        Toast.makeText(getApplicationContext(), "Congratulations!", Toast.LENGTH_SHORT).show();
                    }
                    else{
                        distributeNewTask();
                    }
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
        View decorView = setSystemUiVisibilityMode();
        decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                setSystemUiVisibilityMode(); // Needed to avoid exiting immersive_sticky when keyboard is displayed
            }
        });
    }

    private View setSystemUiVisibilityMode() {
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
}

