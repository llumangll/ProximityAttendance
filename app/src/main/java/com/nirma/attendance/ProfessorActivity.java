package com.nirma.attendance;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ProfessorActivity extends AppCompatActivity {

    private static final String SERVICE_ID = "com.nirma.attendance";
    private Button btnStart, btnStop, btnExport; // Defined correctly at top
    private TextView statusText;
    private ListView studentListView;
    private ArrayList<String> studentList;
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_professor);

        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnExport = findViewById(R.id.btnExport); // Initialized here
        statusText = findViewById(R.id.statusText);
        studentListView = findViewById(R.id.studentListView);

        studentList = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, studentList);
        studentListView.setAdapter(adapter);

        btnStart.setOnClickListener(v -> {
            if (hasPermissions()) {
                checkLocationEnabled();
            } else {
                requestPermissions();
            }
        });

        btnStop.setOnClickListener(v -> {
            Nearby.getConnectionsClient(this).stopAdvertising();
            Nearby.getConnectionsClient(this).stopAllEndpoints();
            statusText.setText("Status: Stopped");
            Toast.makeText(this, "Session Stopped", Toast.LENGTH_SHORT).show();
        });

        btnExport.setOnClickListener(v -> {
            if (studentList.isEmpty()) {
                Toast.makeText(this, "No students to export!", Toast.LENGTH_SHORT).show();
                return;
            }
            createFile();
        });
    }

    // --- CSV EXPORT LOGIC ---
    private void createFile() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE, "Attendance_" + timeStamp + ".csv");
        startActivityForResult(intent, 4001);
    }

    private void writeCSV(Uri uri) {
        try {
            OutputStream outputStream = getContentResolver().openOutputStream(uri);
            StringBuilder sb = new StringBuilder();

            // Header
            sb.append("Roll Number,Timestamp\n");

            for (String student : studentList) {
                // student string looks like: "21BCE045\n(Device: abc) @ 10:30:45"

                // 1. Split into Name and Time
                String[] parts = student.split(" @ ");

                String namePart = parts[0].replace("\n", " ").replace(",", " "); // Clean up name
                String timePart = (parts.length > 1) ? parts[1] : ""; // Get time if it exists

                // 2. Write: Name,Time
                sb.append(namePart).append(",").append(timePart).append("\n");
            }

            outputStream.write(sb.toString().getBytes());
            outputStream.close();

            Toast.makeText(this, "Saved Successfully!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error saving file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 4001 && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                writeCSV(data.getData());
            }
        }
    }

    // --- GPS CHECKER ---
    private void checkLocationEnabled() {
        android.location.LocationManager lm = (android.location.LocationManager) getSystemService(android.content.Context.LOCATION_SERVICE);
        boolean gps_enabled = false;
        try {
            gps_enabled = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
        } catch(Exception ex) {}

        if(!gps_enabled) {
            new android.app.AlertDialog.Builder(this)
                    .setMessage("Please turn on GPS Location to host the class.")
                    .setPositiveButton("Open Settings", (paramDialogInterface, paramInt) -> {
                        startActivity(new android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            startAdvertising();
        }
    }

    // --- ADVERTISING LOGIC ---
    private void startAdvertising() {
        AdvertisingOptions options = new AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build();

        Nearby.getConnectionsClient(this)
                .startAdvertising("Professor", SERVICE_ID, connectionLifecycleCallback, options)
                .addOnSuccessListener((Void unused) -> statusText.setText("Status: BROADCASTING..."))
                .addOnFailureListener((Exception e) -> statusText.setText("Error: " + e.getMessage()));
    }

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo info) {
            Nearby.getConnectionsClient(getApplicationContext()).acceptConnection(endpointId, payloadCallback);
        }
        @Override
        public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution result) {}
        @Override
        public void onDisconnected(@NonNull String endpointId) {}
    };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            String data = new String(payload.asBytes(), StandardCharsets.UTF_8);

            // CHANGED: "hh:mm:ss a" gives "12:00:24 AM" instead of "00:00:24"
            String time = new SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(new Date());

            studentList.add(data + " @ " + time);
            adapter.notifyDataSetChanged();

            Payload response = Payload.fromBytes("SUCCESS".getBytes(StandardCharsets.UTF_8));
            Nearby.getConnectionsClient(getApplicationContext()).sendPayload(endpointId, response);
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {}
    };

    // --- PERMISSIONS ---
    private boolean hasPermissions() {
        boolean locationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (Build.VERSION.SDK_INT >= 33) {
            return locationPermission &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return locationPermission &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return locationPermission;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, 100);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, 100);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, 100);
        }
    }
}