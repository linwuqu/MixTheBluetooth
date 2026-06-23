package com.hc.mixthebluetooth.ui.cgm;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.hc.mixthebluetooth.databinding.BottomSheetDataStreamBinding;
import com.hc.mixthebluetooth.R;

public final class DataStreamBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetDataStreamBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetDataStreamBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @NonNull
    public androidx.recyclerview.widget.RecyclerView getRecyclerView() {
        if (binding == null) {
            throw new IllegalStateException("BottomSheet not yet created");
        }
        return binding.recyclerMessage;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
