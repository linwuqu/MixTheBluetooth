package com.hc.mixthebluetooth.schema.cgm;

import android.app.AlertDialog;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.tool.message.MessageSender;
import com.hc.mixthebluetooth.activity.tool.profile.UserRolePolicy;
import com.hc.mixthebluetooth.activity.tool.runtime.FragmentRuntime;

public final class CgmParameterDialog {

    private CgmParameterDialog() {
    }

    public static void show(
            @NonNull FragmentRuntime runtime,
            @NonNull UserRolePolicy rolePolicy,
            @NonNull MessageSender sender
    ) {
        View view = View.inflate(runtime.context(), R.layout.parameter_setting, null);
        EditText controlRatio = view.findViewById(R.id.control_ratio_Edit);
        EditText extractionTime = view.findViewById(R.id.extraction_time_edit);
        EditText highLevelTime = view.findViewById(R.id.high_level_edit);
        EditText voltage = view.findViewById(R.id.voltage_Edit);
        EditText detectionTime = view.findViewById(R.id.detection_time_edit);

        controlRatio.setText("60");
        boolean editable = rolePolicy.canEditParameters();
        controlRatio.setEnabled(editable);
        extractionTime.setEnabled(editable);
        highLevelTime.setEnabled(editable);
        voltage.setEnabled(editable);
        detectionTime.setEnabled(editable);

        new AlertDialog.Builder(runtime.context())
                .setTitle("Settings")
                .setIcon(R.drawable.setting)
                .setView(view)
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Submit", (dialog, which) -> {
                    CgmParameters params = new CgmParameters(
                            controlRatio.getText().toString().trim(),
                            extractionTime.getText().toString().trim(),
                            highLevelTime.getText().toString().trim(),
                            voltage.getText().toString().trim(),
                            detectionTime.getText().toString().trim()
                    );
                    sender.send(CgmCommandSet.buildParameters(params));
                })
                .create()
                .show();
    }
}
