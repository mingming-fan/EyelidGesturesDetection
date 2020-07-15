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
import android.os.Environment;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import eyeinteraction.ui.camera.CameraSourcePreview;
import eyeinteraction.ui.camera.GraphicOverlay;

import eyeinteraction.MSD;

import android.view.View.OnKeyListener;
import android.view.View;
import android.view.KeyEvent;
import android.widget.Toast;

/**
 * Activity for the face tracker app.  This app detects faces with the rear facing camera, and draws
 * overlay graphics to indicate the position, size, and ID of each face.
 */
public final class EvaluationThreeMethodOneActivity extends AppCompatActivity implements View.OnClickListener, View.OnSystemUiVisibilityChangeListener {
    private final String TAG = EvaluationThreeMethodOneActivity.this.getClass().getSimpleName();

    String participantCode;
//    String glassCode;
    int numOfPhrases = 10;  // number of phrases to type
    int targetPhraseIndex = 0;

    Button mBtConfirm;
    TextView mTvPresented;
    EditText mEtTranscribed;
    String presentedText = "";
    String transcribedText = "";
    ImageButton mBtOverview;
    RelativeLayout layer1;
    RelativeLayout layer2;
    RelativeLayout layer3;
    RelativeLayout layer4;
    RelativeLayout currentLayer;
    ImageView img_layer1;
    ImageView img_layer2;
    ImageView img_layer21;
    ImageView img_layer22;

    String[] phrases;
//    File textEntryFile = null;
//    PrintWriter pwOutput;
    long startTime = 0;
    int timeForPhrase;
    BufferedWriter sd2;
    File f2;
    String sd2Leader;
    final String SD2_HEADER = "Participant, Condition, Presented_char,Transcribed_char,Time(s),MSD,"
            + "Entry_speed(wpm),Error_rate(%)\n";
    String condition = "normal";

    View decorView;

    File mDetailFile;
    PrintWriter mDetailWriter;
    StringBuilder mDetailBuilder = new StringBuilder();

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
        setContentView(R.layout.activity_evaluation_three_method_one);

        Bundle b = getIntent().getExtras();
        participantCode = b.getString("participantCode");
//        glassCode = b.getString("glassCode");
        String repeats = b.getString("numPhrases");
        numOfPhrases = Integer.parseInt(repeats);
        String testOrPractice = b.getString("TestOrPractice");

        layer1 = (RelativeLayout)findViewById(R.id.layer1);
        layer2 = (RelativeLayout)findViewById(R.id.layer2);
        layer3 = (RelativeLayout)findViewById(R.id.layer3);
        layer4 = (RelativeLayout)findViewById(R.id.layer4);
        currentLayer = layer1;
        layer1.setVisibility(View.VISIBLE);
        layer2.setVisibility(View.INVISIBLE);
        layer3.setVisibility(View.INVISIBLE);
        layer4.setVisibility(View.INVISIBLE);

        img_layer1 = (ImageView)findViewById(R.id.img_layer1);
        img_layer2 = (ImageView)findViewById(R.id.img_layer2);
        img_layer1.setOnClickListener(this);
        img_layer2.setOnClickListener(this);
        img_layer21 = (ImageView)findViewById(R.id.img_layer21);
        img_layer22 = (ImageView)findViewById(R.id.img_layer22);
        img_layer21.setOnClickListener(this);
        img_layer22.setOnClickListener(this);

        mBtConfirm = (Button)findViewById(R.id.button_textentry);
        mBtConfirm.setOnClickListener(this);
        mBtOverview = (ImageButton)findViewById(R.id.bt_overview);
        mBtOverview.setOnClickListener(this);
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
                           // InputMethodManager.HIDE_NOT_ALWAYS);
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
//        String baseFilename = participantCode + "_" + glassCode + "_3_"  + condition +"_" + numOfPhrases + "_" + testOrPractice + "_" + System.currentTimeMillis();
        String baseFilename = participantCode + "_eval_3_"  + condition +"_" + numOfPhrases + "_" + testOrPractice + "_" + System.currentTimeMillis();

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
//                transcribedText = mEtTranscribed.getText().toString();
//                pwOutput.println(presentedText + "," + transcribedText);
//                pwOutput.flush();
//                targetPhraseIndex ++;
//                //Log.i(TAG, "target phrase index: " + targetPhraseIndex);
//                if(targetPhraseIndex < numOfPhrases) {
//                    preparePhrase();
//                }
//                else {
//                    pwOutput.flush();
//                    pwOutput.close();
//                    this.finish();
//                }
                break;
            case R.id.bt_overview:
                if(currentLayer == layer1){
                    layer1.setVisibility(View.INVISIBLE);
                    layer2.setVisibility(View.VISIBLE);
                    layer3.setVisibility(View.INVISIBLE);
                    layer4.setVisibility(View.INVISIBLE);
                    currentLayer = layer2;
                }
                else if(currentLayer == layer3){
                    layer1.setVisibility(View.INVISIBLE);
                    layer2.setVisibility(View.INVISIBLE);
                    layer3.setVisibility(View.INVISIBLE);
                    layer4.setVisibility(View.VISIBLE);
                    currentLayer = layer4;
                }
                Log.i(TAG, "overview button pressed...");
                mDetailBuilder.append(String.format("%d,%s,%s,%d\n", targetPhraseIndex, "Overview",
                        "[" + mEtTranscribed.getText().toString().trim() + "]", System.currentTimeMillis() - startTime));
                break;
            case R.id.img_layer1:
                Log.i(TAG, "image layer 1 pressed...");
                break;
            case R.id.img_layer2:
                Log.i(TAG, "image layer 2 pressed (InputView) ...");
                layer1.setVisibility(View.INVISIBLE);
                layer2.setVisibility(View.INVISIBLE);
                layer3.setVisibility(View.VISIBLE);
                layer4.setVisibility(View.INVISIBLE);
                currentLayer = layer3;
                mDetailBuilder.append(String.format("%d,%s,%s,%d\n", targetPhraseIndex, "InputView",
                        "[" + mEtTranscribed.getText().toString().trim() + "]", System.currentTimeMillis() - startTime));

                break;
            case R.id.img_layer21:
                Log.i(TAG, "image layer 21 pressed...");
                break;
            case R.id.img_layer22:
                layer1.setVisibility(View.VISIBLE);
                layer2.setVisibility(View.INVISIBLE);
                layer3.setVisibility(View.INVISIBLE);
                layer4.setVisibility(View.INVISIBLE);
                currentLayer = layer1;
                Log.i(TAG, "image layer 22 (TargetView) pressed...");
                mDetailBuilder.append(String.format("%d,%s,%s,%d\n", targetPhraseIndex, "TargetView",
                        "[" + mEtTranscribed.getText().toString().trim() + "]", System.currentTimeMillis() - startTime));

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
            // task is completed
            // this.finish();
            Toast.makeText(getApplicationContext(), "Task completed!", Toast.LENGTH_LONG).show();
        }

    }

    /**
     * .
     */
    @Override
    protected void onResume() {
        super.onResume();
//        View decorView = getWindow().getDecorView();
//        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE;
//        decorView.setSystemUiVisibility(uiOptions);
        setupMainWindowDisplayMode();
    }

    /**
     * Stops
     */
    @Override
    protected void onPause() {
        super.onPause();
    }

    /**
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    @Override
    public void onSystemUiVisibilityChange(int i) {
        setupMainWindowDisplayMode();
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
