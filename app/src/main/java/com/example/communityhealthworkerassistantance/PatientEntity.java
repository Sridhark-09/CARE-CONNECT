package com.example.communityhealthworkerassistantance;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "patients_offline")
public class PatientEntity {

    @PrimaryKey(autoGenerate = true)
    public int localId;

    public String name;
    public int age;
    public String village;
    public String followUpDate;
    public boolean synced;
    public String photoPath;

    public double latitude;
    public double longitude;

    // id of the matching document in Firestore (null until uploaded)
    public String firestoreId;

    public PatientEntity(@NonNull String name, int age, String village, String followUpDate, boolean synced) {
        this.name = name;
        this.age = age;
        this.village = village;
        this.followUpDate = followUpDate;
        this.synced = synced;
        this.photoPath = null;
        this.latitude = 0.0;
        this.longitude = 0.0;
        this.firestoreId = null;
    }
}