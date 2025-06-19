// HomeFragment.java
package com.example.testweb;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

import android.graphics.Color;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class HomeFragment extends Fragment {

    private EditText editTextIp;
    private EditText editTextSend;
    private Button connectButton;
    private Button sendButton;
    private Button clearButton;
    private TextView messageView;
    private TextView dataView;
    private Socket socket;
    private PrintWriter output;
    private LineChart lineChart;
    private final List<Entry> entries = new ArrayList<>();
    private LineDataSet dataSet;
    private LineData lineData;
    private boolean hasAlerted = false;
    private ScrollView scrollView;;


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
        clearButton = view.findViewById(R.id.clearButton);
        dataView = view.findViewById(R.id.dataView);
        messageView = view.findViewById(R.id.messageView);
        messageView.setMovementMethod(new ScrollingMovementMethod());
        lineChart = view.findViewById(R.id.chart1);
        scrollView = view.findViewById(R.id.myscrollView);

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

        clearButton.setOnClickListener(v -> {
            entries.clear();
            dataSet.setValues(new ArrayList<>());
            dataSet.notifyDataSetChanged();
            lineData.notifyDataChanged();
            lineChart.notifyDataSetChanged();
            lineChart.invalidate();
            requireActivity().runOnUiThread(() ->
                    messageView.setText(""));
            requireActivity().runOnUiThread(() ->
                    dataView.setText(""));
        });

        drawInit();

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

                int data_len = 0; // 接收到的数据长度(以逗号分割的数量为准)

                // 解析字符串型数据，数据之间用","分割
                String[] parts = line.split(",");

                // 在data区显示接收到的数据
                if(Objects.equals(parts[0], "data"))
                {
                    requireActivity().runOnUiThread(() ->
                            dataView.setText("data:\n"));
                    data_len = parts.length - 1;
                    try {
                        for (int i=0;i<data_len;i++)
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
                // 不定长数据接收并绘图
                else if(Objects.equals(parts[0], "chartdata"))
                {
                    data_len = parts.length - 1;
                    try {
                        List<Entry> entries = new ArrayList<>();
                        for (int i=0;i<data_len;i++)
                        {
                            float data_par = Float.parseFloat(parts[i+1]);
                            entries.add(new Entry(i, data_par));
                            requireActivity().runOnUiThread(() ->
                                    messageView.append("接收到: " + data_par + "\n"));
                            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN)); // 滚动到底部
                        }

                        // 创建数据集
                        LineDataSet dataSet = new LineDataSet(entries, "扫频数据");
                        dataSet.setColor(Color.BLACK);
                        dataSet.setValueTextColor(Color.BLACK);
                        dataSet.setCircleColor(Color.RED);
                        dataSet.setLineWidth(2f);
                        dataSet.setCircleRadius(3f);
                        dataSet.setValueTextSize(12f);
                        dataSet.setDrawCircleHole(false);
                        dataSet.setDrawValues(false);

                        // 添加到图表
                        LineData lineData = new LineData(dataSet);
                        lineChart.setData(lineData);
                        lineChart.invalidate(); // 刷新

                    } catch (NumberFormatException e) {
                        // 数据解析错误
                        requireActivity().runOnUiThread(() ->
                                messageView.append("数据解析错误\n"));
                    }
                }
                // 三点数据连续接收，并实时绘图
                else if (parts.length >= 4 && "tridata".equals(parts[0])) {
                    try {
                        float f = Float.parseFloat(parts[1]); // x轴 - 频率
                        float z = Float.parseFloat(parts[2]); // x2  - 阻抗
                        float v = Float.parseFloat(parts[3]); // y轴 - 体积

                        requireActivity().runOnUiThread(() ->
                                dataView.setText(""));
                        requireActivity().runOnUiThread(() ->
                                dataView.append("频率: " + f + "\n"));
                        requireActivity().runOnUiThread(() ->
                                dataView.append("阻抗: " + z + "\n"));
                        requireActivity().runOnUiThread(() ->
                                dataView.append("体积: " + v + "\n"));

                        Entry entry = new Entry(v, f);
                        entries.add(entry);

                        // 关键修复：按x轴(v)升序排序，避免闪退
                        entries.sort((e1, e2) -> Float.compare(e1.getX(), e2.getX()));

                        // ⚠️ 必须重新设置 dataset 的值！
                        dataSet.setValues(entries);

                        requireActivity().runOnUiThread(() -> {
                            messageView.append("接收到: f=" + f + ", v=" + v + "\n");
                            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN)); // 滚动到底部

                            dataSet.notifyDataSetChanged();
                            lineData.notifyDataChanged();
                            lineChart.notifyDataSetChanged();

                            // 可选：自动移动到最新 x 点
    //                        lineChart.setVisibleXRangeMaximum(100);
    //                        lineChart.moveViewToX(f);

                            lineChart.invalidate(); // 刷新图表

                            // 报警提示
                            if ((v > 200.0) && !hasAlerted) {
                                hasAlerted = true;

                                // 播放提示音（系统默认通知声）
                                try {
                                    MediaPlayer mediaPlayer = MediaPlayer.create(requireContext(), Settings.System.DEFAULT_NOTIFICATION_URI);
                                    if (mediaPlayer != null) {
                                        mediaPlayer.start();
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                // 震动提示（兼容 Android 12）
                                Vibrator vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
                                if (vibrator != null) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        // 震动持续 500ms，使用默认强度
                                        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                                    } else {
                                        vibrator.vibrate(500); // 旧版本方式
                                    }
                                }

                                // 报警提示对话框
                                new AlertDialog.Builder(requireContext())
                                        .setTitle("报警提示")
                                        .setMessage("检测到异常数据：体积 = " + v + " mL")
                                        .setPositiveButton("确定", (dialog, which) -> {
                                            hasAlerted = false; // 用户确认后允许再次报警
                                        })
                                        .show();
                            }

                        });

                    } catch (NumberFormatException e) {
                        requireActivity().runOnUiThread(() ->
                                messageView.append("数据解析错误\n"));
                    }
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
            requireActivity().runOnUiThread(() ->
                    messageView.append("连接失败: " + e.getMessage() + "\n"));
        }
    }

    private void drawInit() {
        // 设置图表标题
        Description description = new Description();
        description.setText(" ");
        description.setTextSize(14f);
        description.setTextColor(Color.DKGRAY);
        lineChart.setDescription(description);

        // 设置 X 轴
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextSize(10f);
        xAxis.setTextColor(Color.BLACK);
        xAxis.setDrawAxisLine(true);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(300f);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return value + " mL";
            }
        });

        // 设置 Y 轴
        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setTextSize(12f);
        leftAxis.setTextColor(Color.BLACK);
        leftAxis.setDrawGridLines(false);
        leftAxis.setAxisMinimum(0.8f);
        leftAxis.setAxisMaximum(3f);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return value + " MHz";
            }
        });

        lineChart.getAxisRight().setEnabled(false); // 关闭右侧Y轴

        // 基础交互设置
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setPinchZoom(true);
        lineChart.setDrawGridBackground(false);
        lineChart.setBackgroundColor(Color.WHITE);

        // 风格调整
        lineChart.setBackgroundColor(Color.TRANSPARENT);           // 图表整体背景透明
        lineChart.setDrawGridBackground(false);                    // 不绘制网格背景（默认灰白）
        lineChart.setExtraOffsets(5, 5, 5, 5);                      // 可选：图表边距
        lineChart.getXAxis().setGridColor(Color.LTGRAY);           // 网格线颜色（可调淡）
        lineChart.getAxisLeft().setGridColor(Color.LTGRAY);

        // 初始化数据集
        dataSet = new LineDataSet(entries, "扫频数据");
        dataSet.setColor(Color.BLACK);
        dataSet.setCircleColor(Color.RED);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawCircleHole(false);
        dataSet.setDrawValues(false);

        lineData = new LineData(dataSet);
        lineChart.setData(lineData);

    }


    // 不能destroy，否则页面会崩溃
    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
