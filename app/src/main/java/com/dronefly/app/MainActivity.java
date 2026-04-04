package com.dronefly.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView tvStatus   = findViewById(R.id.tvStatus);
        TextView tvDroneInfo = findViewById(R.id.tvDroneInfo);
        Button   btnPlan    = findViewById(R.id.btnPlanMission);

        tvStatus.setText("Tervező mód");
        tvDroneInfo.setText("Drón: nem csatlakoztatva");

        btnPlan.setOnClickListener(v ->
            startActivity(new Intent(this, MissionPlannerActivity.class)));
    }
}
