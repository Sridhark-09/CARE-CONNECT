package com.example.communityhealthworkerassistantance;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PatientDao {

    @Insert
    long insert(PatientEntity patient);

    @Update
    void update(PatientEntity patient);

    @Delete
    void delete(PatientEntity patient);

    @Query("SELECT * FROM patients_offline ORDER BY localId DESC")
    List<PatientEntity> getAllPatients();

    @Query("SELECT * FROM patients_offline WHERE synced = 0")
    List<PatientEntity> getUnsyncedPatients();

    @Query("SELECT COUNT(*) FROM patients_offline WHERE synced = 0")
    int getUnsyncedCount();

    @Query("SELECT * FROM patients_offline WHERE firestoreId = :id LIMIT 1")
    PatientEntity getByFirestoreId(String id);
}