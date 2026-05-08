package com.hc.mixthebluetooth.activity.tool.message;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSampleRegistry;
import com.hc.mixthebluetooth.activity.tool.sample.SampleConsumerRegistry;

/**
 * MessagePipelineController — 蓝牙数据接收流水线（核心枢纽）
 * <p>
 * 职责：接收蓝牙字节 → 解码成文本行 → 在消息列表显示 → 解析成结构化样本 → 广播给所有消费者
 * <p>
 * 核心流程（每收到一批蓝牙数据就走一遍）：
 * <p>
 * onBtData(module, bytes)                              // 入口：FM 收到蓝牙数据时调用
 * │                                                   //  ↓
 * ▼                                                   // BluetoothPayloadDecoder.decodeResult()
 * bytes → BluetoothPayloadDecoder（解码）               // 把字节数组按用户设置解码成文本行
 * │                                                   //  ↓
 * ▼                                                   // handleLine(module, "CA:266,99.3")
 * handleLine(module, line)                             // 处理单行文本
 * │
 * ├─ messageList.appendIncomingText(...)              // ① 在消息列表追加显示这行
 * │
 * ├─ sampleRegistry.parse(line)                      // ② 用已注册的解析器解析这行
 * │      │
 * │      ▼  CgmParser.parse("CA:266,99.3")
 * │      返回 CgmSample { event="ca", metrics={"primary": 99.3f} }
 * │
 * ▼
 * sampleConsumers.consume(sample)                       // ③ 广播给所有消费者
 * │
 * ├─ CgmStatusConsumer        → tvStatus.setText("ca")
 * ├─ CgmCurrentValueConsumer   → tvCurrentValue.setText("99.3")
 * ├─ CgmFileConsumer           → 缓存行写文件
 * ├─ CgmRecorderConsumer       → 录波写 JSONL
 * └─ SampleChartBinder         → 图表推数据点
 * <p>
 * 为什么叫"流水线"？
 * 因为数据流经每个处理阶段是单向的：字节 → 文本行 → 样本对象 → 消费者。
 * 每个阶段只做一件事，不回头，类似于工厂流水线。
 * <p>
 * 为什么叫"控制器"？
 * 因为它组合了多个子模块（messageList、sampleRegistry、sampleConsumers），
 * 控制它们按顺序工作，本身不直接操作字节或做业务判断。
 * <p>
 * 依赖：
 * context         — Android Context
 * messageList     — 显示收到的原始文本
 * sampleRegistry  — 解析文本成样本
 * sampleConsumers — 广播样本给所有消费者
 * options         — 解码设置（编码格式等）
 * logger          — 解析失败时打日志
 */
public final class MessagePipelineController {

    /**
     * SampleListener — 解析成功后的额外回调。
     * pipeline 可以通过这个回调通知外部（如 FM）：有新的有效样本进来了。
     * 目前传的是 null，保留扩展用。
     */
    public interface SampleListener {
        void onSample(@NonNull BluetoothSample sample);
    }

    /**
     * Logger — 日志接口。
     * 解析失败时调用，输出警告信息。
     * 传入的是 FragmentMessage::logWarn。
     */
    public interface Logger {
        void warn(@NonNull String message);
    }

    // ── 字段 ─────────────────────────────────────────────────

    private final Context context;
    private final MessageListController messageList;
    private final BluetoothSampleRegistry sampleRegistry;
    private final SampleConsumerRegistry sampleConsumers;
    private final SampleListener sampleListener;
    private final Logger logger;

    /**
     * 用户设置的解码选项（可动态更新，FM 调用 updateOptions 时刷新）
     */
    private MessageOptions options;

    // ── 构造函数 ─────────────────────────────────────────────

    /**
     * 简化构造：使用默认配置，适合不需要监听器和自定义消费者注册的场景。
     */
    public MessagePipelineController(
            @NonNull Context context,
            @NonNull MessageListController messageList,
            @NonNull BluetoothSampleRegistry sampleRegistry,
            @Nullable SampleListener sampleListener,
            @Nullable Logger logger
    ) {
        this(
                context,
                messageList,
                sampleRegistry,
                MessageOptions.defaults(FragmentParameter.getInstance().getCodeFormat(context)),
                null,       // sampleConsumers 由 initPipeline 时外部传入，这里用 null
                sampleListener,
                logger
        );
    }

