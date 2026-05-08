package com.hc.mixthebluetooth.activity.tool.runtime;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.activity.tool.message.MessageOptionStore;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;
import com.hc.mixthebluetooth.storage.Storage;

/**
 * FragmentRuntime — Fragment 基础能力的"中介打包"
 * <p>
 * FragmentMessage 构造 Runtime 时传入三个 Lambda：
 * <p>
 * runtime = new FragmentRuntime(
 * context,
 * new Storage(context),
 * FragmentParameter.getInstance(),
 * item -> sendDataToActivity(CMD_SEND_BT_DATA, item),  // commandSink
 * this::toastShort,                                  // notifier
 * this::logWarn                                      // logger
 * );
 * <p>
 * 子模块使用 Runtime 时，不需要知道这三个 Lambda 背后是谁：
 * <p>
 * runtime.sendBtData(item) → commandSink.sendBtData(item)
 * → FragmentMessage.this.sendDataToActivity(CMD_SEND_BT_DATA, item)
 * → LiveEventBus → Activity 收到 → 实际发蓝牙数据
 * <p>
 * runtime.toast("错误") → notifier.toast("错误")
 * → FragmentMessage.this.toastShort("错误")
 * → Toast.makeText(...).show()
 * <p>
 * runtime.log("警告") → logger.log("警告")
 * → FragmentMessage.this.logWarn("警告")
 * → Log.w(TAG, "警告")
 * <p>
 * 另外，Runtime 还负责维护两个状态：
 * module     — 当前连接的蓝牙设备模块
 * connected  — 当前是否已连接
 * <p>
 * 这两个状态被 MessageSender 用来做发送前的检查：
 * if (!runtime.connected()) { runtime.toast("当前状态不可以发送数据"); }
 * if (runtime.module() == null) { runtime.toast("当前没有可发送的蓝牙模块"); }
 */
public final class FragmentRuntime implements MessageOptionStore.FragmentRuntimeAccess {

    // ── 三个通信接口 ─────────────────────────────────────────────

    /**
     * CommandSink — 发送蓝牙数据的通道。
     * 传入的 Lambda：item → sendDataToActivity(CMD_SEND_BT_DATA, item)
     * 最终效果：Activity 收到 LiveEventBus 事件，实际发送蓝牙数据。
     */
    public interface CommandSink {
        void sendBtData(@NonNull FragmentMessageItem item);
    }

    /**
     * Notifier — 弹出 Toast。
     * 传入的 Lambda：message → toastShort(message)
     */
    public interface Notifier {
        void toast(@NonNull String message);
    }

    /**
     * Logger — 打印日志。
     * 传入的 Lambda：message → logWarn(message)
     */
    public interface Logger {
        void log(@NonNull String message);
    }

    // ── 字段 ───────────────────────────────────────────────────

    /**
     * Android Context（用于获取资源等）
     */
    private final Context context;

    /**
     * 持久化存储
     */
    private final Storage storage;

    /**
     * Fragment 参数（包含编码格式等配置）
     */
    private final FragmentParameter fragmentParameter;

    /**
     * 三个通信回调（由构造时传入的 Lambda 赋值）
     */
    private final CommandSink commandSink;
    private final Notifier notifier;
    private final Logger logger;

    /**
     * 当前连接的蓝牙设备模块（连接成功时 set，断开时 clear）
     */
    private DeviceModule module;

    /**
     * 当前是否已连接（用于 MessageSender 发送前检查）
     */
    private boolean connected;

    // ── 构造函数 ────────────────────────────────────────────────

    /**
     * 构造 FragmentRuntime。
     *
     * @param commandSink 发送蓝牙数据的回调（Fragment 传入的 Lambda）
     * @param notifier    弹出 Toast 的回调（Fragment 传入的 Lambda）
     * @param logger      打印日志的回调（Fragment 传入的 Lambda）
     */
    public FragmentRuntime(
            @NonNull Context context,
            @NonNull Storage storage,
            @NonNull FragmentParameter fragmentParameter,
            @NonNull CommandSink commandSink,
            @NonNull Notifier notifier,
            @NonNull Logger logger
    ) {
        this.context = context;
        this.storage = storage;
        this.fragmentParameter = fragmentParameter;
        this.commandSink = commandSink;
        this.notifier = notifier;
        this.logger = logger;
    }

    // ── 访问器 ──────────────────────────────────────────────────

    @NonNull
    @Override
    public Context context() {
        return context;
    }

    @NonNull
    @Override
    public Storage storage() {
        return storage;
    }

    @NonNull
    @Override
    public FragmentParameter fragmentParameter() {
        return fragmentParameter;
    }

    /**
     * 获取当前连接的蓝牙设备模块（可能为 null，如果未连接）
     */
    @Nullable
    public DeviceModule module() {
        return module;
    }

    /**
     * 设置当前连接的蓝牙设备模块（连接成功时由 FM 调用）
     */
    public void setModule(@Nullable DeviceModule module) {
        this.module = module;
    }

    /**
     * 查询是否已连接
     */
    public boolean connected() {
        return connected;
    }

    /**
     * 设置连接状态（连接成功/断开时由 FM 调用）
     */
    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    // ── 对外暴露的三个基础能力 ───────────────────────────────────

    /**
     * 发送蓝牙数据。
     * 内部调用 commandSink.sendBtData()，实际执行的是 Fragment 传入的 Lambda。
     * <p>
     * 典型调用路径：
     * MessageSender.send() → runtime.sendBtData(item)
     * → commandSink.sendBtData(item)
     * → FragmentMessage.sendDataToActivity(CMD_SEND_BT_DATA, item)
     * → LiveEventBus.publish()
     * → Activity 收到，调用蓝牙库发送数据
     */
    public void sendBtData(@NonNull FragmentMessageItem item) {
        commandSink.sendBtData(item);
    }

    /**
     * 弹出 Toast 提示。
     * 用于子模块向用户报告错误或状态（如"发送失败"、"没有连接"）。
     */
    public void toast(@NonNull String message) {
        notifier.toast(message);
    }

    /**
     * 打印警告日志。
     * 用于子模块记录异常情况（如解析失败、未知按钮点击等）。
     */
    public void log(@NonNull String message) {
        logger.log(message);
    }
}
