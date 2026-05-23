package com.hc.mixthebluetooth.uni;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.single.BTPackage;
import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.activity.tool.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.BluetoothSampleParser;
import com.hc.mixthebluetooth.recyclerData.FragmentMessAdapter;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;
import com.hc.mixthebluetooth.uni.Widgets.MetricWidget;
import com.hc.mixthebluetooth.uni.Widgets.WidgetSpec;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class Controller {
    private static final int AUTO_CLEAR_BYTES = 400_000;

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

        TextView bottomInfo();
    }

    public interface Gateway {
        void postText(@NonNull DeviceModule module, @NonNull String text);

        default void onCacheFileReady(@NonNull File file) {
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
        void onLine(@NonNull Context context, @NonNull String line, @NonNull Gateway gateway);
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
    private final ArrayList<FragmentMessageItem> messages = new ArrayList<>();
    private final ArrayList<MetricWidget> widgets = new ArrayList<>();
    private final HashMap<String, TextView> indicators = new HashMap<>();

    private FragmentMessAdapter adapter;
    private DeviceModule module;
    private int readBytes;
    private int sentBytes;
    private boolean widgetsActive;

    public Controller(@NonNull Context context, @NonNull ProfileSpec spec, @NonNull HostView host, @NonNull Gateway gateway) {
        this.context = context;
        this.spec = spec;
        this.host = host;
        this.gateway = gateway;
    }

    public void init() {
        adapter = new FragmentMessAdapter(context, messages, R.layout.item_message_fragment);
        host.messageList().setLayoutManager(new LinearLayoutManager(context));
        host.messageList().setAdapter(adapter);
        createActions();
        createSystemIndicators();
        createWidgets();
        setBottomInfo("Ready");
    }

    public void onEvent(@Nullable Object event) {
        if (event instanceof BTPackage.BTData) {
            onBtData((BTPackage.BTData) event);
        } else if (event instanceof BTPackage.Connected) {
            module = ((BTPackage.Connected) event).module;
        } else if (event instanceof BTPackage.Disconnected) {
            module = null;
        } else if (event instanceof BTPackage.SentBytes) {
            sentBytes += ((BTPackage.SentBytes) event).count;
            updateByteCounter();
        } else if (event instanceof BTPackage.ConnectState) {
            setBottomInfo(((BTPackage.ConnectState) event).state);
        }
    }

    public void release() {
        output.release();
    }

    private void createActions() {
        for (ActionSpec action : spec.actions) {
            Button button = new Button(context);
            button.setAllCaps(false);
            button.setText(action.label);
            button.setOnClickListener(v -> handleAction(action));
            host.region(Region.ACTION).addView(button, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }
    }

    private void createSystemIndicators() {
        addIndicatorText("record_state", "Record: OFF");
        addIndicatorText("byte_counter", "Read: 0 B    Sent: 0 B");
    }

    private TextView addIndicatorText(@NonNull String id, @NonNull String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(Color.rgb(96, 96, 96));
        tv.setTextSize(15f);
        indicators.put(id, tv);
        host.region(Region.DEBUG).addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return tv;
    }

    private void createWidgets() {
        ArrayList<WidgetSpec> orderedWidgets = Widgets.orderedForDisplay(spec.widgets);
        for (WidgetSpec widgetSpec : orderedWidgets) {
            MetricWidget widget = Widgets.create(context, widgetSpec);
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
            gateway.postText(module, action.textSupplier.get());
            return;
        }
        if (action.route == Route.INNER && action.builtIn != null) {
            runBuiltIn(action.builtIn);
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
    private void onBtData(@NonNull BTPackage.BTData data) {
        module = data.module;
        readBytes += data.bytes.length;
        updateByteCounter();

        Codec.Options options = new Codec.Options(
                FragmentParameter.getInstance().getCodeFormat(context),
                false,
                false
        );
        String text = Codec.decode(data.bytes, options);
        if (text == null || text.isEmpty()) {
            return;
        }

        FragmentMessageItem item = new FragmentMessageItem(text, Analysis.getTime(), false, data.module, false);
        item.setDataEndNewline(true);
        messages.add(item);
        adapter.notifyItemInserted(messages.size() - 1);
        host.messageList().smoothScrollToPosition(messages.size() - 1);

        if (spec.rawLineConsumer != null) {
            spec.rawLineConsumer.onLine(context, text, gateway);
        }

        BluetoothSample sample = parse(text);
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

