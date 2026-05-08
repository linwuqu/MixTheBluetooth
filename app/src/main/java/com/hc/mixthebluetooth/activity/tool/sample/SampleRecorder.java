package com.hc.mixthebluetooth.activity.tool.sample;

import android.content.Context;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SampleRecorder — 录波器：把采到的血糖样本写入 JSONL 文件
 *
 * 作用：
 *   用户按"开始录波"后，每次收到有效的血糖样本（BluetoothSample），
 *   都会被转成一行 JSON，写入带时间戳的 JSONL 文件。
 *   录波结束后，可以导出文件，在电脑上分析数据。
 *
 * 文件格式（JSONL = JSON Lines，每行一条 JSON）：
 *   {"type":"cgm","event":"current","raw":"CA:266,99.3","metrics":{"primary":99.3,"current":99.3}}
 *   {"type":"cgm","event":"current","raw":"CA:266,100.1","metrics":{"primary":100.1,"current":100.1}}
 *   ...每行一条记录，方便用 Python / Excel 等工具处理
 *
 * 文件命名规则：
 *   message_cgm_20260507_143000.jsonl
 *   前缀 + 下划线 + 时间戳，区分每次录波会话
 *
 * 为什么用单线程 ExecutorService 写文件？
 *   因为文件 IO 操作（磁盘写入）可能较慢，如果放在主线程会卡 UI。
 *   用单线程池保证写入顺序，同时不影响 UI 响应。
 *
 * 状态：
 *   recording == false：空闲，不接收任何 appendLine() 请求
 *   recording == true：录波中，appendLine() 正常写入
 *
 * 典型调用链：
 *   startRecording()  → recorder.start(context, "message_cgm")
 *                      → 创建带时间戳的 JSONL 文件，recording = true
 *
 *   每收到一个 sample → CgmRecorderConsumer.consume(sample)
 *                      → CgmJsonLineBuilder.build(...) → JSON 字符串
 *                      → recorder.appendLine(jsonLine)
 *                      → io.execute(() → 异步写文件)
 *
 *   stopRecording()   → recorder.stop()
 *                      → recording = false，不再接收新数据
 *
 *   exportRecording() → recorder.exportPath()
 *                      → 触发 onRecordExported 回调，Toast 显示文件路径
 */
public final class SampleRecorder {

    /** 录波状态变化、导出完成、写文件出错时的回调接口 */
    public interface Callback {
        /** 录波开始或停止时调用（用于更新界面文字） */
        void onRecordStateChanged(boolean recording);

        /** 导出完成时调用（传入文件路径） */
        void onRecordExported(@NonNull String path);

        /** 写文件出错时调用 */
        void onRecordError(@NonNull String message);
    }

    /** 后台线程池：单线程执行文件写入（保证顺序，不阻塞主线程） */
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    /** 外部回调（用于通知录波状态变化和错误） */
    private final Callback callback;

    /** 当前是否在录波中 */
    private volatile boolean recording = false;

    /** 当前录波文件的引用（start() 时创建，stop() 时置 null） */
    private volatile File recordFile = null;

    /** 已写入的样本数量 */
    private int sampleCount = 0;

    public SampleRecorder(@Nullable Callback callback) {
        this.callback = callback;
    }

    /**
     * 开始录波。
     * 创建一个带时间戳的 JSONL 文件，之后所有 appendLine() 都会写入这个文件。
     *
     * @param context    Android Context（用于获取文件目录）
     * @param filePrefix 文件名前缀，如 "message_cgm" → 生成 "message_cgm_20260507_143000.jsonl"
     */
    public void start(@NonNull Context context, @NonNull String filePrefix) {
        recordFile = createRecordFile(context, filePrefix);
        recording = true;
        sampleCount = 0;
        notifyStateChanged(true);
    }

    /** 停止录波。之后 appendLine() 会被静默忽略。 */
    public void stop() {
        recording = false;
        notifyStateChanged(false);
    }

    /** 查询是否正在录波（SampleChartBinder 用这个作为 Gate 门控） */
    public boolean isRecording() {
        return recording;
    }

    /** 获取已写入的样本数量 */
    public int getSampleCount() {
        return sampleCount;
    }

    /**
     * 触发导出回调。
     * 调用后会触发 callback.onRecordExported(path)，Toast 显示文件路径。
     * 注意：这只是通知外部文件已经准备好了，并没有真正复制到某个公共目录。
     */
    @NonNull
    public String exportPath() {
        String path = getRecordPath();
        if (callback != null) {
            callback.onRecordExported(path);
        }
        return path;
    }

    /**
     * 追加一行 JSON 到当前录波文件（异步，在后台线程执行）。
     *
     * @param jsonLine 一行完整的 JSON 字符串
     *
     * 跳过条件（不会写入文件，也不会有错误）：
     *   - recording == false（没有在录波）
     *   - recordFile == null（文件还没创建）
     *   - jsonLine == null 或空字符串
     */
    public void appendLine(@Nullable String jsonLine) {
        if (!canAppend(jsonLine)) return;

        File file = recordFile; // 先复制到局部变量，避免并发问题
        String safeLine = jsonLine.trim();
        sampleCount++;
        io.execute(() -> appendUtf8Line(file, safeLine)); // 后台线程异步写
    }

    /** 释放资源：关闭后台线程池（Fragment onDestroy 时调用） */
    public void release() {
        io.shutdown();
    }

    /** 写入前的前置检查 */
    private boolean canAppend(@Nullable String jsonLine) {
        if (!recording) return false;
        if (recordFile == null) return false;
        return jsonLine != null && !jsonLine.trim().isEmpty();
    }

    @NonNull
    private String getRecordPath() {
        return recordFile != null ? recordFile.getAbsolutePath() : "";
    }

    private void notifyStateChanged(boolean recording) {
        if (callback != null) {
            callback.onRecordStateChanged(recording);
        }
    }

    /**
     * 创建本次录波的文件。
     * 目录优先级：外部文件目录（Documents）> 应用私有目录 > filesDir
     * 文件名：prefix_yyyyMMdd_HHmmss.jsonl
     */
    @NonNull
    private File createRecordFile(@NonNull Context context, @NonNull String filePrefix) {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) dir = context.getExternalFilesDir(null);
        if (dir == null) dir = context.getFilesDir();

        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File file = new File(dir, filePrefix + "_" + ts + ".jsonl");

        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && callback != null) {
            callback.onRecordError("Create record directory failed: " + parent.getAbsolutePath());
        }
        return file;
    }

    /** 实际写入文件（后台线程执行）：追加一行 UTF-8 编码的文本 */
    private void appendUtf8Line(@NonNull File file, @NonNull String line) {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
            writer.write(line);
            writer.newLine();
        } catch (Exception e) {
            if (callback != null) {
                callback.onRecordError("Record write failed: " + e.getMessage());
            }
        }
    }
}
