package com.example.communityhealthworkerassistantance;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PatientDetailsActivity extends AppCompatActivity {

    private static final int CAMERA_REQUEST_CODE = 100;
    private static final int CAMERA_PERMISSION_CODE = 200;

    private ImageView patientPhoto;
    private String currentPhotoPath;
    private AppDatabase db;
    private int patientLocalId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_details);

        db = AppDatabase.getInstance(this);

        TextView nameText = findViewById(R.id.detailsName);
        TextView ageText = findViewById(R.id.detailsAge);
        TextView villageText = findViewById(R.id.detailsVillage);
        TextView followUpText = findViewById(R.id.detailsFollowUp);
        patientPhoto = findViewById(R.id.patientPhoto);
        Button takePhotoButton = findViewById(R.id.takePhotoButton);

        String name = getIntent().getStringExtra("name");
        int age = getIntent().getIntExtra("age", 0);
        String village = getIntent().getStringExtra("village");
        String followUp = getIntent().getStringExtra("followUpDate");
        patientLocalId = getIntent().getIntExtra("localId", -1);

        nameText.setText(name);
        ageText.setText("Age: " + age);
        villageText.setText("Village: " + village);
        followUpText.setText("Follow-up: " + followUp);

        loadExistingPhoto();

        takePhotoButton.setOnClickListener(v -> checkCameraPermissionAndOpen());
    }

    private void checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else {
            openCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
        }
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        File photoFile = createImageFile();
        if (photoFile == null) return;

        Uri photoUri = FileProvider.getUriForFile(
                this,
                "com.example.communityhealthworkerassistantance.fileprovider",
                photoFile
        );

        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        startActivityForResult(takePictureIntent, CAMERA_REQUEST_CODE);
    }

    private File createImageFile() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File storageDir = new File(getExternalFilesDir(null), "patient_photos");
            if (!storageDir.exists()) storageDir.mkdirs();

            File imageFile = new File(storageDir, "PATIENT_" + timeStamp + ".jpg");
            currentPhotoPath = imageFile.getAbsolutePath();
            return imageFile;
        } catch (Exception e) {
            Toast.makeText(this, "Could not create file", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK) {

            displayPhoto(currentPhotoPath);

            if (patientLocalId != -1) {
                savePhotoPathToDb(currentPhotoPath);
            }
        }
    }

    private void displayPhoto(String path) {
        Bitmap bitmap = BitmapFactory.decodeFile(path);
        if (bitmap != null) {
            patientPhoto.setImageBitmap(bitmap);
            patientPhoto.setVisibility(ImageView.VISIBLE);
        }
    }

    private void savePhotoPathToDb(String path) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                List<PatientEntity> all = db.patientDao().getAllPatients();
                for (PatientEntity p : all) {
                    if (p.localId == patientLocalId) {
                        p.photoPath = path;
                        db.patientDao().update(p);
                        break;
                    }
                }
                return null;
            }
        }.execute();
    }

    private void loadExistingPhoto() {
        if (patientLocalId == -1) return;

        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                List<PatientEntity> all = db.patientDao().getAllPatients();
                for (PatientEntity p : all) {
                    if (p.localId == patientLocalId) {
                        return p.photoPath;
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(String path) {
                if (path != null) {
                    currentPhotoPath = path;
                    displayPhoto(path);
                }
            }
        }.execute();
    }
}