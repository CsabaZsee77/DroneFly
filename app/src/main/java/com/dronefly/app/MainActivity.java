package com.dronefly.app;

import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.dronefly.app.dji.DJIHelper;

public class MainActivity extends AppCompatActivity {

    private TextView tvDroneInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView tvStatus = findViewById(R.id.tvStatus);
        tvDroneInfo = findViewById(R.id.tvDroneInfo);
        Button btnPlan = findViewById(R.id.btnPlanMission);

        tvStatus.setText("DroneFly GCS");
        updateDroneStatus();

        btnPlan.setOnClickListener(v ->
            startActivity(new Intent(this, MissionPlannerActivity.class)));

        // M09 — tőszámlálás bármikor elérhető, repüléstől függetlenül
        Button btnCounting = findViewById(R.id.btnCounting);
        btnCounting.setOnClickListener(v ->
            startActivity(new Intent(this, SamplingResultsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            DJIHelper helper = DJIHelper.getInstance();
            helper.setListener(new DJIHelper.ConnectionListener() {
                @Override public void onRegistered(boolean success, String message) {
                    runOnUiThread(() -> updateDroneStatus());
                }
                @Override public void onProductConnected(String productName) {
                    runOnUiThread(() -> updateDroneStatus());
                }
                @Override public void onProductDisconnected() {
                    runOnUiThread(() -> updateDroneStatus());
                }
            });
            if (!helper.isConnected()) helper.reconnect();
        } catch (Throwable t) { /* DJI SDK nem elérhető */ }
        updateDroneStatus();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(intent.getAction())) {
            try {
                DJIHelper.getInstance().reconnect();
            } catch (Throwable t) { /* ignore */ }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            DJIHelper.getInstance().setListener(null);
        } catch (Throwable t) { /* ignore */ }
    }

    private void updateDroneStatus() {
        if (tvDroneInfo == null) return;
        try {
            DJIHelper helper = DJIHelper.getInstance();
            if (helper.isConnected()) {
                String name = helper.getConnectedProductName();
                String label = (name != null && !name.isEmpty())
                        ? "Drón: " + name
                        : "Drón: csatlakoztatva";
                tvDroneInfo.setText(label);
                tvDroneInfo.setTextColor(0xFF00FF88);
            } else if (helper.isRegistered()) {
                tvDroneInfo.setText("Drón: keresés...");
                tvDroneInfo.setTextColor(0xFFFFAA00);
            } else {
                tvDroneInfo.setText("Drón: nincs csatlakoztatva");
                tvDroneInfo.setTextColor(0xFFAAAAAA);
            }
        } catch (Throwable t) {
            tvDroneInfo.setText("Drón: nincs csatlakoztatva");
            tvDroneInfo.setTextColor(0xFFAAAAAA);
        }
    }
}
