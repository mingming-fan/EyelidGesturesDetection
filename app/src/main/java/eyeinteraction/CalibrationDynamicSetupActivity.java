package eyeinteraction;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;

import weka.core.Check;


public class CalibrationDynamicSetupActivity extends AppCompatActivity implements View.OnClickListener{
    private final String TAG = CalibrationDynamicSetupActivity.this.getClass().getSimpleName();

    String[] participantCode = {"P99", "P01", "P02", "P03", "P04", "P05", "P06", "P07", "P08",
            "P09", "P10", "P11", "P12", "P13", "P14", "P15", "P16", "P17", "P18", "P19", "P20",
            "P21", "P22", "P23", "P24", "P25","P26", "P27", "P28", "P29","P30","P31","P32","P33","P34","P35","P36", "P37", "P38", "P39", "P40"};

    // if larger than 5, must be 5x.
    String[] numOfTrialsCode = {"5", "1", "2", "10", "15", "20"};

    Spinner spinParticipant, spinGlasses, spinTrials;
    CheckBox chb_pretrained;
    CheckBox chb_graph;
    boolean usePreTrainedModel = false;  //whether to load a pretrained model or not
    boolean showGraph = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration_dynamic_setup);

        spinParticipant = (Spinner)findViewById(R.id.paramPart);
//        spinGlasses = (Spinner)findViewById(R.id.paramGlasses);
        spinTrials = (Spinner)findViewById(R.id.paramTrials);

        chb_pretrained = (CheckBox)findViewById(R.id.checkBox_preTrainedModel);
        chb_pretrained.setChecked(false);
        chb_pretrained.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                    usePreTrainedModel = isChecked;
                    Log.i(TAG, "usePreTrainedModel: " + usePreTrainedModel);
            }
        });


        chb_graph = (CheckBox)findViewById(R.id.checkBox_graph);
        chb_graph.setChecked(false);
        chb_graph.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                showGraph = isChecked;
                Log.i(TAG, "usePreTrainedModel: " + usePreTrainedModel);
            }
        });

        // initialise spinner adapters
        ArrayAdapter<CharSequence> adapterPC = new ArrayAdapter<CharSequence>(this, R.layout
                .spinnerstylenew,
                participantCode);
        spinParticipant.setAdapter(adapterPC);

        ArrayAdapter<CharSequence> adapterT = new ArrayAdapter<CharSequence>(this, R.layout
                .spinnerstylenew, numOfTrialsCode);
        spinTrials.setAdapter(adapterT);

        Button bt_confirm = (Button) findViewById(R.id.bt_confirm);
        bt_confirm.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch(view.getId()){
            case R.id.bt_confirm:
                String part = participantCode[spinParticipant.getSelectedItemPosition()];
                String trial = numOfTrialsCode[spinTrials.getSelectedItemPosition()];

                Bundle b = new Bundle();
                b.putString("participantCode", part);
                b.putString("repeatsCode", trial);
                b.putBoolean("usePretrainedCode", usePreTrainedModel);
                b.putBoolean("showGraph", showGraph);

                Intent i = new Intent(getApplicationContext(), CalibrationDynamicActivity.class);
                i.putExtras(b);
                startActivity(i);
                finish();
                break;
            default:
                break;
        }
    }
}
