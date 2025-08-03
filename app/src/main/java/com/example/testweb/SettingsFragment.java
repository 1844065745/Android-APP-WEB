package com.example.testweb;

import static com.example.testweb.R.layout;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SettingsFragment extends Fragment {
    private Button exportCsvButton;
    private Button cmdButton_2;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(layout.fragment_settings, container, false);

        exportCsvButton = view.findViewById(R.id.exportCsvButton);
        cmdButton_2 = view.findViewById(R.id.cmdButton_2);

        exportCsvButton.setOnClickListener(v -> showFileSelectionDialog());

        return view;
    }

    private void showFileSelectionDialog() {
        File dir = new File(requireContext().getExternalFilesDir(null), "data_logs");
        if (!dir.exists()) {
            showToast("没有找到保存目录");
            return;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".csv"));
        if (files == null || files.length == 0) {
            showToast("没有 CSV 文件可操作");
            return;
        }

        String[] fileNames = Arrays.stream(files).map(File::getName).toArray(String[]::new);
        boolean[] checkedItems = new boolean[fileNames.length];
        List<File> selectedFiles = new ArrayList<>();

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("选择CSV文件")
                .setMultiChoiceItems(fileNames, checkedItems, (dialog, which, isChecked) -> {
                    if (isChecked) {
                        selectedFiles.add(files[which]);
                    } else {
                        selectedFiles.remove(files[which]);
                    }
                })
                .setPositiveButton("导出", (dialog, which) -> {
                    if (selectedFiles.isEmpty()) {
                        showToast("未选择任何文件");
                    } else {
                        shareMultipleCsvFiles(selectedFiles);
                    }
                })
                .setNegativeButton("取消", null)
                .setNeutralButton("全选", (dialog, which) -> {
                    AlertDialog alert = (AlertDialog) dialog;
                    ListView listView = alert.getListView();
                    for (int i = 0; i < files.length; i++) {
                        listView.setItemChecked(i, true);
                        if (!selectedFiles.contains(files[i])) {
                            selectedFiles.add(files[i]);
                        }
                    }
                })
                .setNeutralButton("删除", (dialog, which) -> {
                    if (selectedFiles.isEmpty()) {
                        showToast("未选择任何文件");
                        return;
                    }
                    confirmDelete(selectedFiles);
                })
                .show();
    }

    private void confirmDelete(List<File> selectedFiles) {
        new AlertDialog.Builder(requireContext())
                .setTitle("确认删除")
                .setMessage("是否删除所选的 " + selectedFiles.size() + " 个文件？此操作无法撤销。")
                .setPositiveButton("删除", (dialog, which) -> {
                    int count = 0;
                    for (File f : selectedFiles) {
                        if (f.delete()) count++;
                    }
                    showToast("已删除 " + count + " 个文件");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void shareMultipleCsvFiles(List<File> files) {
        ArrayList<Uri> uris = new ArrayList<>();
        for (File file : files) {
            Uri uri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    file
            );
            uris.add(uri);
        }

        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("text/csv");
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "导出多个CSV文件"));
    }

    private void showToast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
