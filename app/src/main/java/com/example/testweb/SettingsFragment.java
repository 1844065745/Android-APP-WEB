// SettingsFragment.java
package com.example.testweb;

import static com.example.testweb.R.*;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {
    private Button cmdButton_1;
    private Button cmdButton_2;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(layout.fragment_settings, container, false);

        cmdButton_1 = view.findViewById(R.id.cmdButton_1);
        cmdButton_2 = view.findViewById(R.id.cmdButton_2);



        return view;
    }
}