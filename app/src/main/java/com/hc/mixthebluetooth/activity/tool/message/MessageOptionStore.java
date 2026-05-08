package com.hc.mixthebluetooth.activity.tool.message;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.customView.PopWindowFragment;
import com.hc.mixthebluetooth.storage.Storage;

/**
 * MessageOptionStore — 用户发送/接收选项的持久化存取器
 * <p>
 * 作用：
 * 从 SharedPreferences（或 Storage）加载用户在设置面板里配置的选项。
 * 这些选项影响 MessageSender（发送）和 MessagePipelineController（接收）的行为。
 * <p>
 * 为什么需要单独一个存取器？
 * 因为加载逻辑涉及：
 * ① 从 Storage 读取 6 个布尔值（showOutgoing / showTime / sendHex 等）
 * ② 从 FragmentParameter 读取编码格式（UTF-8 / GBK 等）
 * 打包成 MessageOptionStore.load() 后，FragmentMessage 只需要调用一次。
 * <p>
 * SharedPreferences 的 key 来源：
 * 所有 key 都是 PopWindowFragment 里定义的常量（如 KEY_DATA / KEY_TIME 等）。
 * 用户在设置面板改了选项后，实际是往 SharedPreferences 写这些 key。
 * load() 时按同样的 key 读出来。
 * <p>
 * 数据来源：
 * Storage.getData(key) — 读 SharedPreferences，返回 boolean
 * FragmentParameter.getCodeFormat(context) — 读编码格式（全局配置）
 * <p>
 * 为什么 options 不用 setter，只用 load()？
 * 因为 options 是不可变对象（final 字段）。
 * 如果用户改了设置，FragmentMessage 会调用 refreshOptions()，
 * 重新 load() 一次生成新的 MessageOptions 对象，再调用 sender.updateOptions() 替换。
 * 不直接修改现有对象，而是替换整个对象——这是函数式编程的思路，避免意外修改。
 */
public final class MessageOptionStore {

    /**
     * FragmentRuntimeAccess — FragmentRuntime 必须实现的接口
     * <p>
     * 为什么需要这个接口？
     * MessageOptionStore 需要访问 runtime.storage() 和 runtime.fragmentParameter()，
     * 但不希望直接依赖 FragmentRuntime 类本身（避免循环依赖或强耦合）。
     * 所以定义一个只包含"需要的方法"的接口，FragmentRuntime 实现它。
     * <p>
     * FragmentRuntime implements MessageOptionStore.FragmentRuntimeAccess
     * MessageOptionStore 依赖 FragmentRuntimeAccess（而不是 FragmentRuntime）
     */
    public interface FragmentRuntimeAccess {
        @NonNull
        Storage storage();

        @NonNull
        FragmentParameter fragmentParameter();

        @NonNull
        Context context();
    }

    private final FragmentRuntimeAccess runtime;

    public MessageOptionStore(@NonNull FragmentRuntimeAccess runtime) {
        this.runtime = runtime;
    }

    /**
     * 加载所有选项。
     * <p>
     * 读取步骤：
     * ① 从 runtime.storage() 读取 6 个布尔值
     * ② 从 runtime.fragmentParameter() 读取编码格式
     * ③ 打包成 MessageOptions 返回
     * <p>
     * 返回值是新的 MessageOptions 实例（不可变）。
     * FragmentMessage.refreshOptions() 会调用这个方法，用返回的新对象替换旧的。
     */
    @NonNull
    public MessageOptions load() {
        Storage storage = runtime.storage();
        String codeFormat = runtime.fragmentParameter().getCodeFormat(runtime.context());
        return new MessageOptions(storage.getData(PopWindowFragment.KEY_DATA),     // showOutgoing
                storage.getData(PopWindowFragment.KEY_TIME),    // showTime
                storage.getData(PopWindowFragment.KEY_HEX_SEND), // sendHex
                storage.getData(PopWindowFragment.KEY_HEX_READ), // readHex
                storage.getData(PopWindowFragment.KEY_CLEAR),    // autoClear
                storage.getData(PopWindowFragment.KEY_NEWLINE),  // sendNewline
                codeFormat == null ? "" : codeFormat            // codeFormat
        );
    }
}
