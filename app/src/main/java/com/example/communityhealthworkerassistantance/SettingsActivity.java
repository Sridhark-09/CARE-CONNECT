package com.example.communityhealthworkerassistantance;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

public class SettingsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Spinner languageSpinner = findViewById(R.id.languageSpinner);
        Button saveButton = findViewById(R.id.saveLanguageButton);

        ArrayAdapter<LanguageHelper.Lang> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                LanguageHelper.ALL
        );
        languageSpinner.setAdapter(adapter);
        languageSpinner.setSelection(LanguageHelper.indexOf(LocaleHelper.getSavedLanguage(this)));

        saveButton.setOnClickListener(v -> {
            LanguageHelper.Lang chosen = (LanguageHelper.Lang) languageSpinner.getSelectedItem();
            LocaleHelper.saveLanguage(this, chosen.code);

            Intent intent = new Intent(SettingsActivity.this, DashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
}