    /**
     * 完整构造：所有依赖显式注入。
     * <p>
     * 注意：sampleConsumers 参数允许传 null。
     * 当传 null 时，流水线仍然会解码、解析、显示原始行，
     * 但不会触发消费者（不会更新 UI、不会写文件）。
     * 这在只需要显示原始数据、不需要解析的场景下有用。
     */
    public MessagePipelineController(
            @NonNull Context context,
            @NonNull MessageListController messageList,
            @NonNull BluetoothSampleRegistry sampleRegistry,
            @NonNull MessageOptions options,
            @Nullable SampleConsumerRegistry sampleConsumers,
            @Nullable SampleListener sampleListener,
            @Nullable Logger logger
    ) {
        this.context = context;
        this.messageList = messageList;
        this.sampleRegistry = sampleRegistry;
        this.options = options;
        this.sampleConsumers = sampleConsumers;
        this.sampleListener = sampleListener;
        this.logger = logger;
    }

    /**
     * 更新解码选项。
     * FragmentMessage.refreshOptions() 时会调用这个方法。
     * 下次 onBtData() 收到数据时，会按新的解码方式解析。
     */
    public void updateOptions(@NonNull MessageOptions options) {
        this.options = options;
    }

    // ── 核心方法 ───────────────────────────────────────────────

    /**
     * 流水线入口：收到蓝牙数据时由 FragmentMessage 调用。
     *
     * @param module 来源蓝牙设备模块（用于消息列表显示）
     * @param bytes  收到的原始字节数组
     *               <p>
     *               完整链路：
     *               <p>
     *               BluetoothPayloadDecoder.decodeResult(bytes, codeFormat, decoderOptions)
     *               → 把字节数组按 codeFormat（UTF-8/GBK/HEX 等）解码成文本行
     *               → 返回 Result { text: String, bytes: byte[], isText: boolean }
     *               → 如果 text 为空（纯二进制数据），直接返回，不处理
     *               → 如果有文本，调用 handleLine(module, text) 继续处理
     */
    public void onBtData(@Nullable DeviceModule module, @Nullable byte[] bytes) {
        BluetoothPayloadDecoder.Result result = BluetoothPayloadDecoder.decodeResult(
                bytes,
                options.codeFormat,
                options.decoderOptions(true)
        );
        if (result.isEmpty()) return;   // 空数据或纯二进制，跳过
        handleLine(module, result.text); // 有文本，继续处理
    }

    /**
     * 处理单行文本（流水线第二阶段）。
     * <p>
     * 在这里做了两件事：
     * ① 在消息列表追加显示原始行
     * ② 解析行，并广播给所有消费者
     * <p>
     * 注意：这里的"行"是指 BluetoothPayloadDecoder 返回的整段文本，
     * 里面可能包含多个换行符（设备可能一次发多行数据）。
     * 实际会调用 messageList.appendIncomingText()，由 MessageListController 内部处理换行拆分。
     */
    public void handleLine(@Nullable DeviceModule module, @NonNull String line) {
        // ① 在消息列表追加显示原始行（加上时间戳，如果用户开启了显示时间的话）
        String time = options.showTime ? Analysis.getTime() : null;
        messageList.appendIncomingText(line, false, time, module, false);
        messageList.notifyDataSetChangedAndScrollToBottom();

        // ② 用已注册的解析器解析这行文本
        BluetoothSample sample = sampleRegistry.parse(line);
        if (sample == null) {
            // 没有任何解析器能处理这行（不是 CGM 认识的数据）
            // 可能是设备乱发的，或者是其他协议的数据
            warn("Pipeline parse failed, raw: " + line);
            return;
        }

        // ③ 广播给所有消费者（如果有的话）
        if (sampleConsumers != null) {
            sampleConsumers.consume(sample);
        }

        // ④ 触发额外监听器（如果有的话，目前传的是 null）
        if (sampleListener != null) {
            sampleListener.onSample(sample);
        }
    }

    private void warn(@NonNull String message) {
        if (logger != null) {
            logger.warn(message);
        }
    }
}
