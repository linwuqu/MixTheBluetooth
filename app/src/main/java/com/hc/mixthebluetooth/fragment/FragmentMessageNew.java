package com.hc.mixthebluetooth.fragment;

import android.content.Context;
import android.graphics.Color;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.single.BTPackage;
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSampleRegistry;
import com.hc.mixthebluetooth.activity.tool.chart.ChartRegistry;
import com.hc.mixthebluetooth.schema.eis.EisJsonLineBuilder;
import com.hc.mixthebluetooth.schema.eis.EisParser;
import com.hc.mixthebluetooth.schema.eis.EisSample;
import com.hc.mixthebluetooth.activity.tool.message.MessageListController;
import com.hc.mixthebluetooth.activity.tool.message.MessagePipelineController;
import com.hc.mixthebluetooth.activity.tool.chart.RealtimeLineChart;
import com.hc.mixthebluetooth.activity.tool.chart.SampleChartBinder;
import com.hc.mixthebluetooth.activity.tool.sample.SampleRecorder;
import com.hc.mixthebluetooth.databinding.FragmentMessageNewBinding;

public class FragmentMessageNew extends BTFragment<FragmentMessageNewBinding> {

    private static final String CHART_OHM = "ohm";
    private static final String CHART_US = "us";
    private static final int MAX_POINTS = 500;

    private MessageListController messageList;
    private MessagePipelineController messagePipeline;
    private SampleRecorder sampleRecorder;
    private DeviceModule module;

    private final ChartRegistry chartRegistry = new ChartRegistry();
    private final BluetoothSampleRegistry sampleRegistry = new BluetoothSampleRegistry()
            .register(new EisParser());
    private SampleChartBinder chartBinder;

    @Override
    protected void initChannels() {
        register(StaticConstants.CH_BT_DATA);
    }

    @Override
    protected void initAllImpl(View view, Context context) {
        initRecycler();
        initCharts();
        initRecorder();
        initPipeline();
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

        chartBinder = new SampleChartBinder(chartRegistry)
                .bind(EisSample.METRIC_OHM, CHART_OHM)
                .bind(EisSample.METRIC_US, CHART_US);
    }

    private void initRecorder() {
        sampleRecorder = new SampleRecorder(new SampleRecorder.Callback() {
            @Override
            public void onRecordStateChanged(boolean recording) {
                updateRecordStateView(recording);
                logWarn("MessageNew record state: " + (recording ? "ON" : "OFF"));
            }

            @Override
            public void onRecordExported(@NonNull String path) {
                showExportResult(path);
            }

            @Override
            public void onRecordError(@NonNull String message) {
                logError(message);
                setBottomInfo(message);
            }
        });
    }

    private void initPipeline() {
        messagePipeline = new MessagePipelineController(
                requireContext(),
                messageList,
                sampleRegistry,
                this::consumeSampleWhenRecording,
                this::logWarn
        );
    }

    // ------------------- 临时工具 -------------------
    private void updateCurrentModule(DeviceModule module) {
        this.module = module;
    }

    private void recordSample(BluetoothSample sample) {
        if (sampleRecorder == null || !sampleRecorder.isRecording()) return;

        if (!(sample instanceof EisSample)) {
            logWarn("MessageNew skip record, unsupported sample type: " + sample.type());
            return;
        }

        sampleRecorder.appendLine(EisJsonLineBuilder.build(module, (EisSample) sample));
    }

    private void updateRecordStateView(boolean recording) {
        viewBinding.tvRecordState.setText(recording ? "Record: ON" : "Record: OFF");
        setBottomInfo(recording ? "Recording started" : "Recording stopped");
    }

    private void startRecording() {
        resetChartsForNewSession();
        sampleRecorder.start(requireContext(), "message_new");
        toastShort("MessageNew record: ON");
    }

    private void stopRecording() {
        sampleRecorder.stop();
        toastShort("MessageNew record: OFF");
        logWarn("MessageNew samples: " + sampleRecorder.getSampleCount());
    }

    private void exportRecording() {
        sampleRecorder.exportPath();
    }

    private void showExportResult(@Nullable String path) {
        String msg = path == null || path.isEmpty() ? "Export: (empty)" : ("Export: " + path);
        toastShort(msg);
        logWarn("MessageNew export result: " + path);
        setBottomInfo(msg);
    }

    private void consumeSampleWhenRecording(BluetoothSample sample) {
        if (sampleRecorder == null || !sampleRecorder.isRecording()) {
            logWarn("MessageNew sample ignored because recording is OFF: " + sample.raw());
            return;
        }
        appendSampleToChart(sample);
        recordSample(sample);
    }

    private void appendSampleToChart(BluetoothSample sample) {
        if (chartBinder == null) return;
        chartBinder.append(sample);
    }


    // ------------------- 回调实现 -------------------
    @Override
    protected void onBtConnected(DeviceModule m) {
        updateCurrentModule(m);
        logWarn("MessageNew got module: " + m.getName() + " / " + m.getMac());
    }

    @Override
    protected void onBtData(BTPackage.BTData data) {
        updateCurrentModule(data.module);

        if (messagePipeline != null) {
            messagePipeline.onBtData(data.module, data.bytes);
        }
    }

    @Override
    protected void onClickView(View v) {
        if (isCheck(viewBinding.btnStartRecord)) {
            logWarn("MessageNew click: START");
            setBottomInfo("Start clicked");
            startRecording();
            return;
        }

        if (isCheck(viewBinding.btnStopRecord)) {
            logWarn("MessageNew click: STOP");
            setBottomInfo("Stop clicked");
            stopRecording();
            return;
        }

        if (isCheck(viewBinding.btnExport)) {
            logWarn("MessageNew click: EXPORT");
            setBottomInfo("Export clicked");
            exportRecording();
        }
    }

    @Override
    protected FragmentMessageNewBinding getViewBinding() {
        return FragmentMessageNewBinding.inflate(getLayoutInflater());
    }

    private void resetChartsForNewSession() {
        chartRegistry.resetAll();
        logWarn("MessageNew charts reset");
    }

    private void setBottomInfo(String text) {
        if (viewBinding == null) return;
        viewBinding.tvBottomInfo.setText(text == null ? "" : text);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sampleRecorder != null) {
            sampleRecorder.release();
        }
    }
}
