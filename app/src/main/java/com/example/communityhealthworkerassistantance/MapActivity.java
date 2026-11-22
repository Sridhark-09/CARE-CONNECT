package com.example.communityhealthworkerassistantance;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.ScaleBarOverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MapActivity extends BaseActivity {

    private static final int LOCATION_PERMISSION_CODE = 700;

    private MapView map;
    private AppDatabase db;
    private TextView statusText;
    private TextView attributionText;
    private Button styleButton;
    private int styleIndex;

    private FusedLocationProviderClient fusedClient;
    private LocationCallback locationCallback;
    private Marker myMarker;
    private Polygon accuracyCircle;
    private Location myLocation;

    private boolean markersLoaded = false;
    private boolean initialViewDone = false;
    private boolean fittedToPatients = false;

    private final List<Marker> patientMarkers = new ArrayList<>();
    private BitmapDrawable pinRed, pinOrange, pinGreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MapTiles.configure(this);
        setContentView(R.layout.activity_map);

        db = AppDatabase.getInstance(this);
        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        map = findViewById(R.id.osmMap);
        statusText = findViewById(R.id.statusText);
        attributionText = findViewById(R.id.attributionText);
        styleButton = findViewById(R.id.styleButton);
        Button myLocationButton = findViewById(R.id.myLocationButton);
        Button fitAllButton = findViewById(R.id.fitAllButton);

        pinRed = MapTiles.pin(this, Color.parseColor("#DC2626"));
        pinOrange = MapTiles.pin(this, Color.parseColor("#F59E0B"));
        pinGreen = MapTiles.pin(this, Color.parseColor("#16A34A"));

        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.getController().setZoom(5.0);
        map.getController().setCenter(new GeoPoint(20.5937, 78.9629));

        float density = getResources().getDisplayMetrics().density;
        ScaleBarOverlay scaleBar = new ScaleBarOverlay(map);
        scaleBar.setAlignBottom(false);
        scaleBar.setScaleBarOffset(Math.round(8 * density), Math.round(52 * density));
        map.getOverlays().add(scaleBar);

        styleIndex = MapTiles.getSavedStyle(this);
        applyStyle(styleIndex);

        styleButton.setOnClickListener(v -> {
            styleIndex = MapTiles.nextStyle(styleIndex);
            MapTiles.saveStyle(this, styleIndex);
            applyStyle(styleIndex);
        });

        myLocationButton.setOnClickListener(v -> {
            if (myLocation != null) {
                map.getController().setZoom(17.0);
                map.getController().animateTo(
                        new GeoPoint(myLocation.getLatitude(), myLocation.getLongitude()));
            } else {
                Toast.makeText(this, "Waiting for a GPS fix...", Toast.LENGTH_SHORT).show();
            }
        });

        fitAllButton.setOnClickListener(v -> fitAll());

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc != null) {
                    updateMyLocation(loc);
                }
            }
        };

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_CODE);
        }

        loadMarkers();

        SyncManager.syncPendingPatients(this, syncedCount -> {
            if (isFinishing() || isDestroyed()) return;
            if (syncedCount > 0) {
                Toast.makeText(MapActivity.this,
                        "Synced " + syncedCount + " patient(s) with the cloud",
                        Toast.LENGTH_SHORT).show();
                loadMarkers();
            }
        });
    }

    private void applyStyle(int index) {
        map.setTileSource(MapTiles.source(index));
        attributionText.setText(MapTiles.attribution(index));
        styleButton.setText("STYLE: " + MapTiles.STYLE_NAMES[index].toUpperCase(Locale.ROOT));
        map.invalidate();
    }

    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                .setMinUpdateIntervalMillis(2000L)
                .build();

        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
    }

    private void stopLocationUpdates() {
        fusedClient.removeLocationUpdates(locationCallback);
    }

    private void updateMyLocation(Location loc) {
        myLocation = loc;
        GeoPoint point = new GeoPoint(loc.getLatitude(), loc.getLongitude());

        if (accuracyCircle == null) {
            accuracyCircle = new Polygon(map);
            accuracyCircle.setFillColor(0x222563EB);
            accuracyCircle.setStrokeColor(0x802563EB);
            accuracyCircle.setStrokeWidth(2f);
            map.getOverlays().add(0, accuracyCircle);
        }
        accuracyCircle.setPoints(Polygon.pointsAsCircle(point, loc.getAccuracy()));

        if (myMarker == null) {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(Color.parseColor("#2563EB"));
            dot.setStroke(6, Color.WHITE);
            dot.setSize(56, 56);

            myMarker = new Marker(map);
            myMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            myMarker.setIcon(dot);
            myMarker.setTitle("You are here");
            map.getOverlays().add(myMarker);
        }

        myMarker.setPosition(point);

        statusText.setText(String.format(Locale.getDefault(),
                "Live location: %.5f, %.5f  (accuracy %d m)",
                loc.getLatitude(), loc.getLongitude(), Math.round(loc.getAccuracy())));

        if (!initialViewDone && markersLoaded && patientMarkers.isEmpty()) {
            initialViewDone = true;
            map.getController().setZoom(16.0);
            map.getController().setCenter(point);
        }

        map.invalidate();
    }

    private void loadMarkers() {
        new AsyncTask<Void, Void, List<PatientEntity>>() {
            @Override
            protected List<PatientEntity> doInBackground(Void... voids) {
                return db.patientDao().getAllPatients();
            }

            @Override
            protected void onPostExecute(List<PatientEntity> patients) {

                for (int i = 0; i < patientMarkers.size(); i++) {
                    map.getOverlays().remove(patientMarkers.get(i));
                }
                patientMarkers.clear();

                for (int i = 0; i < patients.size(); i++) {
                    final PatientEntity p = patients.get(i);

                    if (p.latitude == 0.0 && p.longitude == 0.0) continue;

                    final GeoPoint point = new GeoPoint(p.latitude, p.longitude);
                    final String priority = PriorityUtils.classify(p.followUpDate);

                    BitmapDrawable pin;
                    if (priority.equals(PriorityUtils.HIGH)) {
                        pin = pinRed;
                    } else if (priority.equals(PriorityUtils.DUE_TODAY)) {
                        pin = pinOrange;
                    } else {
                        pin = pinGreen;
                    }

                    Marker marker = new Marker(map);
                    marker.setPosition(point);
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    marker.setIcon(pin);
                    marker.setTitle(p.name);
                    marker.setOnMarkerClickListener((m, mapView) -> {
                        showPatientDialog(p, point, priority);
                        return true;
                    });
                    map.getOverlays().add(marker);
                    patientMarkers.add(marker);
                }

                markersLoaded = true;

                if (patientMarkers.isEmpty()) {
                    Toast.makeText(MapActivity.this,
                            "No patients with a location yet. Add a patient and pick the exact location on the map.",
                            Toast.LENGTH_LONG).show();
                    if (!initialViewDone && myLocation != null) {
                        initialViewDone = true;
                        map.getController().setZoom(16.0);
                        map.getController().setCenter(new GeoPoint(
                                myLocation.getLatitude(), myLocation.getLongitude()));
                    }
                } else if (!fittedToPatients) {
                    fittedToPatients = true;
                    initialViewDone = true;
                    fitAll();
                }

                map.invalidate();
            }
        }.execute();
    }

    private void fitAll() {
        List<GeoPoint> points = new ArrayList<>();

        for (int i = 0; i < patientMarkers.size(); i++) {
            points.add(patientMarkers.get(i).getPosition());
        }
        if (myLocation != null) {
            points.add(new GeoPoint(myLocation.getLatitude(), myLocation.getLongitude()));
        }

        if (points.isEmpty()) {
            Toast.makeText(this, "Nothing to show yet", Toast.LENGTH_SHORT).show();
            return;
        }

        final BoundingBox box = BoundingBox.fromGeoPoints(points);

        if (points.size() == 1
                || (box.getLatitudeSpan() < 0.0005 && box.getLongitudeSpan() < 0.0005)) {
            map.getController().setZoom(16.0);
            map.getController().setCenter(points.get(0));
            return;
        }

        map.post(() -> map.zoomToBoundingBox(box, true, 150));
    }

    private void showPatientDialog(PatientEntity p, GeoPoint point, String priority) {
        String distanceText = "Distance unknown (waiting for GPS)";
        if (myLocation != null) {
            double meters = new GeoPoint(myLocation.getLatitude(), myLocation.getLongitude())
                    .distanceToAsDouble(point);
            distanceText = formatDistance(meters) + " from you";
        }

        String message = p.village
                + "\nPriority: " + PriorityUtils.label(priority)
                + "\nFollow-up: " + p.followUpDate
                + "\n" + distanceText;

        new AlertDialog.Builder(this)
                .setTitle(p.name)
                .setMessage(message)
                .setPositiveButton("Navigate", (dialog, which) -> openNavigation(p, point))
                .setNegativeButton("Close", null)
                .show();
    }

    private String formatDistance(double meters) {
        if (meters < 1000) {
            return String.format(Locale.getDefault(), "%d m", Math.round(meters));
        }
        return String.format(Locale.getDefault(), "%.1f km", meters / 1000.0);
    }

    private void openNavigation(PatientEntity p, GeoPoint point) {
        Uri directions = Uri.parse("google.navigation:q=" + point.getLatitude() + "," + point.getLongitude());
        Intent googleMaps = new Intent(Intent.ACTION_VIEW, directions);
        googleMaps.setPackage("com.google.android.apps.maps");

        try {
            startActivity(googleMaps);
            return;
        } catch (ActivityNotFoundException e) {
            // Google Maps is not installed, fall back to any maps app
        }

        Uri geo = Uri.parse("geo:" + point.getLatitude() + "," + point.getLongitude()
                + "?q=" + point.getLatitude() + "," + point.getLongitude()
                + "(" + Uri.encode(p.name) + ")");

        try {
            startActivity(new Intent(Intent.ACTION_VIEW, geo));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No maps app found on this device", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                statusText.setText("Location permission denied. Live position is off.");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
        startLocationUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
        stopLocationUpdates();
    }
}