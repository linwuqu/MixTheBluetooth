package com.hc.mixthebluetooth.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.mikephil.charting.charts.LineChart;
import com.hc.basiclibrary.viewBasic.BaseFragment;
import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.single.BTPackage;
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.activity.tool.ChartRegistry;
import com.hc.mixthebluetooth.activity.tool.CommunicateTool;
import com.hc.mixthebluetooth.activity.tool.RecyclerTool;
import com.hc.mixthebluetooth.databinding.FragmentMessageNewBinding;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;
import com.hc.mixthebluetooth.storage.Storage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * FragmentMessageNew 重构参考实现。
 *
 * 重构亮点：
 * 1. 继承 BTFragment，接收类型安全的 BTPackage，不再需要 instanceof 判断
 * 2. 使用 ChartRegistry 管理两个 LineChart，信道注册 + 数据追加全自动
 * 3. 使用 CommunicateTool 管理行缓冲、编解码、JSON 构建
 * 4. 使用 RecyclerTool 管理消息列表
 * 5. initAllImpl() 结构清晰：initStorage() → initRecycler() → initCharts() → initControls()
 * 6. 所有录制逻辑通过 BTPackage 路由，不再需要字符串常量比较
 *
 * init 主线（极度清晰）：
 *   initStorage()       — 持久化配置
 *   initRecycler()      — RecyclerView 初始化
 *   initCharts()        — 两个 LineChart 注册
 *   initControls()      — 按钮绑定
 *   setBottomInfo()     — 状态栏
 *
 * 信道（仅2个，职责分明）：
 *   CH_BT_DATA         → onBtData() / onBtConnected()
 *   CH_REC_STATE       → onRecStateChanged()
 *
 * 控制指令（Fragment → Activity）：
 *   CMD_MSG_NEW_CONTROL → 开始/停止录制、导出
 *   CH_REC_SAMPLE      → 单条 JSON Line
 */
public class FragmentMessageNew extends BTFragment<FragmentMessageNewBinding> {

    // ─── 工具实例 ─────────────────────────────────────────────────────

    private final RecyclerTool    recycler = new RecyclerTool();
    private final ChartRegistry   chartRegistry = new ChartRegistry();
    private final CommunicateTool  comm = new CommunicateTool();

    // ─── 状态 ────────────────────────────────────────────────────────

    private DeviceModule module;
    private boolean      mIsRecording = false;
    private long         mStartTimeMs;
    private Storage      mStorage;

    // ─── 配置常量 ─────────────────────────────────────────────────────

    private static final int   MAX_POINTS           = 500;
    private static final float VISIBLE_WINDOW_SEC   = 60f;

    private static final String KEY_SHOW_RAW_LIST    = "MSG_NEW_SHOW_RAW_LIST";
    private static final String KEY_AUTO_START_RECORD = "MSG_NEW_AUTO_START_RECORD";

    // ─── Channel 注册 ─────────────────────────────────────────────────

    @Override
    protected void initChannels() {
        register(StaticConstants.CH_BT_DATA, StaticConstants.CH_REC_STATE);
    }

    // ─── Init 主线 ────────────────────────────────────────────────────

    @Override
    protected void initAllImpl(@NonNull View view, @NonNull Context context) {
        initStorage(context);
        initRecycler(context);
        initCharts();
        initControls();
        setBottomInfo("Ready");
    }

    private void initStorage(@NonNull Context ctx) {
        mStorage = new Storage(ctx);
        if (mStorage.getData(KEY_AUTO_START_RECORD)) {
            sendControl(MessageNewCmd.START_RECORD);
        }
    }

    private void initRecycler(@NonNull Context ctx) {
        recycler.init(ctx, R.layout.item_message_fragment)
                .layoutManager(RecyclerTool.Layout.LINEAR)
                .attach(viewBinding.recyclerMessageNew)
                .build();
    }

    private void initCharts() {
        mStartTimeMs = System.currentTimeMillis();
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

        chartRegistry.register(viewBinding.chartOhm, new ChartRegistry.ChartConfig.Builder()
                .label("阻抗 (Ω)")
                .color(0xFFFF0000) // ARGB RED
                .maxPoints(MAX_POINTS)
                .visibleWindowSeconds(VISIBLE_WINDOW_SEC)
                .lineWidth(1.2f)
                .xAxisFormatter(ts -> fmt.format(new Date(ts)))
                .build());

        chartRegistry.register(viewBinding.chartUs, new ChartRegistry.ChartConfig.Builder()
                .label("电导 (uS)")
                .color(0xFF0000FF) // ARGB BLUE
                .maxPoints(MAX_POINTS)
                .visibleWindowSeconds(VISIBLE_WINDOW_SEC)
                .lineWidth(1.2f)
                .xAxisFormatter(ts -> fmt.format(new Date(ts)))
                .build());
    }

