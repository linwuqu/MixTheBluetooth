package com.hc.mixthebluetooth.fragment;

import android.content.Context;
import android.graphics.Color;
import android.view.View;

import androidx.annotation.Nullable;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.single.BTPackage;
import com.hc.mixthebluetooth.activity.single.MessageNewCmd;
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.activity.tool.BluetoothPayloadDecoder;
import com.hc.mixthebluetooth.activity.tool.ChartRegistry;
import com.hc.mixthebluetooth.activity.tool.EisJsonLineBuilder;
import com.hc.mixthebluetooth.activity.tool.EisParser;
import com.hc.mixthebluetooth.activity.tool.EisSample;
import com.hc.mixthebluetooth.activity.tool.MessageListController;
import com.hc.mixthebluetooth.activity.tool.RealtimeLineChart;
import com.hc.mixthebluetooth.databinding.FragmentMessageNewBinding;

public class FragmentMessageNew extends BTFragment<FragmentMessageNewBinding> {

    private MessageListController messageList;
    private DeviceModule module;

    private boolean mIsRecording = false;

    private final ChartRegistry chartRegistry = new ChartRegistry();
    private static final String CHART_OHM = "ohm";
    private static final String CHART_US = "us";
    private static final int MAX_POINTS = 500;


    @Override
    protected void initChannels() {
        register(StaticConstants.CH_BT_DATA, StaticConstants.CH_REC_STATE, StaticConstants.CH_REC_EXPORT_RESULT);
    }

    @Override
    protected void initAllImpl(View view, Context context) {
        initRecycler();
        initCharts();
        initControls();
        setBottomInfo("Ready");
    }

    private void initRecycler() {
        messageList = new MessageListController(
                requireContext(),
                viewBinding.recyclerMessageNew,
                R.layout.item_message_fragment
        );
    }

    private void initControls() {
        bindOnClickListener(viewBinding.btnStartRecord, viewBinding.btnStopRecord, viewBinding.btnExport);
    }

    private void initCharts() {
        chartRegistry
                .register(
                        CHART_OHM,
                        viewBinding.chartOhm,
                        new RealtimeLineChart.Config.Builder()
                                .label("阻抗 (Ω)")
                                .color(Color.RED)
                                .maxPoints(MAX_POINTS)
                                .visibleWindowSeconds(60f)
                                .build()
                )
                .register(
                        CHART_US,
                        viewBinding.chartUs,
                        new RealtimeLineChart.Config.Builder()
                                .label("电导 (uS)")
                                .color(Color.BLUE)
                                .maxPoints(MAX_POINTS)
                                .visibleWindowSeconds(60f)
                                .build()
                );
    }

    // ------------------- 临时工具 -------------------
    private boolean isRecording() {
        return mIsRecording;
    }

    private void updateCurrentModule(DeviceModule module) {
        this.module = module;
    }

    private void recordSample(EisSample sample) {
        appendSampleToCharts(sample);

        sendDataToActivity(
                StaticConstants.EV_REC_SAMPLE,
                EisJsonLineBuilder.build(module, sample)
        );
    }

    private void sendControl(String cmd) {
        sendDataToActivity(StaticConstants.CMD_MSG_NEW_CONTROL, cmd);
    }

    private void setRecording(boolean recording) {
        mIsRecording = recording;
        logWarn("MessageNew record state: " + (recording ? "ON" : "OFF"));
    }

    private void updateRecordStateView(boolean recording) {
        viewBinding.tvRecordState.setText(recording ? "Record: ON" : "Record: OFF");
        setBottomInfo(recording ? "Recording started" : "Recording stopped");
    }


    // ------------------- 回调实现 -------------------
    @Override
    protected void onRecStateChanged(boolean recording) {
        setRecording(recording);
        updateRecordStateView(recording);

        if (recording) {
            resetChartsForNewSession();
        }
    }

    @Override
    protected void onRecExportResult(@Nullable String path) {
        String msg = path == null ? "Export: (empty)" : ("Export: " + path);
        toastShort(msg);
        logWarn("MessageNew export result: " + path);
        setBottomInfo(msg);
    }

    @Override
    protected void onBtConnected(DeviceModule m) {
        updateCurrentModule(m);
        logWarn("MessageNew got module: " + m.getName() + " / " + m.getMac());
    }

    @Override
    protected void onBtData(BTPackage.BTData data) {
        updateCurrentModule(data.module);

        BluetoothPayloadDecoder.Result result = BluetoothPayloadDecoder.decodeResult(getContext(), data.bytes);
        if (result.isEmpty()) return;
        logWarn("chunk=" + result.text.replace("\r", "\\r").replace("\n", "\\n"));

        handleBluetoothLine(result.text);
    }

    @Override
    protected void onClickView(View v) {
        if (isCheck(viewBinding.btnStartRecord)) {
            logWarn("MessageNew click: START");
            setBottomInfo("Start clicked");
            sendControl(MessageNewCmd.START_RECORD);
            return;
        }

        if (isCheck(viewBinding.btnStopRecord)) {
            logWarn("MessageNew click: STOP");
            setBottomInfo("Stop clicked");
            sendControl(MessageNewCmd.STOP_RECORD);
            return;
        }

        if (isCheck(viewBinding.btnExport)) {
            logWarn("MessageNew click: EXPORT");
            setBottomInfo("Export clicked");
            sendControl(MessageNewCmd.EXPORT);
        }
    }

    @Override
    protected FragmentMessageNewBinding getViewBinding() {
        return FragmentMessageNewBinding.inflate(getLayoutInflater());
    }

    // data line func
    private void handleBluetoothLine(String line) {
        messageList.addIncomingText(line, module);

        EisSample sample = EisParser.parse(line);
        if (sample == null) {
            logWarn("MessageNew parse failed, raw: " + line);
            return;
        }

        if (isRecording()) {
            recordSample(sample);
        }
    }

    // chart func
    private void appendSampleToCharts(EisSample sample) {
        chartRegistry.append(CHART_OHM, sample.ohm);
        chartRegistry.append(CHART_US, sample.us);
    }

    private void resetChartsForNewSession() {
        chartRegistry.resetAll();
        logWarn("MessageNew charts reset");
    }

    private void setBottomInfo(String text) {
        if (viewBinding == null) return;
        viewBinding.tvBottomInfo.setText(text == null ? "" : text);
    }
}

