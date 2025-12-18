package com.nirma.attendance;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.*;
import java.nio.charset.StandardCharsets;

public class StudentActivity extends AppCompatActivity {

    private static final String SERVICE_ID = "com.nirma.attendance";
    private EditText etRollNo;
    private TextView statusLog;
    private Button btnScan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student);

        etRollNo = findViewById(R.id.etRollNo);
        statusLog = findViewById(R.id.statusLog);
        btnScan = findViewById(R.id.btnScan);

        btnScan.setOnClickListener(v -> {
            if (etRollNo.getText().toString().isEmpty()) {
                etRollNo.setError("Enter Roll No!");
                return;
            }
            if (hasPermissions()) {
                // Check if GPS is ON before scanning
                checkLocationEnabled();
            } else {
                requestPermissions();
            }
        });
    }

    // --- 1. GPS CHECKER (The "Foolproof" Method) ---
    private void checkLocationEnabled() {
        android.location.LocationManager lm = (android.location.LocationManager) getSystemService(android.content.Context.LOCATION_SERVICE);
        boolean gps_enabled = false;

        try {
            gps_enabled = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
        } catch(Exception ex) {}

        if(!gps_enabled) {
            new android.app.AlertDialog.Builder(this)
                    .setMessage("Please turn on GPS Location to mark attendance.")
                    .setPositiveButton("Open Settings", (paramDialogInterface, paramInt) -> {
                        startActivity(new android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            startDiscovery();
        }
    }

    // --- 2. DISCOVERY LOGIC ---
    private void startDiscovery() {
        // FIX: Always stop previous scan first to avoid "Status Already Discovering" (8002)
        Nearby.getConnectionsClient(this).stopDiscovery();

        statusLog.setText("Looking for Professor...");
        DiscoveryOptions options = new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build();

        Nearby.getConnectionsClient(this)
                .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
                .addOnSuccessListener((Void unused) -> statusLog.setText("Scanning for class..."))
                .addOnFailureListener((Exception e) -> statusLog.setText("Scan Failed: " + e.getMessage()));
    }

    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
            statusLog.setText("Found: " + info.getEndpointName() + ". Connecting...");
            Nearby.getConnectionsClient(getApplicationContext())
                    .requestConnection("Student", endpointId, connectionLifecycleCallback);
        }
        @Override
        public void onEndpointLost(@NonNull String endpointId) {}
    };

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo info) {
            Nearby.getConnectionsClient(getApplicationContext()).acceptConnection(endpointId, payloadCallback);
        }

        @Override
        public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution result) {
            if (result.getStatus().isSuccess()) {
                sendAttendanceData(endpointId);
            }
        }
        @Override
        public void onDisconnected(@NonNull String endpointId) {}
    };

    // UPDATED: This now asks the Server FIRST before bothering the Professor
    private void sendAttendanceData(String endpointId) {
        String rollNo = etRollNo.getText().toString();
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        statusLog.setText("Verifying with Server...");

        // Start the check.
        // If SERVER says "Success" -> It will automatically call sendPayloadToProfessor
        checkAttendanceWithServer(rollNo, deviceId, endpointId);
    }

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            String msg = new String(payload.asBytes(), StandardCharsets.UTF_8);
            if (msg.equals("SUCCESS")) {
                statusLog.setText("✅ ATTENDANCE MARKED!");
                Nearby.getConnectionsClient(getApplicationContext()).disconnectFromEndpoint(endpointId);
            }
        }
        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {}
    };

    // --- 3. PERMISSIONS (Android 13 Fix) ---
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

    // --- 4. SERVER CONNECTION (UPDATED FIX) ---
    private void checkAttendanceWithServer(String uid, String devId, String endpointId) {
        new Thread(() -> {
            try {
                // Your Laptop IP (Make sure this is correct!)
                String serverIp = "192.168.1.8";
                String path = "http://" + serverIp + ":8080/api/mark?uid=" + uid + "&devId=" + devId;

                java.net.URL url = new java.net.URL(path);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                java.util.Scanner sc = new java.util.Scanner(conn.getInputStream());
                if (sc.hasNext()) {
                    String response = sc.nextLine();

                    runOnUiThread(() -> {
                        // Show server message (Success or Error)
                        Toast.makeText(StudentActivity.this, response, Toast.LENGTH_LONG).show();
                        statusLog.setText(response);

                        if (response.toLowerCase().contains("success")) {
                            // ✅ CASE 1: SUCCESS - Send data to Professor
                            sendPayloadToProfessor(uid, devId, endpointId);
                        } else {
                            // ❌ CASE 2: ERROR (Duplicate/Proxy) - DISCONNECT IMMEDIATELY
                            // This fixes the issue of getting stuck
                            Nearby.getConnectionsClient(StudentActivity.this).disconnectFromEndpoint(endpointId);
                            statusLog.setText(response + " (Disconnected)");
                        }
                    });
                }
                conn.disconnect();

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(StudentActivity.this, "Server Error", Toast.LENGTH_SHORT).show();
                    // Safe to disconnect here too
                    Nearby.getConnectionsClient(StudentActivity.this).disconnectFromEndpoint(endpointId);
                });
            }
        }).start();
    }

    private void sendPayloadToProfessor(String rollNo, String deviceId, String endpointId) {
        String data = rollNo + "\n(Device: " + deviceId.substring(0,6) + ")";
        Payload payload = Payload.fromBytes(data.getBytes(StandardCharsets.UTF_8));

        Nearby.getConnectionsClient(this).sendPayload(endpointId, payload);
        statusLog.setText("✅ Verified & Sent to Prof!");
    }
}