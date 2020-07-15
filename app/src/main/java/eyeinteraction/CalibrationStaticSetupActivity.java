package eyeinteraction;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;


public class CalibrationStaticSetupActivity extends AppCompatActivity implements View.OnClickListener{
    private final String TAG = CalibrationStaticSetupActivity.this.getClass().getSimpleName();

    String[] participantCode = {"P99", "P01", "P02", "P03", "P04", "P05", "P06", "P07", "P08",
            "P09", "P10", "P11", "P12", "P13", "P14", "P15", "P16", "P17", "P18", "P19", "P20",
            "P21", "P22", "P23", "P24", "P25","P26", "P27", "P28", "P29","P30","P31","P32","P33","P34","P35", "P36", "P37", "P38", "P39", "P40"};

    // Must be 100x. So we can cut into pieces.
    String[] numOfTrialsCode = {"200", "100", "400"};

    Spinner spinParticipant, spinGlasses, spinTrials;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration_static_setup);

        spinParticipant = (Spinner)findViewById(R.id.paramPart);
        spinTrials = (Spinner)findViewById(R.id.paramTrials);

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

                Intent i = new Intent(getApplicationContext(), CalibrationStaticActivity.class);
                i.putExtras(b);
                startActivity(i);
                finish();
                break;
            default:
                break;
        }
    }
}
