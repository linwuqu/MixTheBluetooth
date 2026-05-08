package com.hc.mixthebluetooth.schema.cgm;

import android.app.AlertDialog;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.tool.message.MessageSender;
import com.hc.mixthebluetooth.activity.tool.profile.UserRolePolicy;
import com.hc.mixthebluetooth.activity.tool.runtime.FragmentRuntime;

/**
 * CgmParameterDialog — CGM 设备参数设置对话框
 *
 * 作用：
 *   弹出 AlertDialog，让用户输入/查看 5 个设备参数（controlRatio / extractionTime /
 *   highLevelTime / voltage / detectionTime），点击"提交"后发送到设备。
 *
 * 用户体验设计：
 *   - 参数值是否可编辑，由 rolePolicy.canEditParameters() 决定。
 *     admin 用户可以修改，普通用户只能查看。
 *   - 点击"取消"直接关闭对话框，不发送任何命令。
 *   - 点击"提交"后才调用 sender.send()，发送参数命令。
 *
 * 弹窗布局来源：
 *   参数输入框从 R.layout.parameter_setting（XML 布局）inflate 出来。
 *
 * 依赖：
 *   runtime   — 获取 Android Context 来创建 AlertDialog
 *   rolePolicy — 判断当前用户是否有编辑权限
 *   sender    — 发送参数命令
 *
 * 典型调用链：
 *   用户点击 btnParams
 *     → ControlRegistry.dispatch(btnParams)
 *     → Lambda: CgmParameterDialog.show(runtime, rolePolicy, sender)
 *     → 显示对话框
 *     → 用户填参数，点击"Submit"
 *     → sender.send(CgmCommandSet.buildParameters(params))
 *     → MessageSender.send() → 蓝牙发送参数命令
 */
public final class CgmParameterDialog {

    private CgmParameterDialog() {}

    public static void show(
            @NonNull FragmentRuntime runtime,
            @NonNull UserRolePolicy rolePolicy,
            @NonNull MessageSender sender
    ) {
        // 从 XML 布局 inflate 出参数设置 View
        View view = View.inflate(runtime.context(), R.layout.parameter_setting, null);

        // 找到 5 个输入框
        EditText controlRatio = view.findViewById(R.id.control_ratio_Edit);
        EditText extractionTime = view.findViewById(R.id.extraction_time_edit);
        EditText highLevelTime = view.findViewById(R.id.high_level_edit);
        EditText voltage = view.findViewById(R.id.voltage_Edit);
        EditText detectionTime = view.findViewById(R.id.detection_time_edit);

        // 默认值
        controlRatio.setText("60");

        // 根据用户角色决定是否可编辑
        boolean editable = rolePolicy.canEditParameters();
        controlRatio.setEnabled(editable);
        extractionTime.setEnabled(editable);
        highLevelTime.setEnabled(editable);
        voltage.setEnabled(editable);
        detectionTime.setEnabled(editable);

        // 构建并显示对话框
        new AlertDialog.Builder(runtime.context())
                .setTitle("Settings")
                .setIcon(R.drawable.setting)
                .setView(view)
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Submit", (dialog, which) -> {
                    // 收集用户填写的 5 个参数
                    CgmParameters params = new CgmParameters(
                            controlRatio.getText().toString().trim(),
                            extractionTime.getText().toString().trim(),
                            highLevelTime.getText().toString().trim(),
                            voltage.getText().toString().trim(),
                            detectionTime.getText().toString().trim()
                    );
                    // 发送参数命令
                    sender.send(CgmCommandSet.buildParameters(params));
                })
                .create()
                .show();
    }
}
