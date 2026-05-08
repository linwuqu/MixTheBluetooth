package com.hc.mixthebluetooth.fragment;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.single.BTPackage;
import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.activity.single.HoldBluetooth;
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.activity.tool.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.BluetoothSampleParser;
import com.hc.mixthebluetooth.activity.tool.DeviceProfile;
import com.hc.mixthebluetooth.activity.tool.EisProfile;
import com.hc.mixthebluetooth.activity.tool.SampleConsumer;
import com.hc.mixthebluetooth.activity.tool.SampleRecorder;
import com.hc.mixthebluetooth.activity.tool.SampleRecorderImpl;
import com.hc.mixthebluetooth.activity.tool.chart.RealtimeLineChart;
import com.hc.mixthebluetooth.databinding.FragmentMessageBinding;
import com.hc.mixthebluetooth.recyclerData.FragmentMessAdapter;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FragmentMessage extends BTFragment<FragmentMessageBinding> {

    private static final int AUTO_CLEAR_BYTES = 400_000;

    private final HashMap<Integer, Runnable> controls = new HashMap<>();
    private final HashMap<String, RealtimeLineChart> charts = new HashMap<>();
    private final ArrayList<BluetoothSampleParser> parsers = new ArrayList<>();
    private final ArrayList<SampleConsumer> consumers = new ArrayList<>();
    private final ArrayList<FragmentMessageItem> messageList = new ArrayList<>();
    private final List<SampleConsumer> chartConsumers = new ArrayList<>();

    private FragmentMessAdapter adapter;
    private SampleRecorder recorder;
    private DeviceProfile<FragmentMessageBinding> profile;
    private DeviceModule module;
    private boolean connected;
    private int readBytes, sentBytes;

    // ── Lifecycle ────────────────────────────────────────────────

    @Override
    protected void initChannels() {
        register(StaticConstants.CH_BT_DATA);
    }

    @Override
    protected void initAllImpl(View view, Context context) {
        profile = new EisProfile();
        profile.registerCharts(viewBinding, charts);
        parsers.addAll(profile.parsers());
        recorder = new SampleRecorderImpl();
        consumers.addAll(profile.consumers(viewBinding, recorder));

        chartConsumers.add(new SampleConsumer() {
            private final SampleRecorder gate = recorder;
            @Override
            public void consume(@NonNull BluetoothSample s) {
                if (!gate.isRecording()) return;
                for (Map.Entry<String, Float> e : s.metrics().entrySet()) {
                    RealtimeLineChart c = charts.get(e.getKey());
                    if (c != null) c.append(e.getValue());
                }
            }
        });

        adapter = new FragmentMessAdapter(requireContext(), messageList, R.layout.item_message_fragment);
        viewBinding.recyclerMessage.setLayoutManager(new LinearLayoutManager(requireContext()));
        viewBinding.recyclerMessage.setAdapter(adapter);

        setupControls();
        setBottomInfo("Ready");
        updateByteCounters();
    }

    private void setupControls() {
        profile.registerControls(viewBinding, controls);

        controls.put(viewBinding.btnStartRecord.getId(), this::startRecording);
        controls.put(viewBinding.btnStopRecord.getId(), this::stopRecording);
        controls.put(viewBinding.btnExport.getId(), this::exportRecording);
        controls.put(viewBinding.btnOptions.getId(), this::refreshOptions);

        bindOnClickListener(
                viewBinding.btnStartMeasure,
                viewBinding.btnReadCache,
                viewBinding.btnDeleteCache,
                viewBinding.btnParams,
                viewBinding.btnStartRecord,
                viewBinding.btnStopRecord,
                viewBinding.btnExport,
                viewBinding.btnOptions
        );
    }

    // ── Recording ────────────────────────────────────────────────

    private void startRecording() {
        for (RealtimeLineChart c : charts.values()) c.reset();
        recorder.start(requireContext(), "message_cgm");
        viewBinding.tvRecordState.setText("Record: ON");
        setBottomInfo("Recording started");
    }

    private void stopRecording() {
        recorder.stop();
        setBottomInfo("Samples: " + recorder.getSampleCount());
    }

    private void exportRecording() {
        String path = recorder.exportPath();
        setBottomInfo(path);
        toastShort(path);
    }

    private void refreshOptions() {
        setBottomInfo("Options refreshed");
    }

    // ── Bluetooth callbacks ───────────────────────────────────────

    @Override
    protected void onBtConnected(DeviceModule m) {
        module = m;
        connected = true;
    }

    @Override
    protected void onBtDisconnected() {
        module = null;
        connected = false;
    }

    @Override
    protected void onConnectStateChanged(String state) {
        connected = "已连接".equals(state);
    }

    @Override
    protected void onBtData(BTPackage.BTData data) {
        module = data.module;
        if (data.bytes == null) return;

        String text = decode(data.bytes, FragmentParameter.getInstance().getCodeFormat(requireContext()));
        if (text == null || text.isEmpty()) return;

        readBytes += data.bytes.length;
        updateByteCounters();

        FragmentMessageItem item = new FragmentMessageItem(text, Analysis.getTime(), false, data.module, false);
        item.setDataEndNewline(true);
        messageList.add(item);
        adapter.notifyItemInserted(messageList.size() - 1);
        viewBinding.recyclerMessage.smoothScrollToPosition(messageList.size() - 1);

        BluetoothSample sample = null;
        for (BluetoothSampleParser p : parsers) {
            sample = p.parse(text);
            if (sample != null) break;
        }

        if (sample != null) {
            for (SampleConsumer c : consumers) c.consume(sample);
            for (SampleConsumer c : chartConsumers) c.consume(sample);
        }

        if (readBytes > AUTO_CLEAR_BYTES) {
            messageList.clear();
            adapter.notifyDataSetChanged();
            readBytes = 0;
            setBottomInfo("Auto cleared");
        }
    }

    @Override
    protected void onSentBytesChanged(int number) {
        sentBytes += number;
        updateByteCounters();
    }

    @Override
    protected void onStopLoopSend() {
        HoldBluetooth.getInstance().stopSend(module, null);
    }

    // ── Button dispatch ───────────────────────────────────────────

    @Override
    protected void onClickView(View view) {
        Runnable r = controls.get(view.getId());
        if (r != null) r.run();
    }

    @Override
    protected FragmentMessageBinding getViewBinding() {
        return FragmentMessageBinding.inflate(getLayoutInflater());
    }

    // ── Helpers ───────────────────────────────────────────────────

    private void setBottomInfo(@Nullable String text) {
        if (viewBinding != null)
            viewBinding.tvBottomInfo.setText(text == null ? "" : text);
    }

    private void updateByteCounters() {
        if (viewBinding != null)
            viewBinding.tvByteCounters.setText("Read: " + readBytes + " B    Sent: " + sentBytes + " B");
    }

    @Nullable
    private String decode(@Nullable byte[] bytes, @Nullable String code) {
        if (bytes == null || bytes.length == 0) return null;
        String text = Analysis.getByteToString(bytes.clone(), code != null ? code : "UTF-8", false, false);
        if (text == null) return null;
        text = text.replace("\u0000", "").trim();
        return text.isEmpty() ? null : text;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (recorder != null) recorder.release();
        HoldBluetooth.getInstance().stopSend(module, null);
    }
}
