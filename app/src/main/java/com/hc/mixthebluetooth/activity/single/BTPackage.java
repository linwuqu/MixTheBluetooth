package com.hc.mixthebluetooth.activity.single;

import androidx.annotation.NonNull;

import com.hc.bluetoothlibrary.DeviceModule;

/**
 * 蓝牙数据包统一包装器 — 替代原有的 Object[] 二次判断模式。
 *
 * 设计背景：
 * 原来 FRAGMENT_STATE_DATA 承载了 4 种完全不同性质的 payload，
 * 每个 Fragment 都需要 instanceof + arr.length 检查，耦合严重。
 *
 * 解决方案：5 种确定性子类，不再需要二次判断。
 *
 * 用法示例（Activity 侧推送）：
 *   // 推送蓝牙数据帧
 *   sendDataToFragment(CH_BT_DATA, new BTPackage.BTData(module, bytes));
 *
 *   // 推送连接成功
 *   sendDataToFragment(CH_BT_DATA, new BTPackage.Connected(module));
 *
 *   // 推送断开
 *   sendDataToFragment(CH_BT_DATA, BTPackage.Disconnected.INSTANCE);
 *
 * 用法示例（Fragment 侧接收，继承 BTFragment）：
 *   @Override protected void onBtConnected(@NonNull DeviceModule m) {
 *       module = m;
 *   }
 *   @Override protected void onBtData(@NonNull BTPackage.BTData d) {
 *       processLine(decode(d.bytes));
 *   }
 *
 * 5 种类型：
 *   BTData        — 蓝牙数据帧
 *   Connected     — 连接成功
 *   Disconnected  — 连接断开
 *   Velocity      — 实时速度
 *   Log           — 日志消息
 */
public abstract class BTPackage {

    private BTPackage() {}

    // ─── 蓝牙数据帧 ──────────────────────────────────────────────────

    public static final class BTData extends BTPackage {
        @NonNull public final DeviceModule module;
        @NonNull public final byte[] bytes;

        public BTData(@NonNull DeviceModule module, @NonNull byte[] bytes) {
            this.module = module;
            this.bytes  = bytes;
        }
    }

    // ─── 连接成功 ───────────────────────────────────────────────────

    public static final class Connected extends BTPackage {
        @NonNull public final DeviceModule module;

        public Connected(@NonNull DeviceModule module) {
            this.module = module;
        }
    }

    // ─── 连接断开 ───────────────────────────────────────────────────

    public static final class Disconnected extends BTPackage {
        public static final Disconnected INSTANCE = new Disconnected();
    }

    // ─── 实时速度 ───────────────────────────────────────────────────

    public static final class Velocity extends BTPackage {
        public final int bytesPerSecond;

        public Velocity(int bytesPerSecond) {
            this.bytesPerSecond = bytesPerSecond;
        }
    }

    // ─── 日志消息 ───────────────────────────────────────────────────

    public static final class Log extends BTPackage {
        @NonNull public final String message;

        public Log(@NonNull String message) {
            this.message = message;
        }
    }
}
