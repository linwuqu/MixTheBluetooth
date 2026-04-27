package com.hc.mixthebluetooth.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
import com.hc.mixthebluetooth.activity.single.BTPackage;
import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.activity.tool.ChartRegistry;
import com.hc.mixthebluetooth.activity.tool.CommunicateTool;
import com.hc.mixthebluetooth.activity.tool.RecyclerTool;
import com.hc.mixthebluetooth.customView.ChartMarkerView;
import com.hc.mixthebluetooth.customView.CircleProgressView;
import com.hc.mixthebluetooth.databinding.FragmentCustomBinding;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;
import com.hc.mixthebluetooth.storage.Storage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * FragmentCustom 重构参考实现 — 基于 BTFragment + ChartRegistry + RecyclerTool。
 *
 * init 主线（清晰明了）：
 *   initStorage()              — 持久化配置
 *   initRecycler()             — RecyclerView 初始化
 *   initCharts()              — 两个 LineChart 注册（ChartRegistry）
 *   initStats()               — 统计显示初始化
 *   initChildFragment()        — 子 Fragment 管理
 *   initControls()            — 按钮绑定
 *
 * 信道（仅1个，类型安全）：
 *   CH_BT_DATA → onBtData()  — 蓝牙数据帧推送
 *
 * 对比旧实现：
 *   - 删除了 200+ 行的 updateSummaryData()（拆分到 StatsManager）
 *   - 删除了 processBluetoothData() 中的模拟/真实数据混杂
 *   - 删除了 startSimulation() / stopSimulation() 与真实数据逻辑混杂
 *   - 删除了 addDataPoint() 中重复的 Chart 配置
 *   - Chart 配置统一由 ChartRegistry 管理
 */
public class FragmentCustom extends BTFragment<FragmentCustomBinding> {

    // ─── 工具实例 ─────────────────────────────────────────────────

    private final RecyclerTool  recycler = new RecyclerTool();
    private final ChartRegistry chartRegistry = new ChartRegistry();
    private final StatsManager  stats = new StatsManager();

    // ─── 状态 ─────────────────────────────────────────────────────

    private DeviceModule          module;
    private Storage              mStorage;
    private BaseFragmentManage   mFragmentManage;
    private int                  mFragmentHeight;
    private SimpleDateFormat     timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private long                 startTime;

    // ─── Channel 注册 ──────────────────────────────────────────────

    @Override
    protected void initChannels() {
        register(StaticConstants.CH_BT_DATA);
    }

    // ─── Init 主线 ────────────────────────────────────────────────

    @Override
    protected void initAllImpl(@NonNull View view, @NonNull Context context) {
        initStorage(context);
        initRecycler(context);
        initCharts();
        initStats();
        initChildFragment();
        initControls();
        startTime = System.currentTimeMillis();
    }

    private void initStorage(@NonNull Context ctx) {
        mStorage = new Storage(ctx);
        mFragmentParameter = FragmentParameter.getInstance();
    }

    private void initRecycler(@NonNull Context ctx) {
        recycler.init(ctx, R.layout.item_message_fragment)
                .layoutManager(RecyclerTool.Layout.LINEAR)
                .maxBytesBeforeAutoClear(0)
                .attach(viewBinding.customFragmentRecycler)
                .build();
        viewBinding.customFragmentShowReadCheck.setChecked(mStorage.getDataCheckState());
    }

    private void initCharts() {
        chartRegistry.register(viewBinding.customFragment.findViewById(R.id.chart_sodium),
                new ChartRegistry.ChartConfig.Builder()
                        .label("OCP")
                        .color(Color.BLUE)
                        .lineWidth(2f)
                        .maxPoints(100)
                        .visibleWindowSeconds(30f)
                        .yRange(-200f, 400f)
                        .xAxisFormatter(ts -> timeFormat.format(new Date(ts)))
                        .fill(Color.argb(30, 0, 0, 255), 30)
                        .build());

        chartRegistry.register(viewBinding.customFragment.findViewById(R.id.chart_potassium),
                new ChartRegistry.ChartConfig.Builder()
                        .label("EIS")
                        .color(Color.RED)
                        .lineWidth(2f)
                        .maxPoints(100)
                        .visibleWindowSeconds(30f)
                        .xAxisFormatter(ts -> timeFormat.format(new Date(ts)))
                        .fill(Color.argb(30, 255, 0, 0), 30)
                        .build());
    }

