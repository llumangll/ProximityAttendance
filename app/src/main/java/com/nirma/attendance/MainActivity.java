package com.nirma.attendance;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnProf = findViewById(R.id.btnProf);
        Button btnStudent = findViewById(R.id.btnStudent);

        btnProf.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ProfessorActivity.class);
            startActivity(intent);
        });

        btnStudent.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Student Mode Selected", Toast.LENGTH_SHORT).show();
        });
    }
}