package com.hc.mixthebluetooth.fragment;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.LineChart;
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
import com.hc.mixthebluetooth.activity.tool.SampleRecorderImpl;
import com.hc.mixthebluetooth.activity.tool.chart.RealtimeLineChart;
import com.hc.mixthebluetooth.databinding.FragmentUnifiedMessageBinding;
import com.hc.mixthebluetooth.recyclerData.FragmentMessAdapter;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        ViewGroup actionContainer();
        ViewGroup chartContainer();
        ViewGroup indicatorContainer();
        RecyclerView messageList();
        TextView bottomInfo();
    }

    interface BluetoothGateway {
        void postText(@NonNull DeviceModule module, @NonNull String text);
        void postBytes(@NonNull DeviceModule module, @NonNull byte[] bytes);
    }

    private static final class BindingHost implements HostView {
        private final FragmentUnifiedMessageBinding binding;

        BindingHost(@NonNull FragmentUnifiedMessageBinding binding) {
            this.binding = binding;
        }

        @Override public ViewGroup actionContainer() { return binding.actionContainer; }
        @Override public ViewGroup chartContainer() { return binding.chartContainer; }
        @Override public ViewGroup indicatorContainer() { return binding.indicatorContainer; }
        @Override public RecyclerView messageList() { return binding.recyclerMessage; }
        @Override public TextView bottomInfo() { return binding.tvBottomInfo; }
    }

    private final class FragmentGateway implements BluetoothGateway {
        @Override
        public void postText(@NonNull DeviceModule module, @NonNull String text) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            sendDataToActivity(StaticConstants.CMD_BT_POST, new BTPackage.BTPost(module, bytes));
        }

        @Override
        public void postBytes(@NonNull DeviceModule module, @NonNull byte[] bytes) {
            sendDataToActivity(StaticConstants.CMD_BT_POST, new BTPackage.BTPost(module, bytes.clone()));
        }
    }

    public enum Route {
        POST,
        SUBSCRIBE,
        INNER
    }

    public enum BuiltIn {
        START_RECORD,
        STOP_RECORD,
        EXPORT,
        CLEAR_MESSAGES,
        RESET_CHARTS
    }

    public enum ChartType {
        LINE
    }

    public static final class ActionSpec {
        @NonNull public final String id;
        @NonNull public final String label;
        @NonNull public final Route route;
        @Nullable public final TextSupplier textSupplier;
        @Nullable public final BuiltIn builtIn;

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

    public static final class ChartSpec {
        @NonNull public final String id;
        @NonNull public final String title;
        @NonNull public final String metricKey;
        @NonNull public final ChartType type;
        public final int color;

        private ChartSpec(@NonNull String id, @NonNull String title, @NonNull String metricKey, @NonNull ChartType type, int color) {
            this.id = id;
            this.title = title;
            this.metricKey = metricKey;
            this.type = type;
            this.color = color;
        }

        public static ChartSpec line(@NonNull String id, @NonNull String title, @NonNull String metricKey, int color) {
            return new ChartSpec(id, title, metricKey, ChartType.LINE, color);
        }
    }

    public static final class IndicatorSpec {
        @NonNull public final String id;
        @NonNull public final String label;
        @Nullable public final String metricKey;

        private IndicatorSpec(@NonNull String id, @NonNull String label, @Nullable String metricKey) {
            this.id = id;
            this.label = label;
            this.metricKey = metricKey;
        }

        public static IndicatorSpec metric(@NonNull String id, @NonNull String label, @NonNull String metricKey) {
            return new IndicatorSpec(id, label, metricKey);
        }
    }

    public interface TextSupplier {
        @NonNull String get();
    }

    public interface RecordFormatter {
        @NonNull String format(@NonNull BluetoothSample sample);
    }

    public static final class ProfileSpec {
        @NonNull public final String id;
        @NonNull public final List<BluetoothSampleParser> parsers;
        @NonNull public final List<ActionSpec> actions;
        @NonNull public final List<ChartSpec> charts;
        @NonNull public final List<IndicatorSpec> indicators;
        @Nullable public final RecordFormatter recordFormatter;

        private ProfileSpec(@NonNull Builder b) {
            this.id = b.id;
            this.parsers = new ArrayList<>(b.parsers);
            this.actions = new ArrayList<>(b.actions);
            this.charts = new ArrayList<>(b.charts);
            this.indicators = new ArrayList<>(b.indicators);
            this.recordFormatter = b.recordFormatter;
        }

        public static Builder builder(@NonNull String id) {
            return new Builder(id);
        }

        public static final class Builder {
            @NonNull private final String id;
            private final List<BluetoothSampleParser> parsers = new ArrayList<>();
            private final List<ActionSpec> actions = new ArrayList<>();
            private final List<ChartSpec> charts = new ArrayList<>();
            private final List<IndicatorSpec> indicators = new ArrayList<>();
            @Nullable private RecordFormatter recordFormatter;

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

            public Builder chart(@NonNull ChartSpec chart) {
                charts.add(chart);
                return this;
            }

            public Builder indicator(@NonNull IndicatorSpec indicator) {
                indicators.add(indicator);
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
        private final SampleRecorder recorder = new SampleRecorderImpl();
        private final ArrayList<FragmentMessageItem> messages = new ArrayList<>();
        private final HashMap<String, RealtimeLineChart> charts = new HashMap<>();
        private final HashMap<String, TextView> indicators = new HashMap<>();

        private FragmentMessAdapter adapter;
        private DeviceModule module;
        private int readBytes;
        private int sentBytes;

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
            createSystemIndicators();
            createIndicators();
            createActions();
            createCharts();
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

        private void createSystemIndicators() {
            addIndicatorText("record_state", "Record: OFF");
            addIndicatorText("byte_counter", "Read: 0 B    Sent: 0 B");
        }

        private void createIndicators() {
            for (IndicatorSpec indicator : spec.indicators) {
                TextView tv = addIndicatorText(indicator.id, indicator.label + ": --");
                indicators.put(indicator.id, tv);
            }
        }

        private TextView addIndicatorText(@NonNull String id, @NonNull String text) {
            TextView tv = new TextView(context);
            tv.setText(text);
            host.indicatorContainer().addView(tv);
            indicators.put(id, tv);
            return tv;
        }

        private void createActions() {
            for (ActionSpec action : spec.actions) {
                Button button = new Button(context);
                button.setText(action.label);
                button.setOnClickListener(v -> handleAction(action));
                host.actionContainer().addView(button);
            }
        }

        private void createCharts() {
            for (ChartSpec chartSpec : spec.charts) {
                LinearLayout box = new LinearLayout(context);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setBackgroundResource(R.drawable.window_back);
                LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(170)
                );
                boxParams.setMargins(0, dp(6), 0, dp(8));
                host.chartContainer().addView(box, boxParams);

                LineChart chartView = new LineChart(context);
                box.addView(chartView, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
                RealtimeLineChart chart = new RealtimeLineChart(
                        chartView,
                        new RealtimeLineChart.Config.Builder()
                                .label(chartSpec.title)
                                .color(chartSpec.color)
                                .maxPoints(500)
                                .visibleWindowSeconds(60f)
                                .build()
                );
                charts.put(chartSpec.metricKey, chart);
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

        private void runBuiltIn(@NonNull BuiltIn builtIn) {
            if (builtIn == BuiltIn.START_RECORD) {
                resetCharts();
                recorder.start(context, "unified_" + spec.id);
                setRecordState(true);
                setBottomInfo("Recording started");
            } else if (builtIn == BuiltIn.STOP_RECORD) {
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
            } else if (builtIn == BuiltIn.RESET_CHARTS) {
                resetCharts();
            }
        }

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
            for (Map.Entry<String, Float> e : sample.metrics().entrySet()) {
                RealtimeLineChart chart = charts.get(e.getKey());
                if (chart != null && recorder.isRecording()) {
                    chart.append(e.getValue());
                }
                for (IndicatorSpec indicator : spec.indicators) {
                    if (e.getKey().equals(indicator.metricKey)) {
                        TextView tv = indicators.get(indicator.id);
                        if (tv != null) {
                            tv.setText(indicator.label + ": " + e.getValue());
                        }
                    }
                }
            }
            if (recorder.isRecording() && spec.recordFormatter != null) {
                recorder.appendLine(spec.recordFormatter.format(sample));
            }
        }

        private void resetCharts() {
            for (RealtimeLineChart chart : charts.values()) {
                chart.reset();
            }
        }

        private void setRecordState(boolean recording) {
            TextView tv = indicators.get("record_state");
            if (tv != null) {
                tv.setText(recording ? "Record: ON" : "Record: OFF");
            }
        }

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
            return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
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
