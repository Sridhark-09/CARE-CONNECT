package com.example.communityhealthworkerassistantance;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PatientListActivity extends BaseActivity {

    private ListView patientListView;
    private Button addPatientButton;
    private EditText searchInput;
    private TextView emptyText;
    private AppDatabase db;

    private final List<PatientEntity> allPatients = new ArrayList<>();
    private final List<PatientEntity> shownPatients = new ArrayList<>();
    private final ArrayList<String> displayList = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_list);

        db = AppDatabase.getInstance(this);

        patientListView = findViewById(R.id.patientListView);
        addPatientButton = findViewById(R.id.addPatientButton);
        searchInput = findViewById(R.id.searchInput);
        emptyText = findViewById(R.id.emptyText);

        adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_list_item_1,
                displayList
        );
        patientListView.setAdapter(adapter);

        addPatientButton.setOnClickListener(v -> {
            Intent intent = new Intent(PatientListActivity.this, AddPatientActivity.class);
            startActivity(intent);
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        patientListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= shownPatients.size()) return;
            PatientEntity selected = shownPatients.get(position);
            Intent intent = new Intent(PatientListActivity.this, PatientDetailsActivity.class);
            intent.putExtra("localId", selected.localId);
            intent.putExtra("name", selected.name);
            intent.putExtra("age", selected.age);
            intent.putExtra("village", selected.village);
            intent.putExtra("followUpDate", selected.followUpDate);
            startActivity(intent);
        });

        patientListView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= shownPatients.size()) return true;
            confirmDelete(shownPatients.get(position));
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        SyncManager.syncPendingPatients(this, syncedCount -> {
            if (!isFinishing() && !isDestroyed()) {
                loadPatients();
            }
        });
        loadPatients();
    }

    private void loadPatients() {
        new AsyncTask<Void, Void, List<PatientEntity>>() {
            @Override
            protected List<PatientEntity> doInBackground(Void... voids) {
                return db.patientDao().getAllPatients();
            }

            @Override
            protected void onPostExecute(List<PatientEntity> patients) {
                allPatients.clear();
                allPatients.addAll(patients);
                applyFilter();
            }
        }.execute();
    }

    private void applyFilter() {
        String query = searchInput.getText().toString().trim().toLowerCase(Locale.getDefault());

        shownPatients.clear();
        displayList.clear();

        for (int i = 0; i < allPatients.size(); i++) {
            PatientEntity p = allPatients.get(i);

            String name = p.name == null ? "" : p.name.toLowerCase(Locale.getDefault());
            String village = p.village == null ? "" : p.village.toLowerCase(Locale.getDefault());

            if (query.isEmpty() || name.contains(query) || village.contains(query)) {
                shownPatients.add(p);

                String status = p.synced ? "SYNCED" : "OFFLINE";
                displayList.add(status + " - " + p.name + " - " + p.village);
            }
        }

        adapter.notifyDataSetChanged();

        if (shownPatients.isEmpty()) {
            if (query.isEmpty()) {
                emptyText.setText("No patients yet. Tap + ADD PATIENT to add one.");
            } else {
                emptyText.setText("No patient found for \"" + searchInput.getText().toString().trim() + "\"");
            }
            emptyText.setVisibility(View.VISIBLE);
        } else {
            emptyText.setVisibility(View.GONE);
        }
    }

    private void confirmDelete(PatientEntity patient) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete Patient");
        builder.setMessage("Remove " + patient.name + " from your list and the cloud?");
        builder.setPositiveButton("Delete", (dialog, which) -> deletePatient(patient));
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void deletePatient(PatientEntity patient) {

        if (patient.firestoreId != null) {
            FirebaseFirestore.getInstance()
                    .collection("patients")
                    .document(patient.firestoreId)
                    .delete();
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                db.patientDao().delete(patient);
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                loadPatients();
            }
        }.execute();
    }
}