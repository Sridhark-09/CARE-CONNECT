package com.example.communityhealthworkerassistantance;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;

import java.util.List;

public class DashboardActivity extends BaseActivity {

    private static boolean notifiedThisSession = false;

    private Button patientsButton, aiButton, mapButton, reminderButton, reportsButton, settingsButton, logoutButton;
    private TextView patientCountText, highPriorityText, dueTodayText, upcomingText;
    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        db = AppDatabase.getInstance(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 600);
            }
        }

        patientsButton = findViewById(R.id.patientsButton);
        aiButton = findViewById(R.id.aiButton);
        mapButton = findViewById(R.id.mapButton);
        reminderButton = findViewById(R.id.reminderButton);
        reportsButton = findViewById(R.id.reportsButton);
        settingsButton = findViewById(R.id.settingsButton);
        logoutButton = findViewById(R.id.logoutButton);

        patientCountText = findViewById(R.id.patientCountText);
        highPriorityText = findViewById(R.id.highPriorityText);
        dueTodayText = findViewById(R.id.dueTodayText);
        upcomingText = findViewById(R.id.upcomingText);

        patientsButton.setOnClickListener(v -> openActivity(PatientListActivity.class));
        aiButton.setOnClickListener(v -> openActivity(AIChatActivity.class));
        mapButton.setOnClickListener(v -> openActivity(MapActivity.class));
        reminderButton.setOnClickListener(v -> openActivity(ReminderActivity.class));
        reportsButton.setOnClickListener(v -> openActivity(ReportsActivity.class));
        settingsButton.setOnClickListener(v -> openActivity(SettingsActivity.class));

        highPriorityText.setOnClickListener(v -> openPriorityScreen(PriorityUtils.HIGH));
        dueTodayText.setOnClickListener(v -> openPriorityScreen(PriorityUtils.DUE_TODAY));
        upcomingText.setOnClickListener(v -> openPriorityScreen(PriorityUtils.UPCOMING));

        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCounts();
    }

    private void loadCounts() {
        new AsyncTask<Void, Void, int[]>() {
            @Override
            protected int[] doInBackground(Void... voids) {
                List<PatientEntity> all = db.patientDao().getAllPatients();
                int high = 0, due = 0, upcoming = 0;
                for (PatientEntity p : all) {
                    String priority = PriorityUtils.classify(p.followUpDate);
                    if (priority.equals(PriorityUtils.HIGH)) high++;
                    else if (priority.equals(PriorityUtils.DUE_TODAY)) due++;
                    else if (priority.equals(PriorityUtils.UPCOMING)) upcoming++;
                }
                return new int[]{all.size(), high, due, upcoming};
            }

            @Override
            protected void onPostExecute(int[] counts) {
                patientCountText.setText(counts[0] + "\nPatients");
                highPriorityText.setText("\uD83D\uDD34 " + counts[1] + " High priority");
                dueTodayText.setText("\uD83D\uDFE0 " + counts[2] + " Due today");
                upcomingText.setText("\uD83D\uDFE2 " + counts[3] + " Upcoming");

                int needAttention = counts[1] + counts[2];
                if (needAttention > 0 && !notifiedThisSession) {
                    notifiedThisSession = true;
                    NotificationHelper.showFollowUpAlert(DashboardActivity.this, needAttention);
                }
            }
        }.execute();
    }

    private void openPriorityScreen(String priority) {
        Intent intent = new Intent(this, PriorityFollowUpActivity.class);
        intent.putExtra("priority", priority);
        startActivity(intent);
    }

    private void openActivity(Class<?> activityClass) {
        startActivity(new Intent(DashboardActivity.this, activityClass));
    }
}