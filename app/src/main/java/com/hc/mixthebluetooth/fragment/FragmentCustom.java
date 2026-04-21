package com.hc.mixthebluetooth.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.hc.basiclibrary.viewBasic.BaseFragment;
import com.hc.basiclibrary.viewBasic.manage.BaseFragmentManage;
import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.bluetoothlibrary.tootl.ModuleParameters;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.customView.ChartMarkerView;
import com.hc.mixthebluetooth.customView.CircleProgressView;
import com.hc.mixthebluetooth.databinding.FragmentCustomBinding;
import com.hc.mixthebluetooth.recyclerData.FragmentMessAdapter;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;
import com.hc.mixthebluetooth.storage.Storage;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/*
*
* 此处为第二个窗口，可用作备用窗口
* */
public class FragmentCustom extends BaseFragment<FragmentCustomBinding> {

    private FragmentMessAdapter mAdapter;

    private final List<FragmentMessageItem> mDataList = new ArrayList<>();

    private DeviceModule module;

    private FragmentParameter mFragmentParameter;

    private int mFragmentHeight;

    private BaseFragmentManage mFragmentManage;

    private Storage mStorage;
    private LineChart sodiumChart;
    private LineChart potassiumChart;
    private List<Entry> sodiumEntries = new ArrayList<>();
    private List<Entry> potassiumEntries = new ArrayList<>();
    private LineDataSet sodiumDataSet;
    private LineDataSet potassiumDataSet;
    private long startTime = 0;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private TextView tvSodiumXAxisLabel;
    private TextView tvPotassiumXAxisLabel;
    private CircleProgressView circleProgress;
    private TextView tvSodiumMax, tvSodiumMin;
    private TextView tvSodiumFluctuation;
    private TextView tvSodiumMaxTime, tvSodiumMinTime;
    private TextView tvBluetoothDeviceName;
    private TextView tvIonValue;
    private TextView tv_sweat_flow_speed_label;
    private TextView tv_sweat_flow_speed_unit;
    private TextView tv_sweat_flow_speed_value;

    // EIS 阻抗总结数据的 TextViews
    private TextView tvEisFluctuation;
    private TextView tvEisMax, tvEisMin;
    private TextView tvEisMaxTime, tvEisMinTime;

    // 用于模拟数据的变量
    private Handler simulationHandler = new Handler(Looper.getMainLooper());
    private Runnable simulationRunnable;
    private Random random = new Random();
    private boolean isSimulationRunning = false;
    // 模拟数据的当前值 (作为类的成员变量)
    private float currentOpenCircuitPotential = 0.1f;
    private float currentEisImpedance = 1000f;
    private float currentIonConcentration = 30f; // 模拟单一的钠钾离子浓度
    private float currentSweatFlowSpeed = 1.0f;
    private float currentSweatFlowRate = 5.0f;

    private ChartMarkerView sodiumMarkerView; // 现在用于OCP图表
    private ChartMarkerView potassiumMarkerView; // 现在用于EIS阻抗图表

    @Override
    protected void initAll(View view,Context context) {
        mStorage = new Storage(context);
        mFragmentParameter = FragmentParameter.getInstance();
        initData();
        initRecycler();
        initFragment();
        initView(view);
        viewBinding.customFragmentDirection.setState(true);
        viewBinding.customFragmentPullImage.setTag(R.drawable.pull_down);
        new Handler().postDelayed(this::setViewHeight,500);
    }
    @Override
    protected void updateState(String sign, Object o) {
        if (module == null &&  StaticConstants.FRAGMENT_STATE_DATA.equals(sign)) {
            module = (DeviceModule) o;
        }
        if (o instanceof Object[] && StaticConstants.FRAGMENT_STATE_DATA.equals(sign) && viewBinding.customFragmentShowReadCheck.isChecked()) {
            Object[] objects = (Object[]) o;
            if (objects.length<2) return;
            byte[] data = ((byte[]) objects[1]).clone();
            //这里可获取到data
            processBluetoothData(data);//使用蓝牙数据
            addListData(data);

            mAdapter.notifyDataSetChanged();
            viewBinding.customFragmentRecycler.smoothScrollToPosition(mDataList.size());
        }
    }

