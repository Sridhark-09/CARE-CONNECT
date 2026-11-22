package com.example.communityhealthworkerassistantance;

import android.app.AlertDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.List;

public class ReminderActivity extends BaseActivity {

    private AppDatabase db;
    private Spinner typeSpinner;
    private EditText patientInput, dateInput, noteInput;
    private ListView reminderListView;
    private List<ReminderEntity> current = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminder);

        db = AppDatabase.getInstance(this);

        typeSpinner = findViewById(R.id.typeSpinner);
        patientInput = findViewById(R.id.reminderPatientInput);
        dateInput = findViewById(R.id.reminderDateInput);
        noteInput = findViewById(R.id.reminderNoteInput);
        reminderListView = findViewById(R.id.reminderListView);
        Button addButton = findViewById(R.id.addReminderButton);

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Medicine", "Vaccination", "Follow-up"}
        );
        typeSpinner.setAdapter(typeAdapter);

        addButton.setOnClickListener(v -> addReminder());

        loadReminders();
    }

    private void addReminder() {
        String patient = patientInput.getText().toString().trim();
        String date = dateInput.getText().toString().trim();
        String note = noteInput.getText().toString().trim();
        String type = typeSpinner.getSelectedItem().toString();

        if (patient.isEmpty()) {
            patientInput.setError("Enter patient name");
            return;
        }

        if (PriorityUtils.classify(date).equals(PriorityUtils.UNKNOWN)) {
            dateInput.setError("Use dd-MM-yyyy");
            return;
        }

        ReminderEntity reminder = new ReminderEntity(patient, type, date, note);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                db.reminderDao().insert(reminder);
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                patientInput.setText("");
                dateInput.setText("");
                noteInput.setText("");
                loadReminders();
            }
        }.execute();
    }

    private void loadReminders() {
        new AsyncTask<Void, Void, List<ReminderEntity>>() {
            @Override
            protected List<ReminderEntity> doInBackground(Void... voids) {
                return db.reminderDao().getAll();
            }

            @Override
            protected void onPostExecute(List<ReminderEntity> reminders) {
                current = reminders;
                ArrayList<String> display = new ArrayList<>();

                for (int i = 0; i < reminders.size(); i++) {
                    ReminderEntity r = reminders.get(i);
                    String status;
                    if (r.done) {
                        status = "[DONE]";
                    } else {
                        status = "[" + PriorityUtils.label(PriorityUtils.classify(r.dueDate)) + "]";
                    }
                    String line = status + " " + r.type + " - " + r.patientName + " (" + r.dueDate + ")";
                    if (r.note != null && !r.note.isEmpty()) {
                        line = line + "\n" + r.note;
                    }
                    display.add(line);
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                        ReminderActivity.this,
                        android.R.layout.simple_list_item_1,
                        display
                );
                reminderListView.setAdapter(adapter);

                reminderListView.setOnItemClickListener((parent, view, position, id) -> {
                    ReminderEntity selected = current.get(position);
                    selected.done = !selected.done;
                    updateReminder(selected);
                });

                reminderListView.setOnItemLongClickListener((parent, view, position, id) -> {
                    ReminderEntity selected = current.get(position);
                    confirmDelete(selected);
                    return true;
                });
            }
        }.execute();
    }

    private void updateReminder(ReminderEntity reminder) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                db.reminderDao().update(reminder);
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                loadReminders();
            }
        }.execute();
    }

    private void confirmDelete(ReminderEntity reminder) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete reminder");
        builder.setMessage("Remove this " + reminder.type + " reminder for " + reminder.patientName + "?");
        builder.setPositiveButton("Delete", (dialog, which) -> deleteReminder(reminder));
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void deleteReminder(ReminderEntity reminder) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                db.reminderDao().delete(reminder);
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                loadReminders();
            }
        }.execute();
    }
}