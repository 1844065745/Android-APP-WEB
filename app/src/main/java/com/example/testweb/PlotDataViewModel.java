package com.example.testweb;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PlotDataViewModel extends ViewModel {
    private final MutableLiveData<List<Float>> dataRef = new MutableLiveData<>();
    private final MutableLiveData<Map<String, List<Float>>> dataRaw = new MutableLiveData<>();
    private final MutableLiveData<List<Double>> magnitude = new MutableLiveData<>();

    public void update(List<Float> ref, Map<String, List<Float>> raw, List<Double> magnitudeData) {
        dataRef.postValue(ref);
        dataRaw.postValue(raw);
        magnitude.postValue(magnitudeData);
    }

    public LiveData<List<Float>> getDataRef() {
        return dataRef;
    }

    public LiveData<Map<String, List<Float>>> getDataRaw() {
        return dataRaw;
    }

    public LiveData<List<Double>> getMagnitude() {
        return magnitude;
    }
}
