package com.hc.mixthebluetooth.activity.tool.message;

/**
 * ControlAction — 按钮动作接口
 *
 * 作用：
 *   代表"按下按钮后要执行的一个动作"。
 *   ControlRegistry 用 Map<Integer, ControlAction> 存储"按钮ID → 动作"的映射。
 *   按下按钮时，dispatch() 找到对应的 ControlAction，调用其 run() 方法。
 *
 * 为什么用接口而不是直接用 Runnable？
 *   Runnable 也能表达"一个无参数无返回值的动作"。
 *   但用自定义接口 ControlAction 有两个好处：
 *     ① 代码更清晰：ControlAction 的语义是"控制动作"，比 Runnable 更明确
 *     ② 以后扩展更方便：可以在接口里加更多方法（如 canExecute() 检查前置条件）
 *
 * 典型使用：
 *   controls.bind(btnStartMeasure, () -> sender.send(CgmCommandSet.startNow()));
 *   这里 Lambda () -> sender.send(...) 实现了 ControlAction 接口。
 *
 * 等价的匿名内部类写法：
 *   controls.bind(btnStartMeasure, new ControlAction() {
 *       @Override public void run() {
 *           sender.send(CgmCommandSet.startNow());
 *       }
 *   });
 */
public interface ControlAction {
    void run();
}
