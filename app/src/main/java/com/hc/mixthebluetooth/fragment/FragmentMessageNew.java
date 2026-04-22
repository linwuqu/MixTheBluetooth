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
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FragmentMessageNew extends BaseFragment<FragmentMessageNewBinding> {

    private FragmentMessAdapter mAdapter;
    private final List<FragmentMessageItem> mDataList = new ArrayList<>();
    private DeviceModule module;

    // Chart owns
    private LineChart mChartOhm;
    private LineChart mChartUs;
    private LineDataSet mSetOhm;
    private LineDataSet mSetUs;
    private long mStartTimeMs;
    private static final int MAX_POINTS = 500;


    @Override
    protected void initAll(View view, Context context) {
        initRecycler();
        initCharts();
        initData();
    }

    private void initRecycler() {
        mAdapter = new FragmentMessAdapter(getContext(), mDataList, R.layout.item_message_fragment);
        viewBinding.recyclerMessageNew.setLayoutManager(new LinearLayoutManager(getContext()));
        viewBinding.recyclerMessageNew.setAdapter(mAdapter);
    }

    private void initCharts() {
        // binding
        mChartOhm = viewBinding.chartOhm;
        mChartUs = viewBinding.chartUs;
        // get current/system time
        mStartTimeMs = System.currentTimeMillis();
        // init canvas
        setupChartBase(mChartOhm);
        setupChartBase(mChartUs);
        // create dataset
        mSetOhm = createSet("阻抗 (Ω) ", Color.RED);
        mSetUs = createSet("电导 (uS) ", Color.BLUE);
        // update dataset
        mChartOhm.setData(new LineData(mSetOhm));
        mChartUs.setData(new LineData(mSetUs));
    }

    private void initData() {
        subscription(StaticConstants.FRAGMENT_STATE_DATA);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void updateState(String sign, Object o) {
        if (!StaticConstants.FRAGMENT_STATE_DATA.equals(sign) || o == null) return;

        if (o instanceof DeviceModule) {
            module = (DeviceModule) o;
            return;
        }
        if (!(o instanceof Object[])) return;
        Object[] arr = (Object[]) o;
        if (arr.length < 2) return;

        DeviceModule m = (arr[0] instanceof DeviceModule) ? (DeviceModule) arr[0] : module;
        byte[] bytes = (byte[]) arr[1];
        // decode
        String line = decodePayload(bytes);
        if (line.isEmpty()) return;

        mDataList.add(new FragmentMessageItem(line, null, false, m, false));
        mAdapter.notifyItemInserted(mDataList.size() - 1);
        // parse
        float[] v = parseEisLine(line);
        if (v != null) appendPoint(v[0], v[1]);
    }

    @Override
    protected FragmentMessageNewBinding getViewBinding() {
        return FragmentMessageNewBinding.inflate(getLayoutInflater());
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
        float x = (System.currentTimeMillis() - mStartTimeMs) / 1000f;

        LineData dataOhm = mChartOhm.getData();
        LineData dataUs = mChartUs.getData();

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
        mChartOhm.moveViewToX(dataOhm.getEntryCount());
        mChartUs.moveViewToX(dataUs.getEntryCount());
    }

    // decode & parse tool func
    private String decodePayload(byte[] raw) {
        if (raw == null || raw.length == 0) return "";

        String code = FragmentParameter.getInstance().getCodeFormat(getContext());
        boolean hasCrLf = raw.length > 2 && raw[raw.length - 2] == 13 && raw[raw.length - 1] == 10;

        byte[] copy = raw.clone();
        String s = Analysis.getByteToString(copy, code, false, hasCrLf);
        if (s == null) return "";

        s = s.replace("\u0000", "").trim();
        return s;
    }

    private @Nullable float[] parseEisLine(String line) {
        if (line == null) return null;

        int idx = line.lastIndexOf("dataString:");
        if (idx >= 0) line = line.substring(idx + "dataString:".length());

        line = line.replace("\u0000", "").trim();

        final Pattern EIS_PATTERN = Pattern.compile("\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*Ω\\s*,\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*(?:uS|µS)\\s*", Pattern.CASE_INSENSITIVE);
        Matcher m = EIS_PATTERN.matcher(line);
        if (!m.matches()) return null;

        float ohm = Float.parseFloat(m.group(1)); // 阻抗
        float us = Float.parseFloat(m.group(2)); // 电导
        return new float[]{ohm, us};
    }

}