    private void initControls() {
        bindOnClickListener(
                viewBinding.btnStartRecord,
                viewBinding.btnStopRecord,
                viewBinding.btnExport
        );
    }

    // ─── BTFragment 类型安全回调 ──────────────────────────────────────

    @Override
    protected void onBtConnected(@NonNull DeviceModule m) {
        module = m;
        logWarn("MessageNew connected: " + m.getName() + " / " + m.getMac());
    }

    @Override
    protected void onBtData(@NonNull BTPackage.BTData d) {
        String code = com.hc.mixthebluetooth.activity.single.FragmentParameter
                .getInstance().getCodeFormat(requireContext());
        String chunk = CommunicateTool.decode(d.bytes, code);
        if (chunk == null || chunk.isEmpty()) return;

        comm.addRxBytes(d.bytes.length);
        String line;
        while ((line = comm.pollLine(chunk)) != null) {
            if (line.isEmpty()) continue;
            processOneLine(line);
        }
    }

    @Override
    protected void onRecStateChanged(boolean recording) {
        mIsRecording = recording;
        viewBinding.tvRecordState.setText(recording ? "Record: ON" : "Record: OFF");
        setBottomInfo(recording ? "Recording started" : "Recording stopped");
        if (recording) chartRegistry.resetAll(System.currentTimeMillis());
        logWarn("MessageNew record: " + (recording ? "ON" : "OFF"));
    }

    @Override
    protected void onRecExportResult(@Nullable String path) {
        String msg = path != null ? "Export: " + path : "Export: (empty)";
        toastShort(msg);
        setBottomInfo(msg);
        logWarn("MessageNew export: " + path);
    }

    // ─── 业务逻辑 ─────────────────────────────────────────────────────

    private void processOneLine(@NonNull String line) {
        float[] v = CommunicateTool.parseEisLine(line);
        if (v == null) {
            comm.incLinesFail();
            logWarn("MessageNew parse failed, raw: " + line);
            updateBottomInfo();
            return;
        }

        comm.incLinesOk();
        if (mStorage.getData(KEY_SHOW_RAW_LIST)) {
            recycler.addTextLine(line, module, false);
        }

        if (mIsRecording) {
            long tMs = System.currentTimeMillis();
            chartRegistry.append("阻抗 (Ω)", tMs, v[0]);
            chartRegistry.append("电导 (uS)", tMs, v[1]);

            String jsonLine = CommunicateTool.buildJsonLine(
                    tMs, module,
                    new CommunicateTool.Pair("ohm", v[0]),
                    new CommunicateTool.Pair("us",  v[1]),
                    line
            );
            sendDataToActivity(StaticConstants.CH_REC_SAMPLE, jsonLine);
        }

        updateBottomInfo();
    }

    private void sendControl(@NonNull String cmd) {
        sendDataToActivity(StaticConstants.CMD_MSG_NEW_CONTROL, cmd);
        logWarn("MessageNew send control: " + cmd);
    }

    // ─── 点击事件 ─────────────────────────────────────────────────────

    @Override
    protected void onClickView(@NonNull View v) {
        if (isCheck(viewBinding.btnStartRecord)) {
            setBottomInfo("Start clicked");
            sendControl(MessageNewCmd.START_RECORD);
        } else if (isCheck(viewBinding.btnStopRecord)) {
            setBottomInfo("Stop clicked");
            sendControl(MessageNewCmd.STOP_RECORD);
        } else if (isCheck(viewBinding.btnExport)) {
            setBottomInfo("Export clicked");
            sendControl(MessageNewCmd.EXPORT);
        }
    }

    // ─── UI 辅助 ──────────────────────────────────────────────────────

    private void setBottomInfo(@Nullable String text) {
        if (viewBinding == null) return;
        viewBinding.tvBottomInfo.setText(text != null ? text : "");
    }

    private void updateBottomInfo() {
        long total = comm.getLinesTotal();
        String info = String.format(Locale.getDefault(),
                "Rec=%s  Bytes=%d  Parse=%d/%d",
                mIsRecording ? "ON" : "OFF",
                comm.getRxBytes(),
                comm.getLinesOk(),
                total
        );
        viewBinding.tvBottomInfo.setText(info);
    }

    // ─── 生命周期 ─────────────────────────────────────────────────────

    @Override
    protected FragmentMessageNewBinding getViewBinding() {
        return FragmentMessageNewBinding.inflate(getLayoutInflater());
    }
}
