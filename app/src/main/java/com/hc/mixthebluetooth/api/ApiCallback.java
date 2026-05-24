package com.hc.mixthebluetooth.api;

@FunctionalInterface
public interface ApiCallback<T> {
    void onResult(T result);
}