    private void initStats() {
        TextView tvSodiumXAxisLabel = viewBinding.customFragment.findViewById(R.id.sodium_x_axis_label);
        TextView tvPotassiumXAxisLabel = viewBinding.customFragment.findViewById(R.id.potassium_x_axis_label);
        if (tvSodiumXAxisLabel != null)    tvSodiumXAxisLabel.setText(getString(R.string.time_axis_label));
        if (tvPotassiumXAxisLabel != null) tvPotassiumXAxisLabel.setText(getString(R.string.time_axis_label));

        stats.bind(
                viewBinding.customFragment.findViewById(R.id.tv_sodium_max),
                viewBinding.customFragment.findViewById(R.id.tv_sodium_min),
                viewBinding.customFragment.findViewById(R.id.tv_sodium_fluctuation),
                viewBinding.customFragment.findViewById(R.id.tv_sodium_max_time),
                viewBinding.customFragment.findViewById(R.id.tv_sodium_min_time),
                viewBinding.customFragment.findViewById(R.id.tv_eis_max),
                viewBinding.customFragment.findViewById(R.id.tv_eis_min),
                viewBinding.customFragment.findViewById(R.id.tv_eis_fluctuation),
                viewBinding.customFragment.findViewById(R.id.tv_eis_max_time),
                viewBinding.customFragment.findViewById(R.id.tv_eis_min_time),
                viewBinding.customFragment.findViewById(R.id.circle_progress),
                viewBinding.customFragment.findViewById(R.id.tv_ion_value),
                viewBinding.customFragment.findViewById(R.id.tv_sweat_flow_speed_value)
        );
    }

    private void initChildFragment() {
        mFragmentManage = new BaseFragmentManage(R.id.custom_fragment, getActivity());
        mFragmentManage.addFragment(0, new FragmentCustomGroup());
        mFragmentManage.addFragment(1, new FragmentCustomDirection());
        mFragmentManage.showFragment(1);
    }

    private void initControls() {
        bindOnClickListener(
                viewBinding.customFragmentPullImage,
                viewBinding.customFragmentShowReadCheck,
                viewBinding.customFragmentShowReadText,
                viewBinding.customFragmentGroup,
                viewBinding.customFragmentDirection,
                viewBinding.customFragmentReadCheck,
                viewBinding.customFragmentReadHex,
                viewBinding.customFragmentNewlineCheck,
                viewBinding.customFragmentNewlineText,
                viewBinding.customFragmentEmpty
        );
    }

    // ─── BTFragment 类型安全回调 ──────────────────────────────────

    @Override
    protected void onBtConnected(@NonNull DeviceModule m) {
        module = m;
        TextView tv = viewBinding.customFragment.findViewById(R.id.tv_bluetooth_device_name);
        if (tv != null) tv.setText(m.getName());
    }

    @Override
    protected void onBtData(@NonNull BTPackage.BTData d) {
        // 如果 CheckBox 未勾选，不显示接收数据
        if (!viewBinding.customFragmentShowReadCheck.isChecked()) return;

        String code = FragmentParameter.getInstance().getCodeFormat(requireContext());
        String s = CommunicateTool.decode(d.bytes, code);
        if (s == null || s.isEmpty()) return;

        processBinaryFrame(d.bytes);
        recycler.addLine(s, d.bytes, CommunicateTool.endsWithNewline(d.bytes), module, false);
    }

    // ─── 业务逻辑 ─────────────────────────────────────────────────

    /**
     * 处理二进制 EIS 数据帧。
     * 每帧 20 字节 = 5 × float32：[OCP, EIS, Ion, Speed, Rate]
     */
    private void processBinaryFrame(@NonNull byte[] data) {
        float[] v = CommunicateTool.parseBinaryEisFrame(data);
        if (v[0] == 0 && v[1] == 0 && v[2] == 0) return;

        float timeInSeconds = (System.currentTimeMillis() - startTime) / 1000f;
        chartRegistry.append("OCP", System.currentTimeMillis(), v[0]);
        chartRegistry.append("EIS", System.currentTimeMillis(), v[1]);

        stats.updateOCP(v[0], timeInSeconds);
        stats.updateEIS(v[1], timeInSeconds);
        stats.updateDisplay(
                viewBinding.customFragment.findViewById(R.id.circle_progress),
                viewBinding.customFragment.findViewById(R.id.tv_ion_value),
                viewBinding.customFragment.findViewById(R.id.tv_sweat_flow_speed_value),
                v[2], v[3], v[4]
        );
    }

