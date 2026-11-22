package com.example.communityhealthworkerassistantance;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.util.List;
import java.util.Locale;

public class AddPatientActivity extends BaseActivity {

    private static final int LOCATION_PERMISSION_CODE = 300;
    private static final int AUDIO_PERMISSION_CODE = 400;
    private static final int PICK_LOCATION_CODE = 1200;

    private EditText nameInput, ageInput, villageInput, followUpInput;
    private Button saveButton, micButton, pickLocationButton;
    private TextView locationStatusText;
    private AppDatabase db;
    private FusedLocationProviderClient locationClient;

    private double lastLat = 0.0;
    private double lastLng = 0.0;

    private boolean hasPicked = false;
    private double pickedLat = 0.0;
    private double pickedLng = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_patient);

        db = AppDatabase.getInstance(this);
        locationClient = LocationServices.getFusedLocationProviderClient(this);

        nameInput = findViewById(R.id.nameInput);
        ageInput = findViewById(R.id.ageInput);
        villageInput = findViewById(R.id.villageInput);
        followUpInput = findViewById(R.id.followUpInput);
        saveButton = findViewById(R.id.saveButton);
        micButton = findViewById(R.id.micButton);
        pickLocationButton = findViewById(R.id.pickLocationButton);
        locationStatusText = findViewById(R.id.locationStatusText);

        requestLocationIfNeeded();

        micButton.setOnClickListener(v -> requestAudioPermissionAndListen());

        pickLocationButton.setOnClickListener(v -> {
            Intent intent = new Intent(AddPatientActivity.this, LocationPickerActivity.class);
            intent.putExtra("village", villageInput.getText().toString().trim());
            if (hasPicked) {
                intent.putExtra("lat", pickedLat);
                intent.putExtra("lng", pickedLng);
            }
            startActivityForResult(intent, PICK_LOCATION_CODE);
        });

        saveButton.setOnClickListener(v -> {

            String name = nameInput.getText().toString().trim();
            String ageText = ageInput.getText().toString().trim();
            String village = villageInput.getText().toString().trim();
            String followUp = followUpInput.getText().toString().trim();

            if (name.isEmpty()) {
                nameInput.setError("Enter name");
                return;
            }

            if (ageText.isEmpty()) {
                ageInput.setError("Enter age");
                return;
            }

            int age = Integer.parseInt(ageText);

            final boolean usePicked = hasPicked;
            final double pLat = pickedLat;
            final double pLng = pickedLng;

            saveButton.setEnabled(false);

            new AsyncTask<Void, Void, double[]>() {
                @Override
                protected double[] doInBackground(Void... voids) {
                    double[] loc;
                    if (usePicked) {
                        loc = new double[]{pLat, pLng, 2};
                    } else {
                        loc = resolveLocation(village);
                    }

                    PatientEntity entity = new PatientEntity(name, age, village, followUp, false);
                    entity.latitude = loc[0];
                    entity.longitude = loc[1];
                    db.patientDao().insert(entity);

                    return loc;
                }

                @Override
                protected void onPostExecute(double[] loc) {
                    String message;
                    if (loc[2] == 2) {
                        message = "Patient saved (exact location picked on map)";
                    } else if (loc[2] == 1) {
                        message = "Patient saved (approximate location from village name)";
                    } else if (loc[0] != 0.0 || loc[1] != 0.0) {
                        message = "Patient saved (location from phone GPS)";
                    } else {
                        message = "Patient saved (no location - use PICK EXACT LOCATION)";
                    }

                    Toast.makeText(AddPatientActivity.this, message, Toast.LENGTH_LONG).show();

                    SyncManager.syncPendingPatients(AddPatientActivity.this, syncedCount -> {
                        // sync runs quietly in the background
                    });

                    clearForm();
                    saveButton.setEnabled(true);
                }
            }.execute();
        });
    }

    // Returns {latitude, longitude, source} where source 1 = village lookup, 0 = GPS or none
    private double[] resolveLocation(String village) {
        if (village != null && !village.isEmpty() && Geocoder.isPresent()) {
            try {
                Geocoder geocoder = new Geocoder(this, new Locale("en", "IN"));
                List<Address> results = geocoder.getFromLocationName(village, 1);
                if (results != null && !results.isEmpty()) {
                    Address first = results.get(0);
                    return new double[]{first.getLatitude(), first.getLongitude(), 1};
                }
            } catch (Exception e) {
                // lookup failed (no internet or service unavailable), fall back to GPS
            }
        }
        return new double[]{lastLat, lastLng, 0};
    }

    private void requestAudioPermissionAndListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION_CODE);
        } else {
            VoiceInputHelper.startListening(this, LocaleHelper.getSavedLanguage(this));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AUDIO_PERMISSION_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            VoiceInputHelper.startListening(this, LocaleHelper.getSavedLanguage(this));
        } else if (requestCode == LOCATION_PERMISSION_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchLocation();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_LOCATION_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                pickedLat = data.getDoubleExtra("lat", 0.0);
                pickedLng = data.getDoubleExtra("lng", 0.0);
                hasPicked = true;
                locationStatusText.setText(String.format(Locale.US,
                        "Location set on map: %.5f, %.5f", pickedLat, pickedLng));
            }
            return;
        }

        String spokenText = VoiceInputHelper.extractResult(requestCode, resultCode, data);
        if (spokenText != null) {
            villageInput.setText(spokenText);
        }
    }

    private void requestLocationIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_CODE);
        } else {
            fetchLocation();
        }
    }

    private void fetchLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        new CancellationTokenSource().getToken())
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        lastLat = location.getLatitude();
                        lastLng = location.getLongitude();
                    }
                });
    }

    private void clearForm() {
        nameInput.setText("");
        ageInput.setText("");
        villageInput.setText("");
        followUpInput.setText("");
        hasPicked = false;
        pickedLat = 0.0;
        pickedLng = 0.0;
        locationStatusText.setText("Location not set. The village name or phone GPS will be used.");
        nameInput.requestFocus();
    }
}