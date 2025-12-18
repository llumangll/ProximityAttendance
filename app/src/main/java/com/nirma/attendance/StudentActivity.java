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

    private static final String SERVICE_ID = "com.nirma.attendance"; // MUST MATCH PROFESSOR
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
                startDiscovery();
            } else {
                requestPermissions();
            }
        });
    }

    private void startDiscovery() {
        statusLog.setText("Looking for Professor...");
        DiscoveryOptions options = new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build();

        Nearby.getConnectionsClient(this)
                .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
                .addOnSuccessListener((Void unused) -> statusLog.setText("Scanning for class..."))
                .addOnFailureListener((Exception e) -> statusLog.setText("Scan Failed: " + e.getMessage()));
    }

    // 1. Found the Professor
    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
            statusLog.setText("Found: " + info.getEndpointName() + ". Connecting...");
            // Request Connection
            Nearby.getConnectionsClient(getApplicationContext())
                    .requestConnection("Student", endpointId, connectionLifecycleCallback);
        }
        @Override
        public void onEndpointLost(@NonNull String endpointId) {}
    };

    // 2. Connection Lifecycle
    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo info) {
            // Auto-Accept
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

    // 3. Send Data (Roll No + Unique Device ID)
    private void sendAttendanceData(String endpointId) {
        String rollNo = etRollNo.getText().toString();
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        String data = rollNo + "\n(Device: " + deviceId.substring(0,6) + ")";

        Payload payload = Payload.fromBytes(data.getBytes(StandardCharsets.UTF_8));
        Nearby.getConnectionsClient(this).sendPayload(endpointId, payload);
        statusLog.setText("Sending Data...");
    }

    // 4. Receive Confirmation
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

    // Permissions (Same as Professor)
    // ---------------------------------------------------------
    // UPDATED PERMISSION LOGIC FOR ANDROID 13 (API 33) FIX
    // ---------------------------------------------------------

    // ---------------------------------------------------------
    // FINAL PERMISSION FIX (INCLUDES COARSE LOCATION)
    // ---------------------------------------------------------

    private boolean hasPermissions() {
        // Base checks for Location (Both Fine and Coarse are needed for stability)
        boolean locationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        // Android 13+ (API 33)
        if (Build.VERSION.SDK_INT >= 33) {
            return locationPermission &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        // Android 12 (API 31/32)
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return locationPermission &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        // Android 11 and below
        else {
            return locationPermission;
        }
    }

    private void requestPermissions() {
        // Android 13+ (API 33)
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION // Added
            }, 100);
        }
        // Android 12
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION // Added
            }, 100);
        }
        // Android 11 and below
        else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION // Added
            }, 100);
        }
    }
}