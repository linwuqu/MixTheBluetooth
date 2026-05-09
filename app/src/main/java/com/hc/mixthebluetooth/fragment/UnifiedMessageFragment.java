package com.hc.mixthebluetooth.fragment;

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
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.activity.tool.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.BluetoothSampleParser;
import com.hc.mixthebluetooth.activity.tool.Profiles;
import com.hc.mixthebluetooth.activity.tool.SampleRecorder;
import com.hc.mixthebluetooth.activity.tool.chart.MetricWidgets;
import com.hc.mixthebluetooth.activity.tool.chart.MetricWidgets.MetricWidget;
import com.hc.mixthebluetooth.activity.tool.chart.MetricWidgets.WidgetSpec;
import com.hc.mixthebluetooth.databinding.FragmentUnifiedMessageBinding;
import com.hc.mixthebluetooth.recyclerData.FragmentMessAdapter;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class UnifiedMessageFragment extends BTFragment<FragmentUnifiedMessageBinding> {

    private MessageController controller;

    @Override
    protected void initChannels() {
        register(StaticConstants.CH_BT_EVENT);
    }

    @Override
    protected void initAllImpl(View view, Context context) {
        controller = new MessageController(
                requireContext(),
                Profiles.eis(),
                new BindingHost(viewBinding),
                new FragmentGateway()
        );
        controller.init();
    }

    @Override
    protected void updateStateImpl(String sign, Object data) {
        if (StaticConstants.CH_BT_EVENT.equals(sign) && controller != null) {
            controller.onEvent(data);
        }
    }

    @Override
    protected FragmentUnifiedMessageBinding getViewBinding() {
        return FragmentUnifiedMessageBinding.inflate(getLayoutInflater());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (controller != null) {
            controller.release();
        }
    }

    interface HostView {
        ViewGroup region(@NonNull Region region);

        RecyclerView messageList();

        TextView bottomInfo();
    }

    interface BluetoothGateway {
        void postText(@NonNull DeviceModule module, @NonNull String text);
    }

    private static final class BindingHost implements HostView {
        private final FragmentUnifiedMessageBinding binding;

        BindingHost(@NonNull FragmentUnifiedMessageBinding binding) {
            this.binding = binding;
        }

        @Override
        public ViewGroup region(@NonNull Region region) {
            if (region == Region.ACTION) return binding.actionRegion;
            if (region == Region.SUMMARY) return binding.summaryRegion;
            if (region == Region.MAIN) return binding.mainRegion;
            if (region == Region.SECONDARY) return binding.secondaryRegion;
            if (region == Region.DEBUG) return binding.debugRegion;
            return binding.contentRoot;
        }

        @Override
        public RecyclerView messageList() {
            return binding.recyclerMessage;
        }

        @Override
        public TextView bottomInfo() {
            return binding.tvBottomInfo;
        }
    }

    private final class FragmentGateway implements BluetoothGateway {
        @Override
        public void postText(@NonNull DeviceModule module, @NonNull String text) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            sendDataToActivity(StaticConstants.CMD_BT_POST, new BTPackage.BTPost(module, bytes));
        }

    }

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

        private ProfileSpec(@NonNull Builder b) {
            this.id = b.id;
            this.parsers = new ArrayList<>(b.parsers);
            this.actions = new ArrayList<>(b.actions);
            this.widgets = new ArrayList<>(b.widgets);
            this.recordFormatter = b.recordFormatter;
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

            public ProfileSpec build() {
                return new ProfileSpec(this);
            }
        }
    }

    static final class MessageController {
        private static final int AUTO_CLEAR_BYTES = 400_000;

        private final Context context;
        private final ProfileSpec spec;
        private final HostView host;
        private final BluetoothGateway gateway;
        private final SampleRecorder recorder = new SampleRecorder();
        private final ArrayList<FragmentMessageItem> messages = new ArrayList<>();
        private final ArrayList<MetricWidget> widgets = new ArrayList<>();
        private final HashMap<String, TextView> indicators = new HashMap<>();

        private FragmentMessAdapter adapter;
        private DeviceModule module;
        private int readBytes;
        private int sentBytes;
        private boolean widgetsActive;

        MessageController(@NonNull Context context, @NonNull ProfileSpec spec, @NonNull HostView host, @NonNull BluetoothGateway gateway) {
            this.context = context;
            this.spec = spec;
            this.host = host;
            this.gateway = gateway;
        }

        void init() {
            adapter = new FragmentMessAdapter(context, messages, R.layout.item_message_fragment);
            host.messageList().setLayoutManager(new LinearLayoutManager(context));
            host.messageList().setAdapter(adapter);
            createActions();
            createSystemIndicators();
            createWidgets();
            setBottomInfo("Ready");
        }

        void onEvent(@Nullable Object event) {
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

        void release() {
            recorder.release();
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
            ArrayList<WidgetSpec> orderedWidgets = MetricWidgets.orderedForDisplay(spec.widgets);
            for (WidgetSpec widgetSpec : orderedWidgets) {
                MetricWidget widget = MetricWidgets.create(context, widgetSpec);
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
                recorder.start(context, "unified_" + spec.id);
                setRecordState(true);
                setBottomInfo("Recording started");
            } else if (builtIn == BuiltIn.STOP_RECORD) {
                widgetsActive = false;
                recorder.stop();
                setRecordState(false);
                setBottomInfo("Samples: " + recorder.getSampleCount());
            } else if (builtIn == BuiltIn.EXPORT) {
                setBottomInfo(recorder.exportPath());
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

            String text = decode(data.bytes, FragmentParameter.getInstance().getCodeFormat(context));
            if (text == null || text.isEmpty()) {
                return;
            }

            FragmentMessageItem item = new FragmentMessageItem(text, Analysis.getTime(), false, data.module, false);
            item.setDataEndNewline(true);
            messages.add(item);
            adapter.notifyItemInserted(messages.size() - 1);
            host.messageList().smoothScrollToPosition(messages.size() - 1);

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
            if (recorder.isRecording() && spec.recordFormatter != null) {
                recorder.appendLine(spec.recordFormatter.format(sample));
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

        @Nullable
        private String decode(@Nullable byte[] bytes, @Nullable String code) {
            if (bytes == null || bytes.length == 0) return null;
            String text = Analysis.getByteToString(bytes.clone(), code != null ? code : "UTF-8", false, false);
            if (text == null) return null;
            text = text.replace("\u0000", "").trim();
            return text.isEmpty() ? null : text;
        }
    }
}
