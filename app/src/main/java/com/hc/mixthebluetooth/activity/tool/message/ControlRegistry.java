package com.hc.mixthebluetooth.activity.tool.message;

import android.view.View;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ControlRegistry — 按钮调度器：管理"按钮 → 动作"的映射关系
 *
 * 作用：
 *   界面有多个按钮（CGM 有 4 个：开始测量/读缓存/删缓存/参数设置，
 *   界面通用有 4 个：开始录波/停止录波/导出/设置）。
 *   每个按钮按下后要执行不同的动作（发命令、弹对话框、调方法等）。
 *
 *   ControlRegistry 的职责就是：维护一张 Map<按钮ID, 动作> 的表格，
 *   按钮被点击时，查表找到对应动作并执行。
 *
 * 为什么用 Map 而不是直接写 if-else？
 *   if-else 的写法：
 *     if (view.getId() == R.id.btnStartMeasure) { sender.send(startNow()); }
 *     else if (view.getId() == R.id.btnReadCache) { sender.send(readCache()); }
 *     ...每加一个按钮都要改这里
 *
 *   Map 的写法（现在的）：
 *     controls.bind(btnStartMeasure, () -> sender.send(startNow()));
 *     controls.bind(btnReadCache,    () -> sender.send(readCache()));
 *     ...
 *     dispatch() 自动根据 ID 查表执行，不用改 dispatch() 本身
 *
 * 典型调用链：
 *   initControls() 阶段：
 *     controls.bind(btnStartMeasure, () -> sender.send(CgmCommandSet.startNow()));
 *     controls.bind(btnReadCache,    () -> sender.send(CgmCommandSet.readCache()));
 *     ...每绑定一个按钮，就往 Map 里加一条记录
 *
 *   用户点击按钮时：
 *     onClickView(btnStartMeasure)
 *       → controls.dispatch(btnStartMeasure)
 *       → 按 view.getId() 查 Map，找到对应的 Lambda
 *       → Lambda.run() → sender.send(CgmCommandSet.startNow())
 *       → MessageSender.send() → runtime.sendBtData() → 蓝牙发送
 *
 * 为什么用 LinkedHashMap 而不是 HashMap？
 *   LinkedHashMap 保持插入顺序（虽然这里并不依赖顺序，但 LinkedHashMap 性能足够好）。
 *   不需要 HashMap 的随机顺序，所以直接用 LinkedHashMap。
 */
public final class ControlRegistry {

    /** 按钮 ID → 动作 的映射表 */
    private final Map<Integer, ControlAction> actions = new LinkedHashMap<>();

    /**
     * 绑定一个按钮和它的动作。
     *
     * @param view   按钮 View（用它的 getId() 作为 key）
     * @param action 按下后要执行的动作（一个 ControlAction，通常是 Lambda）
     * @return this，支持链式调用
     */
    @NonNull
    public ControlRegistry bind(@NonNull View view, @NonNull ControlAction action) {
        actions.put(view.getId(), action);
        return this;
    }

    /**
     * 分发一个按钮点击事件：找到对应的动作并执行。
     *
     * @param view 被点击的按钮 View
     * @return true 找到了对应动作并执行了；false Map 里没有这个按钮的记录
     *
     * 在 FragmentMessage.onClickView() 里调用：
     *   if (controls != null && controls.dispatch(view)) {
     *       return;  // 已处理
     *   }
     *   logWarn("Unhandled click: " + view.getId()); // 没找到对应按钮
     */
    public boolean dispatch(@NonNull View view) {
        ControlAction action = actions.get(view.getId());
        if (action == null) return false;  // Map 里没这个按钮
        action.run();                      // 执行绑定的动作
        return true;
    }
}
