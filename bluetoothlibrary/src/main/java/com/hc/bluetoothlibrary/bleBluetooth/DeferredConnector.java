package com.hc.bluetoothlibrary.bleBluetooth;
// 解决竞态问题
final class DeferredConnector<T> {

    interface Connection<T> {
        void connect(T target);
    }

    // 两个必要物件
    private Connection<T> connection;
    private T pendingTarget;

    // 先给出目标
    synchronized void submit(T target) {
        pendingTarget = target;
        drain();
    }

    // 先给出连接
    synchronized void attach(Connection<T> connection) {
        this.connection = connection;
        drain();
    }

    synchronized void detach() {
        connection = null;
    }

    synchronized void clear() {
        pendingTarget = null;
    }

    private void drain() {
        if (connection == null || pendingTarget == null) return;
        T target = pendingTarget;
        pendingTarget = null;
        connection.connect(target);
    }
}