    /**
     * 根据数据末尾是否换行，来判定数据添加到{@link #mDataList}的上一个元素或重新创建一个元素
     * @param data 接收到的数据
     */
    private void addListData(byte[] data) {

        if(!ModuleParameters.isCheckNewline()){//不检查换行符
            mDataList.add(new FragmentMessageItem(Analysis.getByteToString(data,mFragmentParameter.getCodeFormat(getContext()),
                    viewBinding.customFragmentReadCheck.isChecked(), false),  null, false, module, false));
            return;
        }
        boolean newline = data[data.length-1] == 10 && data[data.length-2] == 13;
        String dataString = Analysis.getByteToString(data,mFragmentParameter.getCodeFormat(getContext()),
                viewBinding.customFragmentReadCheck.isChecked(), newline);
        if (!mDataList.isEmpty() && mDataList.get(mDataList.size()-1).isAddible()){//数组里最后一个元素没有换行符可以添加数据
            //log("数据合并一次: "+mDataList.get(mDataList.size()-1).isAddible());
            mDataList.get(mDataList.size()-1).addData(dataString,null);

            mDataList.get(mDataList.size()-1).setDataEndNewline(newline);
        }else {//数组最后一个元素有换行符且已处理，创建一个新的元素加载数据并添加至数组最后
            //log("创建一个新的Item存储: newline is "+newline);
            mDataList.add(new FragmentMessageItem(dataString,  null, false, module, false));
            mDataList.get(mDataList.size()-1).setDataEndNewline(newline);
        }
    }

    private void initView(View view) {
        // 获取LineChart实例
        sodiumChart = view.findViewById(R.id.chart_sodium);
        potassiumChart = view.findViewById(R.id.chart_potassium);
        tvSodiumXAxisLabel = view.findViewById(R.id.sodium_x_axis_label);
        tvPotassiumXAxisLabel = view.findViewById(R.id.potassium_x_axis_label);
        circleProgress = view.findViewById(R.id.circle_progress);
        tvSodiumMax = view.findViewById(R.id.tv_sodium_max);
        tvSodiumMin = view.findViewById(R.id.tv_sodium_min);
        tvSodiumFluctuation = view.findViewById(R.id.tv_sodium_fluctuation);
        tvSodiumMaxTime = view.findViewById(R.id.tv_sodium_max_time);
        tvSodiumMinTime = view.findViewById(R.id.tv_sodium_min_time);
        tvBluetoothDeviceName = view.findViewById(R.id.tv_bluetooth_device_name);
        tvIonValue = view.findViewById(R.id.tv_ion_value);
        tv_sweat_flow_speed_label = view.findViewById(R.id.tv_sweat_flow_speed_label);
        tv_sweat_flow_speed_unit = view.findViewById(R.id.tv_sweat_flow_speed_unit);
        tv_sweat_flow_speed_value = view.findViewById(R.id.tv_sweat_flow_speed_value);

        // 查找 EIS 阻抗总结数据的 TextViews
        tvEisFluctuation = view.findViewById(R.id.tv_eis_fluctuation);
        tvEisMax = view.findViewById(R.id.tv_eis_max);
        tvEisMin = view.findViewById(R.id.tv_eis_min);
        tvEisMaxTime = view.findViewById(R.id.tv_eis_max_time);
        tvEisMinTime = view.findViewById(R.id.tv_eis_min_time);

        // 设置X轴标签
        tvSodiumXAxisLabel.setText(getString(R.string.time_axis_label));
        tvPotassiumXAxisLabel.setText(getString(R.string.time_axis_label));

        // 配置钠离子图表
        setupChart(sodiumChart);

        // 配置钾离子图表
        setupChart(potassiumChart);

        // 初始化数据集
        initDataSet();

        // 设置图表初始数据
        startTime = System.currentTimeMillis();
        updateCharts();

        // 启动模拟数据
        //startSimulation();
        //使用真实数据

        // 初始化和设置MarkerView
        // 禁用阈值显示，只显示数据点信息
        sodiumMarkerView = new ChartMarkerView(getContext(), R.layout.custom_chart_marker, startTime, Float.NaN, Float.NaN); // Pass NaN to indicate no thresholds
        sodiumMarkerView.setChartView(sodiumChart);
        sodiumChart.setMarkerView(sodiumMarkerView);

        potassiumMarkerView = new ChartMarkerView(getContext(), R.layout.custom_chart_marker, startTime, Float.NaN, Float.NaN); // Pass NaN to indicate no thresholds
        potassiumMarkerView.setChartView(potassiumChart);
        potassiumChart.setMarkerView(potassiumMarkerView);
    }


