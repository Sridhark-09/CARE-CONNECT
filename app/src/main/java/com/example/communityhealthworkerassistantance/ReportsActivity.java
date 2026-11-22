package com.example.communityhealthworkerassistantance;

import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;

public class ReportsActivity extends BaseActivity {

    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);
        db = AppDatabase.getInstance(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadReport();
    }

    private void loadReport() {
        new AsyncTask<Void, Void, int[]>() {
            @Override
            protected int[] doInBackground(Void... voids) {
                List<PatientEntity> patients = db.patientDao().getAllPatients();
                List<ReminderEntity> reminders = db.reminderDao().getAll();

                int total = patients.size();
                int synced = 0, high = 0, due = 0, upcoming = 0;

                for (int i = 0; i < patients.size(); i++) {
                    PatientEntity p = patients.get(i);
                    if (p.synced) synced++;
                    String priority = PriorityUtils.classify(p.followUpDate);
                    if (priority.equals(PriorityUtils.HIGH)) high++;
                    else if (priority.equals(PriorityUtils.DUE_TODAY)) due++;
                    else if (priority.equals(PriorityUtils.UPCOMING)) upcoming++;
                }

                int remTotal = reminders.size();
                int remDone = 0;
                for (int i = 0; i < reminders.size(); i++) {
                    if (reminders.get(i).done) remDone++;
                }

                return new int[]{total, synced, total - synced, high, due, upcoming, remTotal, remDone};
            }

            @Override
            protected void onPostExecute(int[] r) {
                ((TextView) findViewById(R.id.reportTotal)).setText("Total patients: " + r[0]);
                ((TextView) findViewById(R.id.reportSynced)).setText("Synced to cloud: " + r[1]);
                ((TextView) findViewById(R.id.reportPending)).setText("Pending sync (offline): " + r[2]);
                ((TextView) findViewById(R.id.reportReminders)).setText(
                        "Reminders completed: " + r[7] + " of " + r[6]);

                int max = Math.max(r[0], 1);

                ((TextView) findViewById(R.id.labelHigh)).setText("High priority: " + r[3]);
                ProgressBar barHigh = findViewById(R.id.barHigh);
                barHigh.setMax(max);
                barHigh.setProgress(r[3]);

                ((TextView) findViewById(R.id.labelDue)).setText("Due today: " + r[4]);
                ProgressBar barDue = findViewById(R.id.barDue);
                barDue.setMax(max);
                barDue.setProgress(r[4]);

                ((TextView) findViewById(R.id.labelUpcoming)).setText("Upcoming: " + r[5]);
                ProgressBar barUpcoming = findViewById(R.id.barUpcoming);
                barUpcoming.setMax(max);
                barUpcoming.setProgress(r[5]);
            }
        }.execute();
    }
}