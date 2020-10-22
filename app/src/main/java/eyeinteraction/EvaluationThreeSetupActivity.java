package eyeinteraction;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;


public class EvaluationThreeSetupActivity extends AppCompatActivity implements View.OnClickListener{

    private final String TAG = EvaluationThreeSetupActivity.this.getClass().getSimpleName();

    String[] participantCode = {"P99", "P01", "P02", "P03", "P04", "P05", "P06", "P07", "P08",
            "P09", "P10", "P11", "P12", "P13", "P14", "P15", "P16", "P17", "P18", "P19", "P20",
            "P21", "P22", "P23", "P24", "P25","P26", "P27", "P28", "P29","P30","P31","P32","P33","P34","P35","P36", "P37", "P38", "P39", "P40"};

    String[] glassesCode = {"N", "Y", "C"};

    String[] numOfPhrases = {"5", "1", "2", "10", "15", "20"};

    String[] numofMethods = {"normal_app_switching", "iWink_Switching", "iWink_Porous_Switching"};

    String[] practiceOrTests = { "practice","test"};

    Spinner spinParticipant, spinTrials, spinMethods,  spinTests;
//    Spinner spinGlasses;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_evaluation_three_setup);

        spinParticipant = (Spinner)findViewById(R.id.paramPart);
//        spinGlasses = (Spinner)findViewById(R.id.paramGlasses);
        spinTrials = (Spinner)findViewById(R.id.paramTrials);
        spinMethods = (Spinner)findViewById(R.id.paramMethods);
        spinTests = (Spinner)findViewById(R.id.paramTest);

        // initialise spinner adapters
        ArrayAdapter<CharSequence> adapterPC = new ArrayAdapter<CharSequence>(this, R.layout
                .spinnerstylenew,
                participantCode);
        spinParticipant.setAdapter(adapterPC);

//        ArrayAdapter<CharSequence> adapterG = new ArrayAdapter<CharSequence>(this, R.layout
//                .spinnerstylenew, glassesCode);
//        spinGlasses.setAdapter(adapterG);

        ArrayAdapter<CharSequence> adapterT = new ArrayAdapter<CharSequence>(this, R.layout
                .spinnerstylenew, numOfPhrases);
        spinTrials.setAdapter(adapterT);


        ArrayAdapter<CharSequence> adapterM = new ArrayAdapter<CharSequence>(this, R.layout
                .spinnerstylenew, numofMethods);
        spinMethods.setAdapter(adapterM);

        ArrayAdapter<CharSequence> adapterTest = new ArrayAdapter<CharSequence>(this, R.layout
                .spinnerstylenew, practiceOrTests);
        spinTests.setAdapter(adapterTest);

        Button bt_confirm = (Button) findViewById(R.id.bt_confirm);
        bt_confirm.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch(view.getId()){
            case R.id.bt_confirm:
                String part = participantCode[spinParticipant.getSelectedItemPosition()];
//                String glass = glassesCode[spinGlasses.getSelectedItemPosition()];
                String numPhrases = numOfPhrases[spinTrials.getSelectedItemPosition()];
                int method = spinMethods.getSelectedItemPosition();
                String testOrPractice = practiceOrTests[spinTests.getSelectedItemPosition()];

                Bundle b = new Bundle();
                b.putString("participantCode", part);
//                b.putString("glassCode", glass);
                b.putString("numPhrases", numPhrases);
                b.putString("TestOrPractice", testOrPractice);
                Intent i = null;
                switch(method){
                    case 0:
                        i = new Intent(getApplicationContext(), EvaluationThreeMethodOneActivity.class);
                        break;
                    case 1:
                        i = new Intent(getApplicationContext(), EvaluationThreeMethodTwoActivity.class);
                        break;
                    case 2:
                        i = new Intent(getApplicationContext(), EvaluationThreeMethodThreeActivity.class);
                        break;
                    default:
                        break;
                }

                i.putExtras(b);
                startActivity(i);
                finish();
                break;
            default:
                break;
        }
    }
}
