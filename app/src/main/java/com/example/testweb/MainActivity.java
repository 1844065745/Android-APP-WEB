package com.example.testweb;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    private EditText editTextIP;
    private Button buttonConnect;
    private TextView textViewResult;

    private final int SERVER_PORT = 12345; // ESP32 服务端监听的端口

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        editTextIP = findViewById(R.id.editTextIP);
        buttonConnect = findViewById(R.id.buttonConnect);
        textViewResult = findViewById(R.id.textViewResult);

        buttonConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String ip = editTextIP.getText().toString().trim();
                if (!ip.isEmpty()) {
                    connectToESP32(ip);
                } else {
                    Toast.makeText(MainActivity.this, "请输入 IP 地址", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void connectToESP32(String ip) {
        ScrollView scrollView = findViewById(R.id.scrollView);
        new Thread(() -> {
            try {
                Socket socket = new Socket(ip, SERVER_PORT);

                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                out.println("Hello from Android!");

                String response = in.readLine();

                runOnUiThread(() -> {
                    runOnUiThread(() -> {
                        String current = textViewResult.getText().toString();
                        textViewResult.setText(current + "ESP32: " + response + "\n");
                        // 自动滚动到底部
                        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                    });
                });

                in.close();
                out.close();
                socket.close();
            } catch (IOException e) {
                Log.e("SOCKET", "连接失败", e);
                runOnUiThread(() -> {
                    String current = textViewResult.getText().toString();
                    textViewResult.setText(current + e.getMessage() + "\n");
                    // 自动滚动到底部
                    scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));

                });
            }
        }).start();
    }
    
}