    // ─── 点击事件 ─────────────────────────────────────────────────

    @Override
    protected void onClickView(@NonNull View v) {
        if (isCheck(viewBinding.customFragmentPullImage)) {
            setViewAnimation();
        } else if (isCheck(viewBinding.customFragmentShowReadCheck)
                || isCheck(viewBinding.customFragmentShowReadText)) {
            viewBinding.customFragmentShowReadCheck.toggle();
            mStorage.saveCheckShowDataState(viewBinding.customFragmentNewlineCheck.isChecked());
        } else if (isCheck(viewBinding.customFragmentGroup)) {
            viewBinding.customFragmentGroup.setState(true);
            viewBinding.customFragmentDirection.setState(false);
            mFragmentManage.showFragment(0);
        } else if (isCheck(viewBinding.customFragmentDirection)) {
            viewBinding.customFragmentGroup.setState(false);
            viewBinding.customFragmentDirection.setState(true);
            mFragmentManage.showFragment(1);
        } else if (isCheck(viewBinding.customFragmentReadCheck)
                || isCheck(viewBinding.customFragmentReadHex)) {
            viewBinding.customFragmentReadCheck.toggle();
        } else if (isCheck(viewBinding.customFragmentNewlineCheck)
                || isCheck(viewBinding.customFragmentNewlineText)) {
            viewBinding.customFragmentNewlineCheck.toggle();
            sendDataToActivity(StaticConstants.EV_CUSTOM_NEWLINE,
                    viewBinding.customFragmentNewlineCheck.isChecked());
        } else if (isCheck(viewBinding.customFragmentEmpty)) {
            recycler.clear();
        }
    }

    // ─── 动画 ────────────────────────────────────────────────────

    private void setViewAnimation() {
        int tag = (int) viewBinding.customFragmentPullImage.getTag();
        if (tag == R.drawable.pull_down) {
            viewBinding.customFragmentPullImage.setTag(R.drawable.pull_up);
            viewBinding.customFragmentPullImage.setImageResource(R.drawable.pull_up);
            Analysis.changeViewHeightAnimatorStart(viewBinding.customFragment, mFragmentHeight, 0);
        } else {
            viewBinding.customFragmentPullImage.setTag(R.drawable.pull_down);
            viewBinding.customFragmentPullImage.setImageResource(R.drawable.pull_down);
            Analysis.changeViewHeightAnimatorStart(viewBinding.customFragment, 0, mFragmentHeight);
        }
    }

    private void setViewHeight() {
        viewBinding.customFragment.post(() -> {
            mFragmentHeight = viewBinding.customFragment.getHeight();
            ViewGroup.LayoutParams params = viewBinding.customFragment.getLayoutParams();
            params.height = mFragmentHeight;
            viewBinding.customFragment.setLayoutParams(params);
        });
    }

    // ─── 统计管理器 ──────────────────────────────────────────────

    /**
     * 统计管理器 — 替代原来 FragmentCustom 中 200+ 行的 updateSummaryData()。
     *
     * 职责：
     * - 维护 OCP 和 EIS 两组数据的最值、极值时间、波动
     * - 更新对应的 TextView 显示
     * - 支持自动重置
     *
     * 使用方式：bind() → updateOCP() / updateEIS() → updateDisplay()
     */
    private static class StatsManager {
        // OCP 统计
        private float  ocpMax = -Float.MAX_VALUE, ocpMin = Float.MAX_VALUE;
        private long   ocpMaxTime, ocpMinTime;
        // EIS 统计
        private float  eisMax = -Float.MAX_VALUE, eisMin = Float.MAX_VALUE;
        private long   eisMaxTime, eisMinTime;

        // 绑定的 View 引用
        private TextView tvOcpMax, tvOcpMin, tvOcpFluctuation, tvOcpMaxTime, tvOcpMinTime;
        private TextView tvEisMax, tvEisMin, tvEisFluctuation, tvEisMaxTime, tvEisMinTime;
        private CircleProgressView circleProgress;
        private TextView tvIonValue, tvSweatFlowSpeed;

