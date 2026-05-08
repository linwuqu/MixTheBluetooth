package com.hc.mixthebluetooth.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.single.BTPackage;
import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.activity.tool.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.BluetoothSampleParser;
import com.hc.mixthebluetooth.activity.tool.DeviceProfile;
import com.hc.mixthebluetooth.activity.tool.EisProfileNew;
import com.hc.mixthebluetooth.activity.tool.SampleConsumer;
import com.hc.mixthebluetooth.activity.tool.SampleRecorder;
import com.hc.mixthebluetooth.activity.tool.SampleRecorderImpl;
import com.hc.mixthebluetooth.databinding.FragmentMessageNewBinding;
import com.hc.mixthebluetooth.recyclerData.FragmentMessAdapter;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;

import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.HashMap;

public class FragmentMessageNew extends BTFragment<FragmentMessageNewBinding> {

    private final HashMap<Integer, Runnable> controls = new HashMap<>();
    private final HashMap<String, com.hc.mixthebluetooth.activity.tool.chart.RealtimeLineChart> charts = new HashMap<>();
    private final ArrayList<BluetoothSampleParser> parsers = new ArrayList<>();
    private final ArrayList<SampleConsumer> consumers = new ArrayList<>();
    private final ArrayList<FragmentMessageItem> messageList = new ArrayList<>();

    private FragmentMessAdapter adapter;
    private SampleRecorder recorder;
    private DeviceProfile<FragmentMessageNewBinding> profile;

    @Override
    protected void initChannels() {
        register(StaticConstants.CH_BT_DATA);
    }

    @Override
    protected void initAllImpl(View view, Context context) {
        profile = new EisProfileNew();
        profile.registerCharts(viewBinding, charts);
        parsers.addAll(profile.parsers());
        recorder = new SampleRecorderImpl();
        consumers.addAll(profile.consumers(viewBinding, recorder));

        adapter = new FragmentMessAdapter(requireContext(), messageList, R.layout.item_message_fragment);
        viewBinding.recyclerMessageNew.setLayoutManager(new LinearLayoutManager(requireContext()));
        viewBinding.recyclerMessageNew.setAdapter(adapter);

        profile.registerControls(viewBinding, controls);
        controls.put(viewBinding.btnStartRecord.getId(), this::startRecording);
        controls.put(viewBinding.btnStopRecord.getId(), this::stopRecording);
        controls.put(viewBinding.btnExport.getId(), this::exportRecording);
        bindOnClickListener(viewBinding.btnStartRecord, viewBinding.btnStopRecord, viewBinding.btnExport);

        setBottomInfo("Ready");
    }

    @SuppressLint("SetTextI18n")
    private void startRecording() {
        for (com.hc.mixthebluetooth.activity.tool.chart.RealtimeLineChart c : charts.values())
            c.reset();
        recorder.start(requireContext(), "message_new");
        viewBinding.tvRecordState.setText("Record: ON");
        setBottomInfo("Recording started");
    }

    @SuppressLint("SetTextI18n")
    private void stopRecording() {
        recorder.stop();
        viewBinding.tvRecordState.setText("Record: OFF");
        setBottomInfo("Samples: " + recorder.getSampleCount());
    }

    private void exportRecording() {
        String path = recorder.exportPath();
        toastShort(path.isEmpty() ? "Export: (empty)" : "Export: " + path);
        setBottomInfo(path);
    }

    @Override
    protected void onBtConnected(DeviceModule m) {
    }

    @Override
    protected void onBtData(BTPackage.BTData data) {
        String text = decode(data.bytes, FragmentParameter.getInstance().getCodeFormat(requireContext()));
        if (text == null || text.isEmpty()) return;

        messageList.add(new FragmentMessageItem(text, Analysis.getTime(), false, data.module, false));
        adapter.notifyItemInserted(messageList.size() - 1);
        viewBinding.recyclerMessageNew.smoothScrollToPosition(messageList.size() - 1);

        BluetoothSample sample = null;
        for (BluetoothSampleParser p : parsers) {
            sample = p.parse(text);
            if (sample != null) break;
        }

        if (sample != null) {
            for (SampleConsumer c : consumers) c.consume(sample);
        }
    }

    @Override
    protected void onClickView(View v) {
        Runnable r = controls.get(v.getId());
        if (r != null) r.run();
    }

    @Override
    protected FragmentMessageNewBinding getViewBinding() {
        return FragmentMessageNewBinding.inflate(getLayoutInflater());
    }

    private void setBottomInfo(@Nullable String text) {
        if (viewBinding != null) viewBinding.tvBottomInfo.setText(text == null ? "" : text);
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
    }
}