    /**
     * 接收蓝牙数据并更新图表
     * @param data 通过蓝牙接收的数据
     */
    public void
    processBluetoothData(byte[] data) {
        if (data == null) {
            return; // 数据格式不正确，预期20字节
        }
        String dataString = bytesToString(data);
        log("data_byte_string:"+dataString);
        String []dataset = dataString.split(",");
        for(int i=0;i<dataset.length;i++){
            log("data_byte_string:"+dataset[i]);
        }
        // 如果有蓝牙数据传入，停止模拟
        stopSimulation();

        try {
            // 解析数据，假设顺序为：开路电位，阻抗，钠钾浓度，汗液流速，汗液流量
            // 每种数据类型为4字节浮点数
            float openCircuitPotential = Float.parseFloat(dataset[0]);
            float eisImpedance = Float.parseFloat(dataset[1]);
            float ionConcentration = Float.parseFloat(dataset[2]); // 单一的钠钾离子浓度值
            float sweatFlowSpeed = Float.parseFloat(dataset[3]);
            float sweatFlowRate = Float.parseFloat(dataset[4]);

            // 添加数据点到图表
            float timeInSeconds = (System.currentTimeMillis() - startTime) / 1000f;
            // OCP数据添加到第一个图表的数据集
            addDataPoint(sodiumEntries, sodiumDataSet, timeInSeconds, openCircuitPotential);
            // EIS阻抗数据添加到第二个图表的数据集
            addDataPoint(potassiumEntries, potassiumDataSet, timeInSeconds, eisImpedance);

            // 更新图表
            updateCharts();

            // 更新显示值 (只更新浓度、流速和流量)
            updateDisplayValue(ionConcentration, ionConcentration, sweatFlowSpeed, sweatFlowRate);

            // 更新图表下方的最大值和最小值
            updateSummaryData();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 字节数组转浮点数
     */
    private float bytesToFloat(byte[] bytes, int offset) {
        // 确保偏移量和长度不会超出数组范围
        if (offset < 0 || offset + 4 > bytes.length) {
            throw new IllegalArgumentException("Invalid offset or data length for float conversion.");
        }
        int bits = bytes[offset] & 0xFF |
                (bytes[offset + 1] & 0xFF) << 8 |
                (bytes[offset + 2] & 0xFF) << 16 |
                (bytes[offset + 3] & 0xFF) << 24;
        return Float.intBitsToFloat(bits);
    }

    /**
     * 字节数组转字符串（使用UTF-8编码）
     *
     * @param bytes 字节数组
     * @return 转换后的字符串
     */
    private String bytesToString(byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        try {
            return new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // UTF-8是标准编码，几乎不可能不支持，但仍处理异常
            throw new RuntimeException("UTF-8 encoding not supported", e);
        }
    }


    /**
     * 启动模拟数据生成
     */
    private void startSimulation() {
        if (isSimulationRunning) {
            return;
        }
        log("启用模拟数据");
        resetChartData(); // 清空现有数据
        isSimulationRunning = true;

        // 重置初始值
        currentOpenCircuitPotential = 0.1f;
        currentEisImpedance = 1000f;
        currentIonConcentration = 30f;
        currentSweatFlowSpeed = 1.0f;
        currentSweatFlowRate = 5.0f;

        simulationRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isSimulationRunning) {
                    return;
                }

                // 计算经过的时间（秒）
                float timeInSeconds = (System.currentTimeMillis() - startTime) / 1000f;

                // 生成一组5个数据点
                // 为五种类型的数据生成新的随机值
                float newOpenCircuitPotential = generateNextValue(currentOpenCircuitPotential, -200f, 400f, 10f);
                float newEisImpedance = generateNextValue(currentEisImpedance, 500f, 2000f, 50f);
                float newIonConcentration = generateNextValue(currentIonConcentration, 10f, 60f, 2f);
                float newSweatFlowSpeed = generateNextValue(currentSweatFlowSpeed, 0.1f, 5.0f, 0.1f);
                float newSweatFlowRate = generateNextValue(currentSweatFlowRate, 1.0f, 20.0f, 0.5f);

                // 添加数据点到图表
                // OCP数据添加到第一个图表的数据集
                addDataPoint(sodiumEntries, sodiumDataSet, timeInSeconds, newOpenCircuitPotential);
                // EIS阻抗数据添加到第二个图表的数据集
                addDataPoint(potassiumEntries, potassiumDataSet, timeInSeconds, newEisImpedance);

                // 更新当前值
                currentOpenCircuitPotential = newOpenCircuitPotential;
                currentEisImpedance = newEisImpedance;
                currentIonConcentration = newIonConcentration;
                currentSweatFlowSpeed = newSweatFlowSpeed;
                currentSweatFlowRate = newSweatFlowRate;

                // 计算并更新显示值（使用最后一组数据的值）
                updateDisplayValue(currentIonConcentration, currentIonConcentration, currentSweatFlowSpeed, currentSweatFlowRate);

                // 更新图表
                updateCharts();

                // 每1秒生成一组新的数据点
                simulationHandler.postDelayed(this, 1000);
            }
        };

