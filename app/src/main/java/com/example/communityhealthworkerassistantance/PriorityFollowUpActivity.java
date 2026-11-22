package com.example.communityhealthworkerassistantance;

import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class PriorityFollowUpActivity extends AppCompatActivity {

    private AppDatabase db;
    private String targetPriority;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_priority_followup);

        db = AppDatabase.getInstance(this);
        targetPriority = getIntent().getStringExtra("priority");

        TextView title = findViewById(R.id.priorityTitle);
        title.setText(PriorityUtils.label(targetPriority));

        loadFiltered();
    }

    private void loadFiltered() {
        ListView listView = findViewById(R.id.priorityListView);

        new AsyncTask<Void, Void, List<String>>() {
            @Override
            protected List<String> doInBackground(Void... voids) {
                List<PatientEntity> all = db.patientDao().getAllPatients();
                List<String> matches = new ArrayList<>();
                for (PatientEntity p : all) {
                    String priority = PriorityUtils.classify(p.followUpDate);
                    if (priority.equals(targetPriority)) {
                        matches.add(p.name + " \u2014 " + p.village + " (follow-up: " + p.followUpDate + ")");
                    }
                }
                return matches;
            }

            @Override
            protected void onPostExecute(List<String> matches) {
                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        PriorityFollowUpActivity.this,
                        android.R.layout.simple_list_item_1,
                        matches
                );
                listView.setAdapter(adapter);
            }
        }.execute();
    }
}