package djordje.jovic;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class DecideActivity extends AppCompatActivity {
    private String sessionName;
    private String sessionDate;
    private DatabaseHelper db;
    private Button btnOption1;
    private Button btnOption2;
    private Button btnOption3;
    private String selectedChoice;
    private String serverSessionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_decide);
        db = new DatabaseHelper(this);

        sessionName = getIntent().getStringExtra("session_name");
        sessionDate = getIntent().getStringExtra("session_date");
        serverSessionId = new DatabaseHelper(this).getServerIdForSession(sessionName, sessionDate);

        btnOption1 = findViewById(R.id.btnOption1);
        btnOption2 = findViewById(R.id.btnOption2);
        btnOption3 = findViewById(R.id.btnOption3);

        // Set up button click listeners
        btnOption1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedChoice = "yes";
                submitVote();
            }
        });

        btnOption2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedChoice = "no";
                submitVote();
            }
        });

        btnOption3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedChoice = "abstain";
                submitVote();
            }
        });
    }

    private void submitVote() {
        if (selectedChoice == null) {
            Toast.makeText(DecideActivity.this, "Please select an option", Toast.LENGTH_SHORT).show();
            return;
        }
        final String voteVal = selectedChoice;
        // send to server and update local
        new Thread(() -> {
            try {
                if (serverSessionId != null && !serverSessionId.isEmpty()) {
                    HttpHelper helper = new HttpHelper();
                    String jsonBody = "{\"sessionId\":\"" + serverSessionId + "\",\"vote\":\"" + voteVal + "\"}";
                    StringBuilder resp = new StringBuilder();
                    new HttpThread(helper, CommandEnum.POST_VOTE, jsonBody, resp).run();
                }
                db.addVote(sessionName, sessionDate, voteVal);
                runOnUiThread(() -> {
                    Toast.makeText(DecideActivity.this, "Vote recorded", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(DecideActivity.this, "Failed to send vote to server", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
