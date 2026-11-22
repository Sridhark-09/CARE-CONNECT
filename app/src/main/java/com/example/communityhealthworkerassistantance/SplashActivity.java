package com.example.communityhealthworkerassistantance;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends BaseActivity {

    private static final int SPLASH_TIME = 2500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {

            Class<?> nextActivity;

            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                nextActivity = DashboardActivity.class;
            } else {
                nextActivity = LoginActivity.class;
            }

            Intent intent = new Intent(SplashActivity.this, nextActivity);
            startActivity(intent);
            finish();

        }, SPLASH_TIME);
    }
}