// HomeFragment.java
package com.example.testweb;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Objects;

public class HomeFragment extends Fragment {

    private EditText editTextIp;
    private EditText editTextSend;
    private Button connectButton;
    private Button sendButton;
    private TextView messageView;
    private TextView dataView;
    private Socket socket;
    private PrintWriter output;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_home, container, false);

        editTextIp = view.findViewById(R.id.editTextIp);
        editTextSend = view.findViewById(R.id.editTextSend);        // ✅ 初始化发送输入框
        connectButton = view.findViewById(R.id.connectButton);
        sendButton = view.findViewById(R.id.sendButton);            // ✅ 初始化发送按钮
        dataView = view.findViewById(R.id.dataView);
        messageView = view.findViewById(R.id.messageView);
        messageView.setMovementMethod(new ScrollingMovementMethod());

        connectButton.setOnClickListener(v -> {
            String ip = editTextIp.getText().toString();
            new Thread(() -> connectToServer(ip)).start();
        });

        sendButton.setOnClickListener(v -> {
            String message = editTextSend.getText().toString();
            if (message.isEmpty()) {
                messageView.append("请输入内容\n");
                return;
            }
            if (output != null) {
                new Thread(() -> {
                    output.println(message);
                    requireActivity().runOnUiThread(() ->
                            messageView.append("已发送: " + message + "\n"));
                }).start();
            } else {
                messageView.append("未连接服务器\n");
            }
        });

        return view;
    }

    private void connectToServer(String ip) {
        try {
            socket = new Socket(ip, 12345);
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(socket.getOutputStream(), true);  // ✅ 修正为赋值到类成员变量 output

            requireActivity().runOnUiThread(() ->
                    messageView.append("连接成功: " + ip + "\n"));

            String line;
            while ((line = input.readLine()) != null) {
//                String finalLine = line;
//                requireActivity().runOnUiThread(() ->
//                        messageView.append("接收到: " + finalLine + "\n"));

                int data_len = 3;
                String[] parts = line.split(",");

                if(Objects.equals(parts[0], "data"))
                {
                    requireActivity().runOnUiThread(() ->
                            dataView.setText("data:\n"));
                    if (parts.length == (data_len + 1)) {
                        try {

                            for (int i=0;i<3;i++)
                            {
                                float data_par = Float.parseFloat(parts[i+1]);
                                requireActivity().runOnUiThread(() ->
                                        messageView.append("接收到: " + data_par + "\n"));
                                int finalI = i + 1;
                                requireActivity().runOnUiThread(() ->
                                        dataView.append("数据" + finalI + ": " + data_par + "\n"));
                            }

                        } catch (NumberFormatException e) {
                            // 数据解析错误
                            requireActivity().runOnUiThread(() ->
                                    messageView.append("数据解析错误\n"));
                        }
                    }
                }


            }
        } catch (Exception e) {
            e.printStackTrace();
            requireActivity().runOnUiThread(() ->
                    messageView.append("连接失败: " + e.getMessage() + "\n"));
        }
    }

    // 将大端序的4个字节转换为float
    private float bytesToFloat(byte[] bytes, int offset) {
        int intBits = ((bytes[offset] & 0xFF) << 24) |
                ((bytes[offset+1] & 0xFF) << 16) |
                ((bytes[offset+2] & 0xFF) << 8) |
                (bytes[offset+3] & 0xFF);
        return Float.intBitsToFloat(intBits);
    }


    // 不能destroy，否则页面会崩溃
//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//        try {
//            if (socket != null && !socket.isClosed()) {
//                socket.close();
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
}
