package com.hc.mixthebluetooth.driver.capability;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.List;

public interface ReplaySource {
    @NonNull
    List<String> readLines() throws IOException;
}
