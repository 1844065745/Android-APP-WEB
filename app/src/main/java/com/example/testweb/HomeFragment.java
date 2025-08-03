package com.example.testweb;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.Collections;
import java.util.Comparator;
import java.util.stream.IntStream;
import org.jtransforms.fft.DoubleFFT_1D;


public class HomeFragment extends Fragment {

    private EditText editTextIp, editTextSend;
    private Button connectButton, sendButton, clearButton;
    private TextView messageView, dataView, volumeView;
    private ScrollView scrollView;
    private LineChart lineChart;
    private LineChart settingsChart;


    private Socket socket;
    private PrintWriter output;

    private final List<Entry> entries = new ArrayList<>();
    private LineDataSet dataSet;
    private LineData lineData;
    private boolean hasAlerted = false;

    private float latestPeakFreq = -1f;
    private List<Float> latestFiltered = new ArrayList<>();
    private List<Float> latestDataRef = new ArrayList<>();
    private Map<String, List<Float>> latestDataRawGroups = new LinkedHashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        initUI(view);
        setupListeners();
        setupChart();
        setupSettingsChart();
        observeLivePlotData();
        return view;
    }

    private void initUI(View view) {
        editTextIp = view.findViewById(R.id.editTextIp);
        editTextSend = view.findViewById(R.id.editTextSend);
        connectButton = view.findViewById(R.id.connectButton);
        sendButton = view.findViewById(R.id.sendButton);
        clearButton = view.findViewById(R.id.clearButton);
        dataView = view.findViewById(R.id.dataView);
        volumeView = view.findViewById(R.id.volumeView);
        messageView = view.findViewById(R.id.messageView);
        scrollView = view.findViewById(R.id.myscrollView);
        lineChart = view.findViewById(R.id.chart1);
        messageView.setMovementMethod(new ScrollingMovementMethod());
        settingsChart = view.findViewById(R.id.settingsChart);
    }

    private void setupListeners() {
        connectButton.setOnClickListener(v -> new Thread(() -> connectToServer(editTextIp.getText().toString())).start());
        sendButton.setOnClickListener(v -> {
            String message = editTextSend.getText().toString();
            if (message.isEmpty()) {
                appendMessage("Input content\n");
                return;
            }
            if (output != null) {
                new Thread(() -> {
                    output.println(message);
                    runOnUiThread(() -> appendMessage("[SEND] " + message + "\n"));
                }).start();
            } else {
                appendMessage("Server not connected\n");
            }
        });
        clearButton.setOnClickListener(v -> {
            entries.clear();
            dataSet.clear();
            runOnUiThread(() -> {
                lineChart.invalidate();
                messageView.setText("");
                dataView.setText("");
            });
        });
    }

    private void connectToServer(String ip) {
        try {
            socket = new Socket(ip, 12345);
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(socket.getOutputStream(), true);

            appendMessage("Connected to: " + ip + "\n");

            String line;
            while ((line = input.readLine()) != null) {
                appendMessage("[REV] " + line + "\n");
                handleIncomingLine(line);
            }
        } catch (Exception e) {
            appendMessage("connection failure: " + e.getMessage() + "\n");
        }
    }

    private void handleIncomingLine(String line) {
        String[] parts = line.split(",");
        if (parts.length == 0) return;
        switch (parts[0]) {
            case "data": handleData(parts); break;
            case "chartdata": handleChartData(parts); break;
            case "tridata": handleTriData(parts); break;
            case "data_sum": handleDataSum(parts); break;
            default: //appendMessage("[UNKNOWN] " + line + "\n");
        }
    }

    private void handleData(String[] parts) {
        runOnUiThread(() -> dataView.setText("data:\n"));
        for (int i = 1; i < parts.length; i++) {
            try {
                float value = Float.parseFloat(parts[i]);
                int finalI = i;
                runOnUiThread(() -> {
                    appendMessage("Received: " + value + "\n");
                    dataView.append("Data" + finalI + ": " + value + "\n");
                });
            } catch (NumberFormatException e) {
                appendMessage("Data error\n");
            }
        }
    }

    private void handleChartData(String[] parts) {
        List<Entry> newEntries = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            try {
                newEntries.add(new Entry(i - 1, Float.parseFloat(parts[i])));
            } catch (NumberFormatException e) {
                appendMessage("Data error\n");
                return;
            }
        }
        LineDataSet newDataSet = new LineDataSet(newEntries, "Sensor data");
        styleLineDataSet(newDataSet);
        runOnUiThread(() -> {
            lineChart.setData(new LineData(newDataSet));
            lineChart.invalidate();
        });
    }

    @SuppressLint("DefaultLocale")
    private void handleTriData(String[] parts) {
        try {
            float f = Float.parseFloat(parts[1]);
            float z = Float.parseFloat(parts[2]);
            float v = Float.parseFloat(parts[3]);

            runOnUiThread(() -> dataView.setText(String.format("Frequency: %.2f\nImpedance: %.2f\nVolume: %.2f\n", f, z, v)));
            entries.add(new Entry(v, f));
            entries.sort((a, b) -> Float.compare(a.getX(), b.getX()));
            dataSet.setValues(entries);
            runOnUiThread(() -> {
                dataSet.notifyDataSetChanged();
                lineData.notifyDataChanged();
                lineChart.notifyDataSetChanged();
                lineChart.invalidate();
                // if (v > 200.0 && !hasAlerted) showAlarm(v); // 旧版报警已弃用
            });
        } catch (NumberFormatException e) {
            appendMessage("Data error\n");
        }
    }

    private void handleDataSum(String[] parts) {
        List<Float> dataRef = new ArrayList<>();
        Map<String, List<Float>> dataRawGroups = new LinkedHashMap<>();
        List<Float> dataFilt = new ArrayList<>();
        float dataFre = -1f;

        int i = 1; // Skip "data_sum"

        // Step 1: check and parse "data_ref"
        if (!"data_ref".equals(parts[i])) {
            appendMessage("Missing data_ref section\n");
            return;
        }
        i++;

        while (i < parts.length && !parts[i].equals("data_raw")) {
            try {
                dataRef.add(Float.parseFloat(parts[i]));
            } catch (NumberFormatException e) {
                appendMessage("Invalid float in data_ref: " + parts[i] + "\n");
                return;
            }
            i++;
        }

        if (i >= parts.length || !"data_raw".equals(parts[i])) {
            appendMessage("Missing data_raw section\n");
            return;
        }
        i++; // Skip "data_raw"

        // Step 2: parse t0, t1, ...
        String currentKey = null;
        List<Float> currentList = null;

        while (i < parts.length) {
            String token = parts[i];

            if (token.equals("data_fre") || token.equals("data_filt")) {
                break;  // 进入下一阶段
            }

            if (token.matches("t\\d+")) {
                currentKey = token;
                currentList = new ArrayList<>();
                dataRawGroups.put(currentKey, currentList);
            } else if (currentList != null) {
                try {
                    currentList.add(Float.parseFloat(token));
                } catch (NumberFormatException e) {
                    appendMessage("Invalid float in " + currentKey + ": " + token + "\n");
                    return;
                }
            }
            i++;
        }

        // Step 3: parse optional data_fre
        if (i < parts.length && parts[i].equals("data_fre")) {
            i++;
            if (i < parts.length) {
                try {
                    dataFre = Float.parseFloat(parts[i]);
                } catch (NumberFormatException e) {
                    appendMessage("Invalid float in data_fre: " + parts[i] + "\n");
                }
                i++;
            }
        }

        // Step 4: parse optional data_filt
        if (i < parts.length && parts[i].equals("data_filt")) {
            i++;
            while (i < parts.length) {
                try {
                    dataFilt.add(Float.parseFloat(parts[i]));
                } catch (NumberFormatException e) {
                    appendMessage("Invalid float in data_filt: " + parts[i] + "\n");
                    break;
                }
                i++;
            }
        }

        // 保存数据供后续使用
        latestDataRef = new ArrayList<>(dataRef);
        latestDataRawGroups = new LinkedHashMap<>();
        for (Map.Entry<String, List<Float>> entry : dataRawGroups.entrySet()) {
            latestDataRawGroups.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        latestFiltered = new ArrayList<>(dataFilt);
        latestPeakFreq = dataFre;

        // Step 5: show data preview
        float finalDataFre = dataFre;
        runOnUiThread(() -> {
            StringBuilder builder = new StringBuilder();
            builder.append("data_ref (" + dataRef.size() + "):\n");
            builder.append(dataRef.subList(0, Math.min(5, dataRef.size())).toString()).append("\n\n");
            for (Map.Entry<String, List<Float>> entry : dataRawGroups.entrySet()) {
                builder.append(entry.getKey()).append(" (").append(entry.getValue().size()).append("):\n");
                builder.append(entry.getValue().subList(0, Math.min(5, entry.getValue().size())).toString()).append("\n\n");
            }
            builder.append(String.format("data_fre: %.4f MHz\n", finalDataFre));
            builder.append("data_filt preview:\n");
            builder.append(dataFilt.subList(0, Math.min(5, dataFilt.size())).toString()).append("\n\n");
            dataView.setText(builder.toString());
        });

        // processDataSumAndFindPeak();
        processReceivedFilteredData();
    }

    @SuppressLint("DefaultLocale")
    private void processReceivedFilteredData() {
        if (latestDataRef == null || latestDataRef.isEmpty() ||
                latestDataRawGroups == null || latestDataRawGroups.isEmpty() ||
                latestFiltered == null || latestFiltered.isEmpty() ||
                latestPeakFreq <= 0f) {
            appendMessage("Data not enough\n");
            return;
        }
        int N = latestFiltered.size();
        float freqStart = 0.8f;
        float freqRange = 1.0f;
        float step = freqRange / N;
        // 👉<*> 显示峰值频率 + 定性容量估计
        appendMessage(String.format("Frequency: %.3f MHz\n", latestPeakFreq));
        runOnUiThread(() -> dataView.setText(String.format("%.3f MHz", latestPeakFreq)));
        runOnUiThread(() -> {
            String state;
            int color;
            if (latestPeakFreq >= 0.8f && latestPeakFreq < 1.1f) {
                state = "FULL";
                color = Color.RED;
            } else if (latestPeakFreq >= 1.1f && latestPeakFreq < 1.2f) {
                state = "NORMAL";
                color = Color.rgb(255, 165, 0); // 橙色
            } else if (latestPeakFreq >= 1.2f && latestPeakFreq <= 1.9f) {
                state = "EMPTY";
                color = Color.GREEN;
            } else {
                state = "UNKNOWN";
                color = Color.GRAY;
            }
            volumeView.setText(state);
            volumeView.setTextColor(color);
            showAlarm();
        });
        // 👉 在图上标出主频率点
        drawSingleFrequencyPoint(latestPeakFreq);
        // 👉 转换 filtered 为 Double 列表传给 ViewModel
        List<Double> filteredD = new ArrayList<>();
        for (float v : latestFiltered) filteredD.add((double) v);
        // 👉 更新 ViewModel → 更新图表
        PlotDataViewModel viewModel = new ViewModelProvider(requireActivity()).get(PlotDataViewModel.class);
        viewModel.update(latestDataRef, latestDataRawGroups, filteredD);
        // 👉 保存数据到 CSV 文件
        try {
            File dir = new File(requireContext().getExternalFilesDir(null), "data_logs");
            if (!dir.exists()) dir.mkdirs();

            @SuppressLint("SimpleDateFormat")
            String timestamp = new java.text.SimpleDateFormat("MMdd_HHmmss").format(new java.util.Date());
            String filename = String.format("%s_%.3fMHz.csv", timestamp, latestPeakFreq);
            File file = new File(dir, filename);
            FileWriter fw = new FileWriter(file);
            // 写入 data_ref
            fw.append("data_ref");
            for (float v : latestDataRef) fw.append(",").append(String.valueOf(v));
            fw.append("\n");
            // 写入所有 data_raw
            for (Map.Entry<String, List<Float>> entry : latestDataRawGroups.entrySet()) {
                fw.append(entry.getKey());
                for (float v : entry.getValue()) fw.append(",").append(String.valueOf(v));
                fw.append("\n");
            }
            // 写入 filtered
            fw.append("filtered");
            for (float v : latestFiltered) fw.append(",").append(String.valueOf(v));
            fw.append("\n");
            fw.flush();
            fw.close();
            appendMessage("Saved to: " + filename + "\n");
        } catch (IOException e) {
            appendMessage("Write to CSV error: " + e.getMessage() + "\n");
        }
    }

    @SuppressLint("DefaultLocale")
    private void processDataSumAndFindPeak() {
        if (latestDataRef == null || latestDataRef.isEmpty() || latestDataRawGroups.isEmpty()) {
            appendMessage("No data for process.\n");
            return;
        }

        int N = latestDataRef.size();
        int numGroups = latestDataRawGroups.size();
        double[][] freqDataMatrix = new double[numGroups][N];

        // Step 1: 所有 data_raw - data_ref
        int groupIndex = 0;
        for (Map.Entry<String, List<Float>> entry : latestDataRawGroups.entrySet()) {
            List<Float> raw = entry.getValue();
            double[] diff = new double[N];
            for (int i = 0; i < N; i++) {
                diff[i] = raw.get(i) - latestDataRef.get(i);
            }
            freqDataMatrix[groupIndex++] = diff;
        }

        // Step 2: 计算每列的方差，权重 = 1 / var
        double[] variances = new double[numGroups];
        for (int j = 0; j < numGroups; j++) {
            double mean = 0, var = 0;
            for (int i = 0; i < N; i++) mean += freqDataMatrix[j][i];
            mean /= N;
            for (int i = 0; i < N; i++) var += Math.pow(freqDataMatrix[j][i] - mean, 2);
            variances[j] = var / N;
        }

        double[] invVar = new double[numGroups];
        double sumInv = 0;
        for (int j = 0; j < numGroups; j++) {
            invVar[j] = 1.0 / (variances[j] + 1e-8);
            sumInv += invVar[j];
        }

        double[] weights = new double[numGroups];
        for (int j = 0; j < numGroups; j++) weights[j] = invVar[j] / sumInv;

        // Step 3: 构造 Hermitian 对称 → IFFT 得到时域信号
        int timeLen = 2 * N - 1;
        double[][] timeSignals = new double[numGroups][timeLen];
        DoubleFFT_1D fft = new DoubleFFT_1D(timeLen);

        for (int j = 0; j < numGroups; j++) {
            double[] full = new double[timeLen * 2];
            for (int i = 0; i < N; i++) full[2 * i] = freqDataMatrix[j][i];  // 实部
            for (int i = 1; i < N; i++) full[2 * (timeLen - i)] = freqDataMatrix[j][i]; // 实部镜像

            fft.complexInverse(full, true);

            for (int i = 0; i < timeLen; i++) {
                timeSignals[j][i] = full[2 * i]; // 实部
            }
        }

        // Step 4: 加权平均
        double[] weightedAvg = new double[timeLen];
        for (int i = 0; i < timeLen; i++) {
            for (int j = 0; j < numGroups; j++) {
                weightedAvg[i] += weights[j] * timeSignals[j][i];
            }
        }

        // Step 5: 简单平滑滤波器（代替 gaussian_filter1d）
        double[] smoothed = simpleGaussianSmooth(weightedAvg, 4);  // ** 这个参数对结果有影响！

        // Step 6: FFT → 找主峰
        DoubleFFT_1D fft2 = new DoubleFFT_1D(timeLen);
        double[] fftData = new double[timeLen * 2];
        for (int i = 0; i < timeLen; i++) {
            fftData[2 * i] = smoothed[i]; // 实部
            fftData[2 * i + 1] = 0;       // 虚部
        }

        fft2.complexForward(fftData);

        double[] magnitude = new double[N];
        for (int i = 0; i < N; i++) {
            double re = fftData[2 * i];
            double im = fftData[2 * i + 1];
            magnitude[i] = Math.sqrt(re * re + im * im);
        }

        // Step 7: 寻找最大峰值
        int maxIdx = 0;
        for (int i = 1; i < N; i++) {
            if (magnitude[i] > magnitude[maxIdx]) maxIdx = i;
        }
        float step = 1.0f / N;  // 频率范围为 1.0 MHz，总点数为 N
        float peakFreqMHz = 0.8f + maxIdx * step;
        appendMessage(String.format("Peak Frequency: %.3f MHz\n", peakFreqMHz));

        // 👉 更新到 dataView 中
        runOnUiThread(() -> dataView.setText(String.format("%.3f MHz", peakFreqMHz)));
        runOnUiThread(() -> {
            String state;
            int color;
            if (peakFreqMHz >= 0.8f && peakFreqMHz < 1.1f) {
                state = "FULL";
                color = Color.RED;
            } else if (peakFreqMHz >= 1.1f && peakFreqMHz < 1.2f) {
                state = "NORMAL";
                color = Color.rgb(255, 165, 0); // 橙色
            } else if (peakFreqMHz >= 1.2f && peakFreqMHz <= 1.9f) {
                state = "EMPTY";
                color = Color.GREEN;
            } else {
                state = "UNKNOWN";
                color = Color.GRAY;
            }
            volumeView.setText(state);
            volumeView.setTextColor(color);
            showAlarm();
        });

        // 绘制 M data 图表
        drawSingleFrequencyPoint(peakFreqMHz);

        // Step 8: 写入 CSV 文件
        try {
            File dir = new File(requireContext().getExternalFilesDir(null), "data_logs");
            if (!dir.exists()) dir.mkdirs();

            // 格式化时间和频率
            @SuppressLint("SimpleDateFormat")
            String timestamp = new java.text.SimpleDateFormat("MMdd_HHmmss").format(new java.util.Date());
            String filename = String.format("%s_%.3fMHz.csv", timestamp, peakFreqMHz);

            File file = new File(dir, filename);
            FileWriter fw = new FileWriter(file);

            // 写入 data_ref
            fw.append("data_ref");
            for (float v : latestDataRef) fw.append(",").append(String.valueOf(v));
            fw.append("\n");

            // 写入所有 data_raw
            for (Map.Entry<String, List<Float>> entry : latestDataRawGroups.entrySet()) {
                fw.append(entry.getKey());
                for (float v : entry.getValue()) fw.append(",").append(String.valueOf(v));
                fw.append("\n");
            }

            // 写入滤波后的 weightedAvg
            fw.append("filtered");
            for (double v : weightedAvg) fw.append(",").append(String.valueOf(v));
            fw.append("\n");

            fw.flush();
            fw.close();

            appendMessage("Saved to: " + filename + "\n");
        } catch (IOException e) {
            appendMessage("Write to CSV error: " + e.getMessage() + "\n");
        }

        // 更新sample data chart
        // convert weightedAvg to List<Double>
        List<Double> spectrumList = new ArrayList<>();
        for (double v : magnitude) spectrumList.add(v);
        // 更新 ViewModel
        PlotDataViewModel viewModel = new ViewModelProvider(requireActivity()).get(PlotDataViewModel.class);
        viewModel.update(latestDataRef, latestDataRawGroups, spectrumList);

    }

    @SuppressLint("DefaultLocale")
    private void updatePlot(PlotDataViewModel viewModel) {
        List<Float> ref = viewModel.getDataRef().getValue();
        Map<String, List<Float>> raw = viewModel.getDataRaw().getValue();
        List<Double> filtered = viewModel.getMagnitude().getValue();

        if (ref == null || raw == null || filtered == null) return;

        List<ILineDataSet> sets = new ArrayList<>();

        int N = ref.size();
        float startFreq = 0.8f;
        float stepFreq = 1.0f / N;

        int[] colors = {Color.BLUE, Color.MAGENTA, Color.GREEN, Color.CYAN, Color.LTGRAY};
        int idx = 0;

        // 绘制 data_raw - data_ref 差值
        for (Map.Entry<String, List<Float>> entry : raw.entrySet()) {
            List<Entry> entries = new ArrayList<>();
            List<Float> values = entry.getValue();
            for (int i = 0; i < Math.min(values.size(), ref.size()); i++) {
                float diff = values.get(i) - ref.get(i);
                entries.add(new Entry(startFreq + i * stepFreq, diff));
            }
            LineDataSet diffSet = new LineDataSet(entries, entry.getKey());  // ✅ 只保留 t0/t1...
            diffSet.setColor(colors[idx % colors.length]);
            diffSet.setDrawCircles(false);
            sets.add(diffSet);
            idx++;
        }

        // 绘制 filtered 曲线
        List<Entry> filtEntries = new ArrayList<>();
        for (int i = 0; i < filtered.size(); i++) {
            filtEntries.add(new Entry(startFreq + i * stepFreq, filtered.get(i).floatValue()));
        }
        LineDataSet filtSet = new LineDataSet(filtEntries, "Processed data");
        filtSet.setColor(Color.RED);
        filtSet.setLineWidth(2f);
        filtSet.setDrawCircles(false);
        sets.add(filtSet);

        // 主峰点标记（不显示图例），使用 latestPeakFreq，仅当频率在 [0.8, 1.8] MHz 时绘制
        if (latestPeakFreq >= 0.8f && latestPeakFreq <= 1.8f) {
            int peakIdx = Math.round((latestPeakFreq - startFreq) / stepFreq);
            if (peakIdx >= 0 && peakIdx < filtered.size()) {
                float peakValue = filtered.get(peakIdx).floatValue();
                Entry peakEntry = new Entry(latestPeakFreq, peakValue);
                LineDataSet peakSet = new LineDataSet(Collections.singletonList(peakEntry), "");
                peakSet.setColor(Color.MAGENTA);
                peakSet.setCircleColor(Color.MAGENTA);
                peakSet.setCircleRadius(5f);
                peakSet.setDrawCircles(true);
                peakSet.setDrawValues(false);
                peakSet.setDrawHighlightIndicators(true);
                peakSet.setHighlightLineWidth(1.5f);
                peakSet.setHighlightEnabled(true);
                peakSet.setDrawIcons(false);
                peakSet.setDrawHorizontalHighlightIndicator(true);
                peakSet.setDrawVerticalHighlightIndicator(true);
                peakSet.setForm(Legend.LegendForm.NONE);
                sets.add(peakSet);
            }
        }

        settingsChart.setData(new LineData(sets));
        settingsChart.invalidate();
    }


    private void observeLivePlotData() {
        PlotDataViewModel viewModel = new ViewModelProvider(requireActivity()).get(PlotDataViewModel.class);

        viewModel.getDataRef().observe(getViewLifecycleOwner(), ref -> updatePlot(viewModel));
        viewModel.getDataRaw().observe(getViewLifecycleOwner(), raw -> updatePlot(viewModel));
        viewModel.getMagnitude().observe(getViewLifecycleOwner(), filtered -> updatePlot(viewModel));
    }


    private void showAlarm() {
        if (hasAlerted) return;
        String status = volumeView.getText().toString();
        if (!"FULL".equalsIgnoreCase(status)) return;
        hasAlerted = true;
        try {
            MediaPlayer mediaPlayer = MediaPlayer.create(requireContext(), Settings.System.DEFAULT_NOTIFICATION_URI);
            if (mediaPlayer != null) {
                mediaPlayer.setOnCompletionListener(MediaPlayer::release);
                mediaPlayer.start();
            }
        } catch (Exception ignored) {}
        Vibrator vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(500);
            }
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Alarm notification")
                .setMessage("Bladder status: FULL\nYou may need to urinate soon.")
                .setPositiveButton("Confirm", (dialog, which) -> hasAlerted = false)
                .show();
    }


    private void appendMessage(String msg) {
        runOnUiThread(() -> {
            messageView.append(msg);
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void runOnUiThread(Runnable task) {
        requireActivity().runOnUiThread(task);
    }


    private void drawSingleFrequencyPoint(float peakFrequencyMHz) {
        entries.add(new Entry(entries.size(), peakFrequencyMHz)); // X轴为编号，Y轴为频率
        entries.sort((a, b) -> Float.compare(a.getX(), b.getX()));
        dataSet.setValues(entries);

        runOnUiThread(() -> {
            dataSet.notifyDataSetChanged();
            lineData.notifyDataChanged();
            lineChart.notifyDataSetChanged();
            lineChart.moveViewToX(entries.size());  // 👈 自动移动到最新X位置
            lineChart.invalidate();
        });
    }

    private void setupChart() {
        lineChart.getDescription().setEnabled(false);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setDrawGridLines(false);  // ❌ 关闭横轴网格线
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextSize(8f);
        xAxis.setAxisMinimum(0f); // 起始点
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setDrawGridLines(false);  // ❌ 关闭纵轴网格线
        leftAxis.setTextSize(12f);
        leftAxis.setAxisMinimum(0.8f);
        leftAxis.setAxisMaximum(1.9f);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @SuppressLint("DefaultLocale")
            @Override
            public String getFormattedValue(float value) {
                return String.format("%.1f MHz", value);
            }
        });

        lineChart.getAxisRight().setEnabled(false);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setPinchZoom(true);

        dataSet = new LineDataSet(entries, "Sensor data");
        styleLineDataSet(dataSet);
        lineData = new LineData(dataSet);
        lineChart.setData(lineData);

        // 点击图点，显示其坐标
        lineChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                float x = e.getX();
                float y = e.getY();
                Toast.makeText(requireContext(),
                        String.format("Index: %d, Freq: %.2f MHz", (int) x, y),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNothingSelected() {
                // 可留空
            }
        });
    }

    private void styleLineDataSet(LineDataSet set) {
        set.setColor(Color.BLACK);
        set.setCircleColor(Color.RED);
        set.setLineWidth(2f);
        set.setCircleRadius(3f);
        set.setDrawCircleHole(false);
        set.setDrawValues(false);
    }

    private void setupSettingsChart() {
        settingsChart.setDescription(null);

        XAxis xAxis = settingsChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(0.1f); // 最小间隔
        xAxis.setGranularityEnabled(true);
        xAxis.setTextSize(10f);
        // xAxis.setLabelRotationAngle(-45); // 可选：旋转标签避免重叠

        // 设置横坐标显示为 MHz
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format("%.2f MHz", value);
            }
        });

        settingsChart.getAxisLeft().setDrawGridLines(false);
        settingsChart.getAxisRight().setEnabled(false);
        settingsChart.setTouchEnabled(true);
        settingsChart.setDragEnabled(true);
        settingsChart.setScaleEnabled(true);
    }



    private double[] simpleGaussianSmooth(double[] data, int windowSize) {
        int N = data.length;
        double[] out = new double[N];
        int radius = windowSize / 2;
        double sigma = windowSize / 3.0;
        double sigma2 = 2 * sigma * sigma;
        double[] kernel = new double[windowSize];
        double sum = 0;

        for (int i = 0; i < windowSize; i++) {
            int x = i - radius;
            kernel[i] = Math.exp(-x * x / sigma2);
            sum += kernel[i];
        }
        for (int i = 0; i < windowSize; i++) kernel[i] /= sum;

        for (int i = 0; i < N; i++) {
            double val = 0;
            for (int j = 0; j < windowSize; j++) {
                int idx = i + j - radius;
                if (idx >= 0 && idx < N) {
                    val += kernel[j] * data[idx];
                }
            }
            out[i] = val;
        }
        return out;
    }


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
