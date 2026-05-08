package com.hc.mixthebluetooth.activity.tool.profile;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.chart.ChartRegistry;
import com.hc.mixthebluetooth.activity.tool.message.MessageSender;
import com.hc.mixthebluetooth.activity.tool.runtime.FragmentRuntime;
import com.hc.mixthebluetooth.activity.tool.sample.SampleRecorder;

public final class ProfileContext<B> {
    @NonNull
    public final B binding;
    @NonNull
    public final FragmentRuntime runtime;
    @NonNull
    public final ChartRegistry charts;
    @NonNull
    public final MessageSender sender;
    @NonNull
    public final SampleRecorder recorder;
    @NonNull
    public final UserRolePolicy rolePolicy;

    public ProfileContext(
            @NonNull B binding,
            @NonNull FragmentRuntime runtime,
            @NonNull ChartRegistry charts,
            @NonNull MessageSender sender,
            @NonNull SampleRecorder recorder,
            @NonNull UserRolePolicy rolePolicy
    ) {
        this.binding = binding;
        this.runtime = runtime;
        this.charts = charts;
        this.sender = sender;
        this.recorder = recorder;
        this.rolePolicy = rolePolicy;
    }
}
