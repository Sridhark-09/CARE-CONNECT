package com.example.communityhealthworkerassistantance;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "reminders")
public class ReminderEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String patientName;
    public String type;
    public String dueDate;
    public String note;
    public boolean done;

    public ReminderEntity(@NonNull String patientName, String type, String dueDate, String note) {
        this.patientName = patientName;
        this.type = type;
        this.dueDate = dueDate;
        this.note = note;
        this.done = false;
    }
}