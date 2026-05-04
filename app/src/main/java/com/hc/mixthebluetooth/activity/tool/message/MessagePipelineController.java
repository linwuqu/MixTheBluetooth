package com.hc.mixthebluetooth.activity.tool.message;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSampleRegistry;

/**
 * Reusable receive pipeline for message fragments that parse samples and update charts.
 */
public final class MessagePipelineController {

    public interface SampleListener {
        void onSample(@NonNull BluetoothSample sample);
    }

    public interface Logger {
        void warn(@NonNull String message);
    }

    private final Context context;
    private final MessageListController messageList;
    private final BluetoothSampleRegistry sampleRegistry;
    private final SampleListener sampleListener;
    private final Logger logger;

    public MessagePipelineController(
            @NonNull Context context,
            @NonNull MessageListController messageList,
            @NonNull BluetoothSampleRegistry sampleRegistry,
            @Nullable SampleListener sampleListener,
            @Nullable Logger logger
    ) {
        this.context = context;
        this.messageList = messageList;
        this.sampleRegistry = sampleRegistry;
        this.sampleListener = sampleListener;
        this.logger = logger;
    }

    // 先正确解码 再尝试解析
    public void onBtData(@Nullable DeviceModule module, @Nullable byte[] bytes) {
        // byte[] -> String (decode)
        BluetoothPayloadDecoder.Result result = BluetoothPayloadDecoder.decodeResult(context, bytes);
        if (result.isEmpty()) return;
        // String -> handleLine (parse)
        handleLine(module, result.text);
    }

    public void handleLine(@Nullable DeviceModule module, @NonNull String line) {
        // 先确保加入原始文本
        messageList.addIncomingText(line, module);

        BluetoothSample sample = sampleRegistry.parse(line);
        if (sample == null) {
            warn("Pipeline parse failed, raw: " + line);
            return;
        }

        if (sampleListener != null) {
            sampleListener.onSample(sample);
        }
    }

    private void warn(@NonNull String message) {
        if (logger != null) {
            logger.warn(message);
        }
    }
}
