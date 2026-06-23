package com.hc.mixthebluetooth.ui.cgm;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.api.AppApi;
import com.hc.mixthebluetooth.driver.implementation.codec.Analysis;
import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.cgm.CgmResult;
import com.hc.mixthebluetooth.api.cgm.CgmWorkflow;
import com.hc.mixthebluetooth.driver.implementation.log.ApiTraceLogger;
import com.hc.mixthebluetooth.ui.cgm.CgmWidgets.MetricWidget;
import com.hc.mixthebluetooth.ui.cgm.CgmWidgets.WidgetSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class CgmController {
    private static final int AUTO_CLEAR_BYTES = 400_000;
    private static final String OWNER = "Controller";
    private static final String API_BT_SEND = "BT_SEND";
    private static final String API_BT_RECV = "BT_RECV";
    private static final String API_DEVICE_CONNECT = "DEVICE_CONNECT";
    private static final String API_RENDER = "RENDER";

    public enum Region {
        ACTION,
        SUMMARY,
        MAIN,
        SECONDARY,
        DEBUG
    }

    public enum Route {
        POST,
        INNER
    }

    public enum BuiltIn {
        START_RECORD,
        STOP_RECORD,
        EXPORT,
        CLEAR_MESSAGES,
        RESET_WIDGETS
    }

    public interface HostView {
        ViewGroup region(@NonNull Region region);

        RecyclerView messageList();

        void configureMessageList(@NonNull RecyclerView.Adapter<?> adapter);

        ViewGroup header();

        TextView bottomInfo();
    }

    public interface Gateway {
        void postText(@NonNull DeviceModule module, @NonNull String text);

        default void confirmDeleteCache(@NonNull Runnable onConfirm) {
            onConfirm.run();
        }

        default void onCgmWorkflowUpdate(@NonNull CgmWorkflow.Update update) {
        }

        default void onCgmWorkflowResult(@NonNull CallResult<CgmResult> result) {
        }
    }

    public static final class ActionSpec {
        @NonNull
        public final String id;
        @NonNull
        public final String label;
        @NonNull
        public final Route route;
        @Nullable
        public final TextSupplier textSupplier;
        @Nullable
        public final BuiltIn builtIn;

        private ActionSpec(
                @NonNull String id,
                @NonNull String label,
                @NonNull Route route,
                @Nullable TextSupplier textSupplier,
                @Nullable BuiltIn builtIn
        ) {
            this.id = id;
            this.label = label;
            this.route = route;
            this.textSupplier = textSupplier;
            this.builtIn = builtIn;
        }

        public static ActionSpec postText(@NonNull String id, @NonNull String label, @NonNull TextSupplier textSupplier) {
            return new ActionSpec(id, label, Route.POST, textSupplier, null);
        }

        public static ActionSpec inner(@NonNull String id, @NonNull String label, @NonNull BuiltIn builtIn) {
            return new ActionSpec(id, label, Route.INNER, null, builtIn);
        }
    }

    public interface TextSupplier {
        @NonNull
        String get();
    }

    public interface RecordFormatter {
        @NonNull
        String format(@NonNull BluetoothSample sample);
    }

    public interface RawLineConsumer {
        void onLine(@NonNull Context context,
                    @NonNull DeviceModule module,
                    @NonNull String text,
                    @NonNull Gateway gateway);

        default void onActionPosted(@NonNull Context context,
                                    @NonNull DeviceModule module,
                                    @NonNull ActionSpec action,
                                    @NonNull String payload,
                                    @NonNull Gateway gateway) {
        }
    }

    public static final class ProfileSpec {
        @NonNull
        public final String id;
        @NonNull
        public final List<BluetoothSampleParser> parsers;
        @NonNull
        public final List<ActionSpec> actions;
        @NonNull
        public final List<WidgetSpec> widgets;
        @Nullable
        public final RecordFormatter recordFormatter;
        @Nullable
        public final RawLineConsumer rawLineConsumer;

        private ProfileSpec(@NonNull Builder b) {
            this.id = b.id;
            this.parsers = new ArrayList<>(b.parsers);
            this.actions = new ArrayList<>(b.actions);
            this.widgets = new ArrayList<>(b.widgets);
            this.recordFormatter = b.recordFormatter;
            this.rawLineConsumer = b.rawLineConsumer;
        }

        public static Builder builder(@NonNull String id) {
            return new Builder(id);
        }

        public static final class Builder {
            @NonNull
            private final String id;
            private final List<BluetoothSampleParser> parsers = new ArrayList<>();
            private final List<ActionSpec> actions = new ArrayList<>();
            private final List<WidgetSpec> widgets = new ArrayList<>();
            @Nullable
            private RecordFormatter recordFormatter;
            @Nullable
            private RawLineConsumer rawLineConsumer;

            private Builder(@NonNull String id) {
                this.id = id;
            }

            public Builder parser(@NonNull BluetoothSampleParser parser) {
                parsers.add(parser);
                return this;
            }

            public Builder action(@NonNull ActionSpec action) {
                actions.add(action);
                return this;
            }

            public Builder widget(@NonNull WidgetSpec widget) {
                widgets.add(widget);
                return this;
            }

            public Builder recordJson(@NonNull RecordFormatter formatter) {
                recordFormatter = formatter;
                return this;
            }

            public Builder rawLineConsumer(@NonNull RawLineConsumer rawLineConsumer) {
                this.rawLineConsumer = rawLineConsumer;
                return this;
            }

            public ProfileSpec build() {
                return new ProfileSpec(this);
            }
        }
    }

    private final Context context;
    private final ProfileSpec spec;
    private final HostView host;
    private final Gateway gateway;
    private final Output output = new Output();
    private final ArrayList<MessageItem> messages = new ArrayList<>();
    private final ArrayList<MetricWidget> widgets = new ArrayList<>();
    private final HashMap<String, TextView> indicators = new HashMap<>();

    private MessageAdapter adapter;
    private DeviceModule module;
    private int readBytes;
    private int sentBytes;
    private boolean widgetsActive;

    public CgmController(@NonNull Context context, @NonNull ProfileSpec spec, @NonNull HostView host, @NonNull Gateway gateway) {
        this.context = context;
        this.spec = spec;
        this.host = host;
        this.gateway = gateway;
    }

    public void init() {
        adapter = new MessageAdapter(context, messages, R.layout.item_message_fragment);
        // configureMessageList() is deferred via pendingAdapter if BottomSheet hasn't been shown yet,
        // which happens during ViewPager onMeasure before the fragment view is inflated.
        host.configureMessageList(adapter);
        createActions();
        createSystemIndicators();
        createWidgets();
        setBottomInfo("Ready");
    }

    public void onEvent(@Nullable Object event) {
        if (event instanceof CgmBluetoothEvent.BTData) {
            onBtData((CgmBluetoothEvent.BTData) event);
        } else if (event instanceof CgmBluetoothEvent.Connected) {
            module = ((CgmBluetoothEvent.Connected) event).module;
            ApiTraceLogger.text(OWNER, API_DEVICE_CONNECT, "state",
                    "connected=true\ndevice=" + module.getName());
        } else if (event instanceof CgmBluetoothEvent.Disconnected) {
            module = null;
            ApiTraceLogger.text(OWNER, API_DEVICE_CONNECT, "state", "connected=false");
        } else if (event instanceof CgmBluetoothEvent.SentBytes) {
            sentBytes += ((CgmBluetoothEvent.SentBytes) event).count;
            updateByteCounter();
        } else if (event instanceof CgmBluetoothEvent.ConnectState) {
            setBottomInfo(((CgmBluetoothEvent.ConnectState) event).state);
        }
    }

    public void release() {
        output.release();
    }

    public void onCgmResult(@NonNull CgmResult result) {
        ApiTraceLogger.text(OWNER, API_RENDER, "cgmResult",
                "jobId=" + result.jobId
                        + "\nstatus=" + result.status
                        + "\npointCount=" + result.pointCount
                        + "\nunitCount=" + result.unitCount);
        for (MetricWidget widget : widgets) {
            widget.onCgmResult(result);
        }
        setBottomInfo("CGM points: " + result.pointCount + "  jobId: " + result.jobId);
    }

    private void createActions() {
        for (ActionSpec action : spec.actions) {
            TextView button = new TextView(context);
            button.setText(action.label);
            button.setTextSize(13f);
            button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            button.setGravity(Gravity.CENTER);
            button.setPadding(dp(18), dp(9), dp(18), dp(9));

            // Light card style: cream background, dark warm text
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(22));
            bg.setColor(Color.parseColor("#FFF8F2EC"));
            bg.setStroke(dp(1), Color.parseColor("#FFE0D6C8"));
            button.setBackground(bg);
            button.setTextColor(Color.parseColor("#3D2E24"));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(8), 0);
            button.setLayoutParams(lp);

            button.setOnClickListener(v -> handleAction(action));
            host.region(Region.ACTION).addView(button);
        }
    }

    private void createSystemIndicators() {
        addIndicatorChip("record_state", "Record: OFF", false);
        addIndicatorChip("byte_counter", "Read: 0 B  ·  Sent: 0 B", true);
    }

    private void addIndicatorChip(@NonNull String id, @NonNull String text, boolean append) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(11f);
        tv.setPadding(dp(10), dp(4), dp(10), dp(4));

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(Color.parseColor("#FFE8EDF2"));
        tv.setBackground(bg);
        tv.setTextColor(Color.parseColor("#FF5C6670"));

        indicators.put(id, tv);

        ViewGroup container = host.region(Region.DEBUG);
        if (append && !indicators.isEmpty()) {
            android.widget.Space sp = new android.widget.Space(context);
            sp.setLayoutParams(new LinearLayout.LayoutParams(dp(8), 1));
            container.addView(sp);
        }
        container.addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private void createWidgets() {
        ArrayList<WidgetSpec> orderedWidgets = CgmWidgets.orderedForDisplay(spec.widgets);
        for (WidgetSpec widgetSpec : orderedWidgets) {
            MetricWidget widget = CgmWidgets.create(context, widgetSpec);
            View view = widget.view();
            if (widgetSpec.region == Region.SUMMARY) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );
                params.setMargins(dp(4), dp(4), dp(4), dp(6));
                view.setLayoutParams(params);
            }
            host.region(widgetSpec.region).addView(view);
            widgets.add(widget);
        }
    }

    private void handleAction(@NonNull ActionSpec action) {
        if (action.route == Route.POST && action.textSupplier != null && module != null) {
            if ("delete_cache".equals(action.id)) {
                gateway.confirmDeleteCache(() -> postAction(action));
                return;
            }
            postAction(action);
            return;
        }
        if (action.route == Route.INNER && action.builtIn != null) {
            runBuiltIn(action.builtIn);
        }
    }

    private void postAction(@NonNull ActionSpec action) {
        if (action.textSupplier == null || module == null) {
            return;
        }
        String payload = action.textSupplier.get();
        ApiTraceLogger.text(OWNER, API_BT_SEND, "command",
                "id=" + action.id + "\npayload=" + payload);
        gateway.postText(module, payload);
        if (spec.rawLineConsumer != null) {
            spec.rawLineConsumer.onActionPosted(context, module, action, payload, gateway);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void runBuiltIn(@NonNull BuiltIn builtIn) {
        if (builtIn == BuiltIn.START_RECORD) {
            resetWidgets();
            widgetsActive = true;
            output.start(context, "uni_" + spec.id);
            setRecordState(true);
            setBottomInfo("Recording started");
        } else if (builtIn == BuiltIn.STOP_RECORD) {
            widgetsActive = false;
            output.stop();
            setRecordState(false);
            setBottomInfo("Samples: " + output.sampleCount());
        } else if (builtIn == BuiltIn.EXPORT) {
            setBottomInfo(output.exportPath());
        } else if (builtIn == BuiltIn.CLEAR_MESSAGES) {
            messages.clear();
            adapter.notifyDataSetChanged();
            readBytes = 0;
            updateByteCounter();
        } else if (builtIn == BuiltIn.RESET_WIDGETS) {
            resetWidgets();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void onBtData(@NonNull CgmBluetoothEvent.BTData data) {
        module = data.module;
        readBytes += data.bytes.length;
        updateByteCounter();

        Codec.Options rawOptions = new Codec.Options(
                AppApi.settingsStore().textEncoding(),
                false,
                false,
                false
        );
        String rawText = Codec.decode(data.bytes, rawOptions);
        if (rawText == null || rawText.isEmpty()) {
            return;
        }
        String displayText = rawText.trim();

        ApiTraceLogger.text(OWNER, API_BT_RECV, "data",
                "bytes=" + data.bytes.length + "\ntext=" + rawText);

        if (!displayText.isEmpty()) {
            MessageItem item = new MessageItem(displayText, Analysis.getTime(), false, data.module, false);
            item.setDataEndNewline(true);
            messages.add(item);
            adapter.notifyItemInserted(messages.size() - 1);
            host.messageList().smoothScrollToPosition(messages.size() - 1);
        }

        if (spec.rawLineConsumer != null) {
            spec.rawLineConsumer.onLine(context, data.module, rawText, gateway);
        }

        BluetoothSample sample = parse(displayText);
        if (sample != null) {
            consume(sample);
        }

        if (readBytes > AUTO_CLEAR_BYTES) {
            messages.clear();
            adapter.notifyDataSetChanged();
            readBytes = 0;
            setBottomInfo("Auto cleared");
            updateByteCounter();
        }
    }

    @Nullable
    private BluetoothSample parse(@NonNull String text) {
        for (BluetoothSampleParser parser : spec.parsers) {
            BluetoothSample sample = parser.parse(text);
            if (sample != null) {
                return sample;
            }
        }
        return null;
    }

    private void consume(@NonNull BluetoothSample sample) {
        if (widgetsActive) {
            for (MetricWidget widget : widgets) {
                widget.onSample(sample);
            }
        }
        if (output.isRecording() && spec.recordFormatter != null) {
            output.appendJsonLine(spec.recordFormatter.format(sample));
        }
    }

    private void resetWidgets() {
        for (MetricWidget widget : widgets) {
            widget.reset();
        }
    }

    private void setRecordState(boolean recording) {
        TextView tv = indicators.get("record_state");
        if (tv != null) {
            tv.setText(recording ? "Record: ON" : "Record: OFF");
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(dp(12));
            bg.setColor(recording ? Color.parseColor("#FFE3F5E9") : Color.parseColor("#FFE8EDF2"));
            tv.setBackground(bg);
            tv.setTextColor(recording ? Color.parseColor("#FF2E7D32") : Color.parseColor("#FF5C6670"));
        }
    }

    @SuppressLint("SetTextI18n")
    private void updateByteCounter() {
        TextView tv = indicators.get("byte_counter");
        if (tv != null) {
            tv.setText("Read: " + readBytes + " B    Sent: " + sentBytes + " B");
        }
    }

    private void setBottomInfo(@Nullable String text) {
        host.bottomInfo().setText(text == null ? "" : text);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}