        public void bind(View... views) {
            for (View v : views) {
                if (v == null) continue;
                switch (v.getId()) {
                    case R.id.tv_sodium_max:           tvOcpMax        = (TextView) v; break;
                    case R.id.tv_sodium_min:           tvOcpMin        = (TextView) v; break;
                    case R.id.tv_sodium_fluctuation:   tvOcpFluctuation= (TextView) v; break;
                    case R.id.tv_sodium_max_time:      tvOcpMaxTime    = (TextView) v; break;
                    case R.id.tv_sodium_min_time:      tvOcpMinTime    = (TextView) v; break;
                    case R.id.tv_eis_max:              tvEisMax        = (TextView) v; break;
                    case R.id.tv_eis_min:              tvEisMin        = (TextView) v; break;
                    case R.id.tv_eis_fluctuation:      tvEisFluctuation= (TextView) v; break;
                    case R.id.tv_eis_max_time:         tvEisMaxTime    = (TextView) v; break;
                    case R.id.tv_eis_min_time:         tvEisMinTime    = (TextView) v; break;
                    case R.id.circle_progress:         circleProgress   = (CircleProgressView) v; break;
                    case R.id.tv_ion_value:           tvIonValue       = (TextView) v; break;
                    case R.id.tv_sweat_flow_speed_value: tvSweatFlowSpeed = (TextView) v; break;
                }
            }
        }

        public void updateOCP(float value, float timeInSeconds) {
            long absTime = System.currentTimeMillis() - startTime + (long) (timeInSeconds * 1000);
            if (value > ocpMax) { ocpMax = value; ocpMaxTime = absTime; }
            if (value < ocpMin) { ocpMin = value; ocpMinTime = absTime; }
            if (tvOcpMax != null)         tvOcpMax.setText(String.format(Locale.getDefault(), "%.2f", ocpMax));
            if (tvOcpMin != null)         tvOcpMin.setText(String.format(Locale.getDefault(), "%.2f", ocpMin));
            if (tvOcpFluctuation != null) tvOcpFluctuation.setText(String.format(Locale.getDefault(), "%.2f", ocpMax - ocpMin));
            if (tvOcpMaxTime != null)     tvOcpMaxTime.setText(formatTime(ocpMaxTime));
            if (tvOcpMinTime != null)     tvOcpMinTime.setText(formatTime(ocpMinTime));
        }

        public void updateEIS(float value, float timeInSeconds) {
            long absTime = System.currentTimeMillis() - startTime + (long) (timeInSeconds * 1000);
            if (value > eisMax) { eisMax = value; eisMaxTime = absTime; }
            if (value < eisMin) { eisMin = value; eisMinTime = absTime; }
            if (tvEisMax != null)         tvEisMax.setText(String.format(Locale.getDefault(), "%.2f", eisMax));
            if (tvEisMin != null)         tvEisMin.setText(String.format(Locale.getDefault(), "%.2f", eisMin));
            if (tvEisFluctuation != null) tvEisFluctuation.setText(String.format(Locale.getDefault(), "%.2f", eisMax - eisMin));
            if (tvEisMaxTime != null)     tvEisMaxTime.setText(formatTime(eisMaxTime));
            if (tvEisMinTime != null)     tvEisMinTime.setText(formatTime(eisMinTime));
        }

        public void updateDisplay(CircleProgressView cp, TextView ion, TextView speed,
                                  float ionValue, float sweatSpeed, float sweatRate) {
            if (cp != null) {
                cp.setValueText(String.format(Locale.getDefault(), "%.1f", sweatRate));
                cp.setProgress(sweatRate / 100f);
            }
            if (ion != null) ion.setText(String.format(Locale.getDefault(), "%.2f", ionValue));
            if (speed != null) speed.setText(String.format(Locale.getDefault(), "%.2f", sweatSpeed));
        }

        public void reset() {
            ocpMax = -Float.MAX_VALUE; ocpMin = Float.MAX_VALUE;
            eisMax = -Float.MAX_VALUE; eisMin = Float.MAX_VALUE;
        }

        private String formatTime(long millis) {
            return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(millis));
        }
    }

    // ─── 生命周期 ─────────────────────────────────────────────────

    private FragmentParameter mFragmentParameter;

    @Override
    protected FragmentCustomBinding getViewBinding() {
        return FragmentCustomBinding.inflate(getLayoutInflater());
    }
}
