package com.example.communityhealthworkerassistantance;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.util.List;
import java.util.Locale;

public class LocationPickerActivity extends BaseActivity {

    private static final int LOCATION_PERMISSION_CODE = 800;

    private MapView map;
    private TextView statusText;
    private TextView attributionText;
    private Button styleButton;
    private int styleIndex;

    private Marker pin;
    private BitmapDrawable pinDrawable;
    private boolean hasPin = false;
    private double pickedLat = 0.0;
    private double pickedLng = 0.0;

    private FusedLocationProviderClient fusedClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MapTiles.configure(this);
        setContentView(R.layout.activity_location_picker);

        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        map = findViewById(R.id.pickerMap);
        statusText = findViewById(R.id.pickerStatus);
        attributionText = findViewById(R.id.pickerAttribution);
        styleButton = findViewById(R.id.pickerStyleButton);
        Button gpsButton = findViewById(R.id.pickerGpsButton);
        Button confirmButton = findViewById(R.id.pickerConfirmButton);

        pinDrawable = MapTiles.pin(this, Color.parseColor("#0F766E"));

        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.getController().setZoom(5.0);
        map.getController().setCenter(new GeoPoint(20.5937, 78.9629));

        MapEventsReceiver receiver = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                placePin(p);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        };
        map.getOverlays().add(0, new MapEventsOverlay(receiver));

        styleIndex = MapTiles.getSavedStyle(this);
        applyStyle(styleIndex);

        styleButton.setOnClickListener(v -> {
            styleIndex = MapTiles.nextStyle(styleIndex);
            MapTiles.saveStyle(this, styleIndex);
            applyStyle(styleIndex);
        });

        gpsButton.setOnClickListener(v -> useMyGps());

        confirmButton.setOnClickListener(v -> {
            if (!hasPin) {
                Toast.makeText(this, "Tap the map to place the pin first", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent result = new Intent();
            result.putExtra("lat", pickedLat);
            result.putExtra("lng", pickedLng);
            setResult(RESULT_OK, result);
            finish();
        });

        double startLat = getIntent().getDoubleExtra("lat", 0.0);
        double startLng = getIntent().getDoubleExtra("lng", 0.0);
        String village = getIntent().getStringExtra("village");

        if (startLat != 0.0 || startLng != 0.0) {
            GeoPoint start = new GeoPoint(startLat, startLng);
            placePin(start);
            map.getController().setZoom(17.0);
            map.getController().setCenter(start);
        } else if (village != null && !village.isEmpty()) {
            centerOnVillage(village);
        }
    }

    private void applyStyle(int index) {
        map.setTileSource(MapTiles.source(index));
        attributionText.setText(MapTiles.attribution(index));
        styleButton.setText("STYLE: " + MapTiles.STYLE_NAMES[index].toUpperCase(Locale.ROOT));
        map.invalidate();
    }

    private void centerOnVillage(String village) {
        statusText.setText("Finding " + village + "...");

        new AsyncTask<Void, Void, GeoPoint>() {
            @Override
            protected GeoPoint doInBackground(Void... voids) {
                if (!Geocoder.isPresent()) return null;
                try {
                    Geocoder geocoder = new Geocoder(LocationPickerActivity.this, new Locale("en", "IN"));
                    List<Address> results = geocoder.getFromLocationName(village, 1);
                    if (results != null && !results.isEmpty()) {
                        return new GeoPoint(results.get(0).getLatitude(), results.get(0).getLongitude());
                    }
                } catch (Exception e) {
                    // lookup failed, the worker can still pan the map by hand
                }
                return null;
            }

            @Override
            protected void onPostExecute(GeoPoint point) {
                if (isFinishing() || isDestroyed()) return;
                if (point != null) {
                    map.getController().setZoom(15.0);
                    map.getController().setCenter(point);
                    statusText.setText("Zoom in and tap the exact spot to place the pin.");
                } else {
                    statusText.setText("Could not find that place. Pan the map and tap the exact spot.");
                }
            }
        }.execute();
    }

    private void placePin(GeoPoint p) {
        if (pin == null) {
            pin = new Marker(map);
            pin.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            pin.setIcon(pinDrawable);
            pin.setOnMarkerClickListener((m, mapView) -> true);
            map.getOverlays().add(pin);
        }

        pin.setPosition(p);
        hasPin = true;
        pickedLat = p.getLatitude();
        pickedLng = p.getLongitude();

        statusText.setText(String.format(Locale.US,
                "Pin at %.5f, %.5f. Tap elsewhere to move it, then press CONFIRM.",
                pickedLat, pickedLng));

        map.invalidate();
    }

    private void useMyGps() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_CODE);
            return;
        }

        statusText.setText("Getting your GPS position...");

        fusedClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        new CancellationTokenSource().getToken())
                .addOnSuccessListener(location -> {
                    if (location == null) {
                        statusText.setText("No GPS fix yet. Try again, or tap the map by hand.");
                        return;
                    }
                    GeoPoint here = new GeoPoint(location.getLatitude(), location.getLongitude());
                    placePin(here);
                    map.getController().setZoom(17.0);
                    map.getController().animateTo(here);
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            useMyGps();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
    }
}