        // 立即开始执行
        simulationHandler.post(simulationRunnable);
    }
    /**
     * 根据钠离子和钾离子的浓度计算并更新显示值
     * @param sodiumValue 钠离子浓度
     * @param potassiumValue 钾离子浓度
     * @param sweatFlowSpeed 汗液流速 (mm/s)
     * @param sweatFlowRate 汗液流量 (uL)
     */
    private void updateDisplayValue(float sodiumValue, float potassiumValue, float sweatFlowSpeed, float sweatFlowRate) {
        // 更新汗液流量显示（uL）
        // 使用接收到的sweatFlowRate值
        if (circleProgress != null) {
            circleProgress.setValueText(String.format(Locale.getDefault(), "%.1f", sweatFlowRate));
            circleProgress.setProgress(sweatFlowRate / 100f); // 假设最大值为100uL，根据实际调整
        }

        // 更新离子浓度显示 (使用接收到的单一浓度值)
        // 假设 sodiumValue 和 potassiumValue 在这里都传递的是同一个单一浓度值
        if (tvIonValue != null) {
            tvIonValue.setText(String.format(Locale.getDefault(), "%.2f", sodiumValue)); // 或者使用potassiumValue，因为它们是同一个值
        }

        // 更新汗液流速显示 (mm/s)
        // 使用接收到的sweatFlowSpeed值
        if (tv_sweat_flow_speed_value != null) {
            tv_sweat_flow_speed_value.setText(String.format(Locale.getDefault(), "%.2f", sweatFlowSpeed));
        }
    }

    /**
     * 停止模拟数据生成
     */
    private void stopSimulation() {
        isSimulationRunning = false;
        if (simulationHandler != null && simulationRunnable != null) {
            simulationHandler.removeCallbacks(simulationRunnable);
        }
    }

    /**
     * 生成下一个随机值，基于当前值和允许的波动范围
     */
    private float generateNextValue(float currentValue, float min, float max, float maxChange) {
        // 生成-1到1之间的随机值，然后乘以最大变化量
        float change = (random.nextFloat() * 2 - 1) * maxChange;

        // 计算新值
        float newValue = currentValue + change;

        // 确保新值在规定范围内
        newValue = Math.max(min, Math.min(max, newValue));

        return newValue;
    }
    /**
     * 添加数据点到数据集
     */
    private void addDataPoint(List<Entry> entries, LineDataSet dataSet, float x, float y) {
        entries.add(new Entry(x, y));

        // 如果数据点过多，移除最早的数据点以保持性能
        if (entries.size() > 100) {
            entries.remove(0);
        }

        // 更新X轴的可见范围，始终显示最新的30个数据点
        LineChart chart = dataSet == sodiumDataSet ? sodiumChart : potassiumChart;

        if (entries.size() > 1) {
            float minX = entries.get(0).getX();
            float maxX = entries.get(entries.size() - 1).getX();

            // 设置X轴范围，显示最近30秒的数据
            chart.getXAxis().setAxisMinimum(Math.max(0, maxX - 30));
            chart.getXAxis().setAxisMaximum(maxX + 2);
        } else if (!entries.isEmpty()) {
            chart.getXAxis().setAxisMinimum(0f);
            chart.getXAxis().setAxisMaximum(30f);
        }

        // 通知数据集已更改
        dataSet.notifyDataSetChanged();

        // 在添加新数据点后更新总结数据
        updateSummaryData();

        // Move the view to the latest data point
        if (!entries.isEmpty()) {
            chart.moveViewToX(entries.get(entries.size() - 1).getX());
        }
    }

    /**
     * 更新图表下方的最大值和最小值显示
     */
    private void updateSummaryData() {
        // 找到最大和最小钠离子浓度 (现在用于OCP)
        float maxOcp = 0f;
        float minOcp = Float.MAX_VALUE;
        Entry maxOcpEntry = null;
        Entry minOcpEntry = null;

        if (!sodiumEntries.isEmpty()) {
            maxOcp = sodiumEntries.get(0).getY();
            minOcp = sodiumEntries.get(0).getY();
            maxOcpEntry = sodiumEntries.get(0);
            minOcpEntry = sodiumEntries.get(0);

            for (Entry entry : sodiumEntries) {
                if (entry.getY() > maxOcp) {
                    maxOcp = entry.getY();
                    maxOcpEntry = entry;
                }
                if (entry.getY() < minOcp) {
                    minOcp = entry.getY();
                    minOcpEntry = entry;
                }
            }

            // 更新OCP图表下方的TextView
            if (tvSodiumMax != null) {
                tvSodiumMax.setText(String.format(Locale.getDefault(), "%.2f", maxOcp));
            }
            if (tvSodiumMin != null) {
                tvSodiumMin.setText(String.format(Locale.getDefault(), "%.2f", minOcp));
            }
            if (tvSodiumFluctuation != null) {
                float fluctuation = maxOcp - minOcp;
                tvSodiumFluctuation.setText(String.format(Locale.getDefault(), "%.2f", fluctuation));
            }
            if (tvSodiumMaxTime != null && maxOcpEntry != null) {
                long maxTimeMillis = startTime + (long)(maxOcpEntry.getX() * 1000);
                tvSodiumMaxTime.setText(timeFormat.format(new Date(maxTimeMillis)));
            } else if (tvSodiumMaxTime != null) {
                tvSodiumMaxTime.setText("--");
            }
            if (tvSodiumMinTime != null && minOcpEntry != null) {
                long minTimeMillis = startTime + (long)(minOcpEntry.getX() * 1000);
                tvSodiumMinTime.setText(timeFormat.format(new Date(minTimeMillis)));
            } else if (tvSodiumMinTime != null) {
                tvSodiumMinTime.setText("--");
            }

            // Highlight max and min entries on the chart
            if (maxOcpEntry != null) {
                sodiumChart.highlightValue(maxOcpEntry.getX(), maxOcpEntry.getY(), 0); // Highlight max
            }
            if (minOcpEntry != null) {
                sodiumChart.highlightValue(minOcpEntry.getX(), minOcpEntry.getY(), 0); // Highlight min
            }

        } else {
            // Handle case where there's no data yet for OCP
            if (tvSodiumMax != null) tvSodiumMax.setText("--");
            if (tvSodiumMin != null) tvSodiumMin.setText("--");
            if (tvSodiumFluctuation != null) tvSodiumFluctuation.setText("--");
            if (tvSodiumMaxTime != null) tvSodiumMaxTime.setText("--");
            if (tvSodiumMinTime != null) tvSodiumMinTime.setText("--");
        }

        // 找到最大和最小EIS阻抗
        float maxEis = 0f;
        float minEis = Float.MAX_VALUE;
        Entry maxEisEntry = null;
        Entry minEisEntry = null;

        if (!potassiumEntries.isEmpty()) { // potassiumEntries现在用于存储EIS阻抗数据
            maxEis = potassiumEntries.get(0).getY();
            minEis = potassiumEntries.get(0).getY();
            maxEisEntry = potassiumEntries.get(0);
            minEisEntry = potassiumEntries.get(0);

            for (Entry entry : potassiumEntries) {
                if (entry.getY() > maxEis) {
                    maxEis = entry.getY();
                    maxEisEntry = entry;
                }
                if (entry.getY() < minEis) {
                    minEis = entry.getY();
                    minEisEntry = entry;
                }
            }

            // 更新EIS阻抗图表下方的TextView
            if (tvEisMax != null) {
                tvEisMax.setText(String.format(Locale.getDefault(), "%.2f", maxEis));
            }
            if (tvEisMin != null) {
                tvEisMin.setText(String.format(Locale.getDefault(), "%.2f", minEis));
            }
            if (tvEisFluctuation != null) {
                float fluctuation = maxEis - minEis;
                tvEisFluctuation.setText(String.format(Locale.getDefault(), "%.2f", fluctuation));
            }
            if (tvEisMaxTime != null && maxEisEntry != null) {
                long maxTimeMillis = startTime + (long)(maxEisEntry.getX() * 1000);
                tvEisMaxTime.setText(timeFormat.format(new Date(maxTimeMillis)));
            } else if (tvEisMaxTime != null) {
                tvEisMaxTime.setText("--");
            }
            if (tvEisMinTime != null && minEisEntry != null) {
                long minTimeMillis = startTime + (long)(minEisEntry.getX() * 1000);
                tvEisMinTime.setText(timeFormat.format(new Date(minTimeMillis)));
            } else if (tvEisMinTime != null) {
                tvEisMinTime.setText("--");
            }

            // Highlight max and min entries on the chart
            if (maxEisEntry != null) {
                potassiumChart.highlightValue(maxEisEntry.getX(), maxEisEntry.getY(), 0); // Highlight max
            }
            if (minEisEntry != null) {
                potassiumChart.highlightValue(minEisEntry.getX(), minEisEntry.getY(), 0); // Highlight min
            }

        } else {
            // Handle case where there's no data yet for EIS
            if (tvEisMax != null) tvEisMax.setText("--");
            if (tvEisMin != null) tvEisMin.setText("--");
            if (tvEisFluctuation != null) tvEisFluctuation.setText("--");
            if (tvEisMaxTime != null) tvEisMaxTime.setText("--");
            if (tvEisMinTime != null) tvEisMinTime.setText("--");
        }
    }
    /**
     * 重置图表数据
     */
    public void resetChartData() {
        sodiumEntries.clear();
        potassiumEntries.clear();
        startTime = System.currentTimeMillis();

        updateCharts();
        // 重置数据时清空总结数据
        updateSummaryData();
    }
    private void updateCharts() {
        // Update chart data
        sodiumChart.getData().notifyDataChanged();
        sodiumChart.notifyDataSetChanged();
        sodiumChart.invalidate();

        potassiumChart.getData().notifyDataChanged();
        potassiumChart.notifyDataSetChanged();
        potassiumChart.invalidate();
    }
    private void initDataSet() {
        // 初始化OCP数据集 (原来是钠离子)
        sodiumDataSet = new LineDataSet(sodiumEntries, getString(R.string.ocp_chart_title)); // 使用新的OCP标题
        sodiumDataSet.setColor(Color.BLUE);
        sodiumDataSet.setCircleColor(Color.BLUE);
        sodiumDataSet.setLineWidth(2f);
        sodiumDataSet.setCircleRadius(3f);
        sodiumDataSet.setDrawCircleHole(false);
        sodiumDataSet.setValueTextSize(9f);
        sodiumDataSet.setDrawValues(false);
        sodiumDataSet.setDrawFilled(true);
        sodiumDataSet.setFillColor(Color.BLUE); // 可以根据需要更改颜色
        sodiumDataSet.setFillAlpha(30);
        sodiumDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        sodiumDataSet.setDrawCircles(false);

        // 初始化钾离子数据集 (这个数据集将继续用于显示单一的钠钾离子浓度)
        potassiumDataSet = new LineDataSet(potassiumEntries, getString(R.string.eis_impedance_title)); // 使用新的EIS阻抗标题
        potassiumDataSet.setColor(Color.RED); // 可以根据需要更改颜色
        potassiumDataSet.setCircleColor(Color.RED);
        potassiumDataSet.setLineWidth(2f);
        potassiumDataSet.setValueTextSize(9f);
        potassiumDataSet.setDrawValues(false);
        potassiumDataSet.setDrawFilled(true);
        potassiumDataSet.setFillColor(Color.RED);
        potassiumDataSet.setFillAlpha(30);
        potassiumDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        potassiumDataSet.setDrawCircles(false);

        // Set initial data to charts - include only the main data datasets
        sodiumChart.setData(new LineData(sodiumDataSet)); // OCP图表使用sodiumDataSet
        potassiumChart.setData(new LineData(potassiumDataSet)); // 钠钾浓度图表使用potassiumDataSet
    }
    private void setupChart(LineChart chart) {
        // 启用图表交互
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setDoubleTapToZoomEnabled(true);

        // 设置背景
        chart.setBackgroundColor(Color.WHITE);
        chart.setDrawGridBackground(false);

        // 禁用描述
        chart.getDescription().setEnabled(false);

        // 设置底部边距，为标签留出空间
        chart.setExtraBottomOffset(20f);

        // 设置X轴
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(0f);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                // 将X轴的值（秒）转换为时间格式
                long elapsedTime = (long) (value * 1000); // 将秒转换为毫秒
                long currentMillis = startTime + elapsedTime;
                return timeFormat.format(new Date(currentMillis));
            }
        });
        xAxis.setLabelCount(5, false);
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(30f); // 初始显示30秒的数据范围

        // 设置Y轴
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(false);
        leftAxis.setDrawZeroLine(false); // Optional: disable zero line
        leftAxis.setDrawLimitLinesBehindData(false); // Ensure LimitLines (if any are added later) are not behind the filled area

        // Remove any existing limit lines - we are not using them for ranges now
        leftAxis.removeAllLimitLines();

        // Set Y-axis range
        if (chart == sodiumChart) { // 这个图表现在用于OCP
            leftAxis.setAxisMinimum(-200f);
            leftAxis.setAxisMaximum(400f);
        } else if (chart == potassiumChart) { // 这个图表现在用于EIS阻抗
            // 纵坐标范围根据数据自动调整，不进行固定设置

            // 不显示纵坐标数字标签
            leftAxis.setAxisMinimum(-5f);
            leftAxis.setAxisMaximum(60f);
            //leftAxis.setDrawLabels(false);
        }

        chart.getAxisRight().setEnabled(false);

        // Set text size and color for axis labels
        xAxis.setTextSize(10f);
        xAxis.setTextColor(Color.BLACK);
        leftAxis.setTextSize(10f);
        leftAxis.setTextColor(Color.BLACK);

        // 设置图例
        Legend legend = chart.getLegend();
        legend.setForm(Legend.LegendForm.LINE);
        legend.setTextSize(12f);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);

        // 没有数据时的文本
        chart.setNoDataText(getString(R.string.no_data));
    }
    private void initRecycler(){
        mAdapter = new FragmentMessAdapter(getContext(),mDataList,R.layout.item_message_fragment);
        viewBinding.customFragmentRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        viewBinding.customFragmentRecycler.setAdapter(mAdapter);
        viewBinding.customFragmentShowReadCheck.setChecked(mStorage.getDataCheckState());
    }

    private void initFragment() {
        mFragmentManage = new BaseFragmentManage(R.id.custom_fragment,getActivity());
        mFragmentManage.addFragment(0,new FragmentCustomGroup());
        mFragmentManage.addFragment(1,new FragmentCustomDirection());
        mFragmentManage.showFragment(1);
    }

    private void setViewHeight() {//动态设置fragment的高度
        viewBinding.customFragment.post(() -> {
            mFragmentHeight = viewBinding.customFragment.getHeight();
            ViewGroup.LayoutParams params=viewBinding.customFragment.getLayoutParams();
            params.height= mFragmentHeight;
            logError("height is "+mFragmentHeight);
            viewBinding.customFragment.setLayoutParams(params);
        });
    }


    @SuppressLint("NotifyDataSetChanged")
    public void onClickView(View view){
        if (isCheck(viewBinding.customFragmentPullImage)) setViewAnimation();
        if (isCheck(viewBinding.customFragmentShowReadCheck) || isCheck(viewBinding.customFragmentShowReadText)){
            viewBinding.customFragmentShowReadCheck.toggle();
            mStorage.saveCheckShowDataState(viewBinding.customFragmentNewlineCheck.isChecked());
        }else if (isCheck(viewBinding.customFragmentGroup)){
            viewBinding.customFragmentGroup.setState(true);
            viewBinding.customFragmentDirection.setState(false);
            mFragmentManage.showFragment(0);
        }else if (isCheck(viewBinding.customFragmentDirection)){
            viewBinding.customFragmentGroup.setState(false);
            viewBinding.customFragmentDirection.setState(true);
            mFragmentManage.showFragment(1);
        }else if (isCheck(viewBinding.customFragmentReadCheck) || isCheck(viewBinding.customFragmentReadHex)){
            viewBinding.customFragmentReadCheck.toggle();
        }else if (isCheck(viewBinding.customFragmentNewlineCheck) || isCheck(viewBinding.customFragmentNewlineText)){
            viewBinding.customFragmentNewlineCheck.toggle();
            sendDataToActivity(StaticConstants.FRAGMENT_CUSTOM_NEWLINE,viewBinding.customFragmentNewlineCheck.isChecked());
        }else if (isCheck(viewBinding.customFragmentEmpty)) {
            mDataList.clear();
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected FragmentCustomBinding getViewBinding() {
        return FragmentCustomBinding.inflate(getLayoutInflater());
    }

    private void setViewAnimation() {
        log("Tag is "+viewBinding.customFragmentPullImage.getTag()+" id is "+R.drawable.pull_down);
        if (Integer.parseInt( viewBinding.customFragmentPullImage.getTag().toString()) == R.drawable.pull_down){
            viewBinding.customFragmentPullImage.setTag(R.drawable.pull_up);
            viewBinding.customFragmentPullImage.setImageResource(R.drawable.pull_up);
            Analysis.changeViewHeightAnimatorStart(viewBinding.customFragment,mFragmentHeight,0);
        }else {
            viewBinding.customFragmentPullImage.setTag(R.drawable.pull_down);
            viewBinding.customFragmentPullImage.setImageResource(R.drawable.pull_down);
            Analysis.changeViewHeightAnimatorStart(viewBinding.customFragment,0,mFragmentHeight);
        }
    }

    private void initData() {
        View[] viewArray = {viewBinding.customFragmentPullImage,viewBinding.customFragmentShowReadCheck,viewBinding.customFragmentShowReadText,
                viewBinding.customFragmentGroup,viewBinding.customFragmentDirection,viewBinding.customFragmentReadCheck,viewBinding.customFragmentReadHex,
                viewBinding.customFragmentNewlineCheck,viewBinding.customFragmentNewlineText,viewBinding.customFragmentEmpty};
        bindOnClickListener(viewArray);
        subscription(StaticConstants.FRAGMENT_STATE_DATA);
    }
}
