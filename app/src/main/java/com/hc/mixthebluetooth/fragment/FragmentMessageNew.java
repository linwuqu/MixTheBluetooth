package com.hc.mixthebluetooth.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.hc.basiclibrary.viewBasic.BaseFragment;
import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.databinding.FragmentMessageNewBinding;
import com.hc.mixthebluetooth.recyclerData.FragmentMessAdapter;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FragmentMessageNew extends BaseFragment<FragmentMessageNewBinding> {

    private FragmentMessAdapter mAdapter;
    private final List<FragmentMessageItem> mDataList = new ArrayList<>();
    private DeviceModule module;

    private boolean mIsRecording = false;

    // Chart owns
    private LineChart mChartOhm;
    private LineChart mChartUs;
    private LineDataSet mSetOhm;
    private LineDataSet mSetUs;
    private long mStartTimeMs;
    private static final int MAX_POINTS = 500;

    private static final Pattern EIS_PATTERN = Pattern.compile(
            "\\s*([+-]?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\s*Ω\\s*,\\s*([+-]?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\s*(?:uS|µS)\\s*",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    protected void initAll(View view, Context context) {
        initRecycler();
        initData();
        initCharts();
        initControls();
        setBottomInfo("Ready");
    }

    private void initRecycler() {
        mAdapter = new FragmentMessAdapter(getContext(), mDataList, R.layout.item_message_fragment);
        viewBinding.recyclerMessageNew.setLayoutManager(new LinearLayoutManager(getContext()));
        viewBinding.recyclerMessageNew.setAdapter(mAdapter);
    }

    private void initData() {
        subscription(
                StaticConstants.FRAGMENT_STATE_DATA,
                StaticConstants.MESSAGE_NEW_RECORD_STATE,
                StaticConstants.MESSAGE_NEW_EXPORT_RESULT
        );
    }

    private void initControls() {
        bindOnClickListener(viewBinding.btnStartRecord, viewBinding.btnStopRecord, viewBinding.btnExport);
    }

    private void initCharts() {
        mChartOhm = viewBinding.chartOhm;
        mChartUs = viewBinding.chartUs;

        mStartTimeMs = System.currentTimeMillis();

        setupChartBase(mChartOhm);
        setupChartBase(mChartUs);

        mSetOhm = createSet("阻抗 (Ω)", Color.RED);
        mSetUs = createSet("电导 (uS)", Color.BLUE);

        mChartOhm.setData(new LineData(mSetOhm));
        mChartUs.setData(new LineData(mSetUs));
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void updateState(String sign, Object o) {
        if (StaticConstants.MESSAGE_NEW_RECORD_STATE.equals(sign) && o instanceof Boolean) {
            boolean on = (Boolean) o;
            mIsRecording = on;
            viewBinding.tvRecordState.setText(on ? "Record: ON" : "Record: OFF");
            logWarn("MessageNew record state: " + (on ? "ON" : "OFF"));
            setBottomInfo(on ? "Recording started" : "Recording stopped");
            if (on) resetChartsForNewSession();
            return;
        }

        if (StaticConstants.MESSAGE_NEW_EXPORT_RESULT.equals(sign)) {
            String msg = o == null ? "Export: (empty)" : ("Export: " + o);
            toastShort(msg);
            logWarn("MessageNew export result: " + o);
            setBottomInfo(msg);
            return;
        }

        if (StaticConstants.FRAGMENT_STATE_DATA.equals(sign)) {
            handleBluetoothPayload(o);
        }
    }

    @Override
    protected void onClickView(View v) {
        if (isCheck(viewBinding.btnStartRecord)) {
            logWarn("MessageNew click: START");
            setBottomInfo("Start clicked");
            sendDataToActivity(StaticConstants.MESSAGE_NEW_CONTROL, StaticConstants.MESSAGE_NEW_CMD_START_RECORD);
            return;
        }

        if (isCheck(viewBinding.btnStopRecord)) {
            logWarn("MessageNew click: STOP");
            setBottomInfo("Stop clicked");
            sendDataToActivity(StaticConstants.MESSAGE_NEW_CONTROL, StaticConstants.MESSAGE_NEW_CMD_STOP_RECORD);
            return;
        }

        if (isCheck(viewBinding.btnExport)) {
            logWarn("MessageNew click: EXPORT");
            setBottomInfo("Export clicked");
            sendDataToActivity(StaticConstants.MESSAGE_NEW_CONTROL, StaticConstants.MESSAGE_NEW_CMD_EXPORT);
        }
    }

    private void handleBluetoothPayload(Object o) {
        if (o instanceof DeviceModule) {
            module = (DeviceModule) o;
            logWarn("MessageNew got module: " + module.getName() + " / " + module.getMac());
            return;
        }
        if (!(o instanceof Object[])) return;
        Object[] arr = (Object[]) o;
        if (arr.length < 2) return;
        if (!(arr[1] instanceof byte[])) return;

        if (arr[0] instanceof DeviceModule) module = (DeviceModule) arr[0];
        byte[] bytes = (byte[]) arr[1];

        String line = decodePayload(bytes);
        if (line.isEmpty()) return;

        mDataList.add(new FragmentMessageItem(line, null, false, module, false));
        mAdapter.notifyItemInserted(mDataList.size() - 1);
        viewBinding.recyclerMessageNew.smoothScrollToPosition(mDataList.size() - 1);

        float[] v = parseEisLine(line);
        if (v == null) {
            logWarn("MessageNew parse failed, raw: " + line);
            return;
        }

        if (!mIsRecording) return;

        appendPoint(v[0], v[1]);
        sendDataToActivity(StaticConstants.MESSAGE_NEW_SAMPLE_JSONL, buildJsonLine(line, v[0], v[1]));
    }

    // chart tool func
    private void setupChartBase(LineChart chart) {
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setDrawGridBackground(false);
        chart.getAxisRight().setEnabled(false);

        XAxis x = chart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setGranularity(1f);
        x.setDrawGridLines(false);
        x.setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

            @Override
            public String getFormattedValue(float value) {
                long t = mStartTimeMs + (long) (value * 1000);
                return fmt.format(new Date(t));
            }
        });

        YAxis y = chart.getAxisLeft();
        y.setDrawGridLines(false);
    }

    private LineDataSet createSet(String label, int color) {
        LineDataSet set = new LineDataSet(new ArrayList<>(), label);
        set.setLineWidth(1.2f);
        set.setColor(color);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        return set;
    }

    private void appendPoint(float ohm, float us) {
        if (mChartOhm == null || mChartUs == null) return;
        if (mSetOhm == null || mSetUs == null) return;

        LineData dataOhm = mChartOhm.getData();
        LineData dataUs = mChartUs.getData();
        if (dataOhm == null || dataUs == null) return;

        float x = (System.currentTimeMillis() - mStartTimeMs) / 1000f;
        dataOhm.addEntry(new Entry(x, ohm), 0);
        dataUs.addEntry(new Entry(x, us), 0);

        if (mSetOhm.getEntryCount() > MAX_POINTS) mSetOhm.removeFirst();
        if (mSetUs.getEntryCount() > MAX_POINTS) mSetUs.removeFirst();

        dataOhm.notifyDataChanged();
        dataUs.notifyDataChanged();

        mChartOhm.notifyDataSetChanged();
        mChartUs.notifyDataSetChanged();

        mChartOhm.setVisibleXRangeMaximum(60f);
        mChartUs.setVisibleXRangeMaximum(60f);

        if (mSetOhm.getEntryCount() > 0) {
            float lastX = mSetOhm.getEntryForIndex(mSetOhm.getEntryCount() - 1).getX();
            mChartOhm.moveViewToX(lastX);
            mChartUs.moveViewToX(lastX);
        }

        mChartOhm.invalidate();
        mChartUs.invalidate();
    }

    private void resetChartsForNewSession() {
        mStartTimeMs = System.currentTimeMillis();
        if (mSetOhm != null) mSetOhm.clear();
        if (mSetUs != null) mSetUs.clear();
        if (mChartOhm != null) {
            mChartOhm.notifyDataSetChanged();
            mChartOhm.invalidate();
        }
        if (mChartUs != null) {
            mChartUs.notifyDataSetChanged();
            mChartUs.invalidate();
        }
        logWarn("MessageNew charts reset");
    }

    // decode & parse tool func
    private String decodePayload(byte[] raw) {
        if (raw == null || raw.length == 0) return "";

        String code = FragmentParameter.getInstance().getCodeFormat(getContext());
        boolean hasCrLf = raw.length >= 2 && raw[raw.length - 2] == 13 && raw[raw.length - 1] == 10;

        byte[] copy = raw.clone();
        String s = Analysis.getByteToString(copy, code, false, hasCrLf);
        if (s == null) return "";

        return s.replace("\u0000", "").trim();
    }

    private @Nullable float[] parseEisLine(String line) {
        if (line == null) return null;

        int idx = line.lastIndexOf("dataString:");
        if (idx >= 0) line = line.substring(idx + "dataString:".length());

        line = line.replace("\u0000", "").trim();

        Matcher m = EIS_PATTERN.matcher(line);
        if (!m.find()) return null;

        float ohm = Float.parseFloat(m.group(1));
        float us = Float.parseFloat(m.group(2));
        return new float[]{ohm, us};
    }

    // export tool func
    private String buildJsonLine(String rawLine, float ohm, float us) {
        String mac = module != null ? module.getMac() : "";
        String name = module != null ? module.getName() : "";

        return "{"
                + "\"tMs\":" + System.currentTimeMillis()
                + ",\"mac\":\"" + escapeJson(mac) + "\""
                + ",\"name\":\"" + escapeJson(name) + "\""
                + ",\"ohm\":" + ohm
                + ",\"us\":" + us
                + ",\"raw\":\"" + escapeJson(rawLine) + "\""
                + "}";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void setBottomInfo(String text) {
        if (viewBinding == null) return;
        viewBinding.tvBottomInfo.setText(text == null ? "" : text);
    }

    @Override
    protected FragmentMessageNewBinding getViewBinding() {
        return FragmentMessageNewBinding.inflate(getLayoutInflater());
    }
}

