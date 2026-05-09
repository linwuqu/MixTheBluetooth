# Unified Message Widget Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `UnifiedMessageFragment` 第二阶段升级为 Region + WidgetSpec 组件生态，清理旧 profile/consumer 模型，并让 EIS 页面同时展示折线图、圆环、数值卡和统计卡。

**Architecture:** `UnifiedMessageFragment` 继续作为 Android 壳和 controller/spec 集合文件；`MetricWidgets` 负责创建可复用数据组件；`Profiles.eis()` 只声明 parser、action、widget 和 recorder formatter，不认识 ViewBinding。XML 只提供 Region 容器，Controller 按 `region + order` 插入 widget，并把 `BluetoothSample` 分发给 widget 与 recorder。

**Tech Stack:** Android Java、ViewBinding、MPAndroidChart `LineChart`、现有 `CircleProgressView`、JUnit4、Gradle Android plugin。

---

## Current Workspace Note

当前工作区已有两个未提交代码改动：

- `app/src/main/java/com/hc/mixthebluetooth/activity/single/StaticConstants.java`
- `app/src/main/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragment.java`

执行本计划时不要使用 `git reset --hard` 或 `git checkout --` 回滚它们。后续任务会修改 `UnifiedMessageFragment.java`，实现者需要把现有改动作为现场内容继续编辑。

## File Structure

### Keep And Modify

- `app/src/main/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragment.java`
  - 保留 Fragment、HostView、BluetoothGateway、ActionSpec、ProfileSpec、MessageController。
  - 新增 `Region`。
  - `ProfileSpec` 从 `charts/indicators` 改为 `widgets`。
  - `MessageController` 从直接创建 `LineChart` 改为调用 `MetricWidgets.create(...)`。

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/Profiles.java`
  - EIS parser 和 recorder formatter 继续保留。
  - EIS sample 类可在清理阶段合并进本文件。
  - EIS 注册两个 line widget、一个 gauge widget、一个 value widget、一个 stats widget。

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/BluetoothSample.java`
  - 保持极小接口。

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/BluetoothSampleParser.java`
  - 保持极小接口。

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/SampleRecorder.java`
  - 从 interface 改为 concrete class。
  - 承接原 `SampleRecorderImpl` 的异步写 jsonl 文件能力。

- `app/src/main/res/layout/fragment_unified_message.xml`
  - 改为 Region 容器：`actionRegion`、`summaryRegion`、`mainRegion`、`secondaryRegion`、`debugRegion`、`bottomRegion`。

- `app/src/test/java/com/hc/mixthebluetooth/activity/tool/ProfilesTest.java`
  - 更新为 WidgetSpec 断言。

### Create

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/chart/MetricWidgets.java`
  - 单文件承载初期 widget 生态：
    - `WidgetKind`
    - `WidgetStyle`
    - `WidgetSpec`
    - `MetricWidget`
    - `LineMetricWidget`
    - `GaugeMetricWidget`
    - `ValueMetricWidget`
    - `StatsMetricWidget`
    - `MetricWidgets.create(...)`

- `app/src/test/java/com/hc/mixthebluetooth/activity/tool/chart/MetricWidgetsTest.java`
  - 测试 `WidgetSpec` builder、默认值、排序相关字段。

### Delete After Migration

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/SampleRecorderImpl.java`
- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/SampleConsumer.java`
- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/DeviceProfile.java`
- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/EisProfile.java`
- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/EisProfileNew.java`
- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/EisSample.java`
- `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessageNew.java`
- `app/src/main/res/layout/fragment_message_new.xml`

### Keep As Legacy For This Plan

- `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessage.java`
- `app/src/main/res/layout/fragment_message.xml`
- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/CgmProfile.java`
- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/CgmSample.java`

这些类属于旧页或未迁移 CGM，不在本计划中删除。

---

## Task 0: Preflight And Baseline

**Files:**
- Read: `app/src/main/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragment.java`
- Read: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/Profiles.java`
- Read: `app/src/main/res/layout/fragment_unified_message.xml`
- Read: `app/src/test/java/com/hc/mixthebluetooth/activity/tool/ProfilesTest.java`

- [ ] **Step 1: Inspect dirty workspace**

Run:

```powershell
git status --short --branch
git diff -- app\src\main\java\com\hc\mixthebluetooth\activity\single\StaticConstants.java
git diff -- app\src\main\java\com\hc\mixthebluetooth\fragment\UnifiedMessageFragment.java
```

Expected:

```text
## dev-1.3
 M app/src/main/java/com/hc/mixthebluetooth/activity/single/StaticConstants.java
 M app/src/main/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragment.java
```

- [ ] **Step 2: Run current baseline tests**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
.\gradlew.bat :app:testDebugUnitTest
```

Expected: both commands finish with `BUILD SUCCESSFUL`.

- [ ] **Step 3: Do not commit baseline**

No commit in this task. It only verifies the current state before changing behavior.

---

## Task 1: Add WidgetSpec Unit Tests First

**Files:**
- Create: `app/src/test/java/com/hc/mixthebluetooth/activity/tool/chart/MetricWidgetsTest.java`
- Modify: `app/src/test/java/com/hc/mixthebluetooth/activity/tool/ProfilesTest.java`

- [ ] **Step 1: Create failing tests for WidgetSpec**

Create `app/src/test/java/com/hc/mixthebluetooth/activity/tool/chart/MetricWidgetsTest.java` with:

```java
package com.hc.mixthebluetooth.activity.tool.chart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.Region;

import org.junit.Test;

public class MetricWidgetsTest {

    @Test
    public void lineWidgetSpecKeepsMetricRegionAndOrder() {
        MetricWidgets.WidgetSpec spec = MetricWidgets.WidgetSpec.line("eis_ohm")
                .title("EIS Ohm")
                .metric("ohm")
                .unit("Ω")
                .region(Region.MAIN)
                .order(20)
                .lineColor(0xFFFF0000)
                .yRange(0f, 100f)
                .build();

        assertEquals("eis_ohm", spec.id);
        assertEquals(MetricWidgets.WidgetKind.LINE, spec.kind);
        assertEquals("EIS Ohm", spec.title);
        assertEquals("ohm", spec.metricKey);
        assertEquals("Ω", spec.unit);
        assertEquals(Region.MAIN, spec.region);
        assertEquals(20, spec.order);
        assertEquals(0xFFFF0000, spec.color);
        assertEquals(Float.valueOf(0f), spec.yMin);
        assertEquals(Float.valueOf(100f), spec.yMax);
    }

    @Test
    public void gaugeWidgetSpecHasSummaryDefaults() {
        MetricWidgets.WidgetSpec spec = MetricWidgets.WidgetSpec.gauge("conductance")
                .title("电导率")
                .metric("us")
                .unit("uS")
                .gaugeMax(10f)
                .build();

        assertEquals(MetricWidgets.WidgetKind.GAUGE, spec.kind);
        assertEquals(Region.SUMMARY, spec.region);
        assertEquals(0, spec.order);
        assertEquals(Float.valueOf(10f), spec.gaugeMax);
        assertNull(spec.yMin);
        assertNull(spec.yMax);
    }
}
```

- [ ] **Step 2: Replace ProfilesTest with widget expectations**

Replace `app/src/test/java/com/hc/mixthebluetooth/activity/tool/ProfilesTest.java` with:

```java
package com.hc.mixthebluetooth.activity.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.hc.mixthebluetooth.activity.tool.chart.MetricWidgets;
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.BuiltIn;
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.ProfileSpec;
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.Region;
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.Route;

import org.junit.Test;

public class ProfilesTest {

    @Test
    public void eisProfileDeclaresActionsAndFiveWidgets() {
        ProfileSpec spec = Profiles.eis();

        assertEquals("eis", spec.id);
        assertEquals(1, spec.parsers.size());
        assertEquals(3, spec.actions.size());
        assertEquals(Route.INNER, spec.actions.get(0).route);
        assertEquals(BuiltIn.START_RECORD, spec.actions.get(0).builtIn);
        assertEquals(BuiltIn.STOP_RECORD, spec.actions.get(1).builtIn);
        assertEquals(BuiltIn.EXPORT, spec.actions.get(2).builtIn);

        assertEquals(5, spec.widgets.size());
        assertWidget(spec.widgets.get(0), "eis_conductance_gauge", MetricWidgets.WidgetKind.GAUGE, "us", Region.SUMMARY);
        assertWidget(spec.widgets.get(1), "eis_ohm_value", MetricWidgets.WidgetKind.VALUE, "ohm", Region.SUMMARY);
        assertWidget(spec.widgets.get(2), "eis_ohm_line", MetricWidgets.WidgetKind.LINE, "ohm", Region.MAIN);
        assertWidget(spec.widgets.get(3), "eis_us_line", MetricWidgets.WidgetKind.LINE, "us", Region.MAIN);
        assertWidget(spec.widgets.get(4), "eis_ohm_stats", MetricWidgets.WidgetKind.STATS, "ohm", Region.MAIN);
    }

    @Test
    public void eisParserProducesOhmAndUsMetrics() {
        BluetoothSample sample = Profiles.eis().parsers.get(0).parse("670258.375Ω,1.492uS");

        assertNotNull(sample);
        assertEquals(Profiles.EisSample.TYPE, sample.type());
        assertEquals(670258.375f, sample.metrics().get(Profiles.EisSample.METRIC_OHM), 0.001f);
        assertEquals(1.492f, sample.metrics().get(Profiles.EisSample.METRIC_US), 0.001f);
    }

    @Test
    public void eisRecordJsonIncludesBothMetricsAndEscapedRawText() {
        BluetoothSample sample = new Profiles.EisSample(12.5f, 3.25f, "12.5Ω,\"3.25uS\"\n");

        String json = Profiles.eisJson(sample);

        assertTrue(json.contains("\"ohm\":12.5"));
        assertTrue(json.contains("\"us\":3.25"));
        assertTrue(json.contains("\"raw\":\"12.5Ω,\\\"3.25uS\\\"\\n\""));
    }

    private static void assertWidget(
            MetricWidgets.WidgetSpec widget,
            String id,
            MetricWidgets.WidgetKind kind,
            String metricKey,
            Region region
    ) {
        assertEquals(id, widget.id);
        assertEquals(kind, widget.kind);
        assertEquals(metricKey, widget.metricKey);
        assertEquals(region, widget.region);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.activity.tool.chart.MetricWidgetsTest" --tests "com.hc.mixthebluetooth.activity.tool.ProfilesTest"
```

Expected: FAIL because `MetricWidgets`, `Region`, `ProfileSpec.widgets`, and `Profiles.EisSample` are not implemented yet.

- [ ] **Step 4: Commit failing tests**

Do not commit failing tests separately. Keep them uncommitted until Task 2 makes them pass.

---

## Task 2: Add MetricWidgets And WidgetSpec

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/chart/MetricWidgets.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragment.java`
- Test: `app/src/test/java/com/hc/mixthebluetooth/activity/tool/chart/MetricWidgetsTest.java`

- [ ] **Step 1: Add Region enum to UnifiedMessageFragment**

In `app/src/main/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragment.java`, add this enum near `Route`:

```java
public enum Region {
    ACTION,
    SUMMARY,
    MAIN,
    SECONDARY,
    DEBUG,
    BOTTOM
}
```

- [ ] **Step 2: Create MetricWidgets.java**

Create `app/src/main/java/com/hc/mixthebluetooth/activity/tool/chart/MetricWidgets.java` with:

```java
package com.hc.mixthebluetooth.activity.tool.chart;

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

import com.github.mikephil.charting.charts.LineChart;
import com.hc.mixthebluetooth.activity.tool.BluetoothSample;
import com.hc.mixthebluetooth.customView.CircleProgressView;
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.Region;

import java.util.Locale;

public final class MetricWidgets {
    private MetricWidgets() {
    }

    public enum WidgetKind {
        LINE,
        GAUGE,
        VALUE,
        STATS
    }

    public enum WidgetStyle {
        CARD,
        HERO,
        COMPACT
    }

    public interface MetricWidget {
        @NonNull View view();
        void onSample(@NonNull BluetoothSample sample);
        void reset();
    }

    public static MetricWidget create(@NonNull Context context, @NonNull WidgetSpec spec) {
        if (spec.kind == WidgetKind.LINE) return new LineMetricWidget(context, spec);
        if (spec.kind == WidgetKind.GAUGE) return new GaugeMetricWidget(context, spec);
        if (spec.kind == WidgetKind.VALUE) return new ValueMetricWidget(context, spec);
        if (spec.kind == WidgetKind.STATS) return new StatsMetricWidget(context, spec);
        return new ValueMetricWidget(context, spec);
    }

    public static final class WidgetSpec {
        @NonNull public final String id;
        @NonNull public final WidgetKind kind;
        @NonNull public final String title;
        @NonNull public final String metricKey;
        @NonNull public final String unit;
        @NonNull public final Region region;
        @NonNull public final WidgetStyle style;
        public final int order;
        public final int color;
        public final int maxPoints;
        public final float visibleWindowSeconds;
        @Nullable public final Float yMin;
        @Nullable public final Float yMax;
        @Nullable public final Float gaugeMax;

        private WidgetSpec(@NonNull Builder b) {
            id = b.id;
            kind = b.kind;
            title = b.title;
            metricKey = b.metricKey;
            unit = b.unit;
            region = b.region;
            style = b.style;
            order = b.order;
            color = b.color;
            maxPoints = b.maxPoints;
            visibleWindowSeconds = b.visibleWindowSeconds;
            yMin = b.yMin;
            yMax = b.yMax;
            gaugeMax = b.gaugeMax;
        }

        public static Builder line(@NonNull String id) {
            return new Builder(id, WidgetKind.LINE).region(Region.MAIN).style(WidgetStyle.CARD);
        }

        public static Builder gauge(@NonNull String id) {
            return new Builder(id, WidgetKind.GAUGE).region(Region.SUMMARY).style(WidgetStyle.HERO);
        }

        public static Builder value(@NonNull String id) {
            return new Builder(id, WidgetKind.VALUE).region(Region.SUMMARY).style(WidgetStyle.COMPACT);
        }

        public static Builder stats(@NonNull String id) {
            return new Builder(id, WidgetKind.STATS).region(Region.MAIN).style(WidgetStyle.COMPACT);
        }
    }

    public static final class Builder {
        @NonNull private final String id;
        @NonNull private final WidgetKind kind;
        @NonNull private String title = "";
        @NonNull private String metricKey = "";
        @NonNull private String unit = "";
        @NonNull private Region region;
        @NonNull private WidgetStyle style = WidgetStyle.CARD;
        private int order = 0;
        private int color = Color.rgb(78, 224, 151);
        private int maxPoints = 500;
        private float visibleWindowSeconds = 60f;
        @Nullable private Float yMin = null;
        @Nullable private Float yMax = null;
        @Nullable private Float gaugeMax = null;

        private Builder(@NonNull String id, @NonNull WidgetKind kind) {
            this.id = id;
            this.kind = kind;
            this.region = kind == WidgetKind.GAUGE || kind == WidgetKind.VALUE ? Region.SUMMARY : Region.MAIN;
        }

        public Builder title(@NonNull String title) { this.title = title; return this; }
        public Builder metric(@NonNull String metricKey) { this.metricKey = metricKey; return this; }
        public Builder unit(@NonNull String unit) { this.unit = unit; return this; }
        public Builder region(@NonNull Region region) { this.region = region; return this; }
        public Builder style(@NonNull WidgetStyle style) { this.style = style; return this; }
        public Builder order(int order) { this.order = order; return this; }
        public Builder lineColor(int color) { this.color = color; return this; }
        public Builder maxPoints(int maxPoints) { this.maxPoints = maxPoints; return this; }
        public Builder visibleWindowSeconds(float seconds) { this.visibleWindowSeconds = seconds; return this; }
        public Builder yRange(float min, float max) { this.yMin = min; this.yMax = max; return this; }
        public Builder gaugeMax(float max) { this.gaugeMax = max; return this; }
        public WidgetSpec build() { return new WidgetSpec(this); }
    }

    private static final class LineMetricWidget implements MetricWidget {
        private final LinearLayout root;
        private final RealtimeLineChart chart;

        LineMetricWidget(@NonNull Context context, @NonNull WidgetSpec spec) {
            root = card(context, LinearLayout.VERTICAL);
            root.addView(title(context, spec.title), matchWrap());
            LineChart chartView = new LineChart(context);
            root.addView(chartView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context, 210)
            ));
            RealtimeLineChart.Config.Builder builder = new RealtimeLineChart.Config.Builder()
                    .label(spec.title)
                    .color(spec.color)
                    .maxPoints(spec.maxPoints)
                    .visibleWindowSeconds(spec.visibleWindowSeconds);
            if (spec.yMin != null && spec.yMax != null) builder.yRange(spec.yMin, spec.yMax);
            chart = new RealtimeLineChart(chartView, builder.build());
        }

        @NonNull @Override public View view() { return root; }

        @Override public void onSample(@NonNull BluetoothSample sample) {
            Float value = sample.metrics().get(root.getTag());
            if (value != null) chart.append(value);
        }

        @Override public void reset() { chart.reset(); }
    }

    private static final class GaugeMetricWidget implements MetricWidget {
        private final LinearLayout root;
        private final WidgetSpec spec;
        private final CircleProgressView gauge;

        GaugeMetricWidget(@NonNull Context context, @NonNull WidgetSpec spec) {
            this.spec = spec;
            root = card(context, LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.addView(title(context, spec.title), matchWrap());
            gauge = new CircleProgressView(context);
            gauge.setUnitText(spec.unit);
            gauge.setProgressColor(spec.color);
            gauge.setValueText("--");
            root.addView(gauge, new LinearLayout.LayoutParams(dp(context, 132), dp(context, 132)));
        }

        @NonNull @Override public View view() { return root; }

        @Override public void onSample(@NonNull BluetoothSample sample) {
            Float value = sample.metrics().get(spec.metricKey);
            if (value == null) return;
            gauge.setValueText(format(value));
            float max = spec.gaugeMax == null || spec.gaugeMax <= 0f ? 1f : spec.gaugeMax;
            gauge.setProgress(Math.max(0f, Math.min(1f, value / max)));
        }

        @Override public void reset() {
            gauge.setValueText("--");
            gauge.setProgress(0f);
        }
    }

    private static final class ValueMetricWidget implements MetricWidget {
        private final LinearLayout root;
        private final WidgetSpec spec;
        private final TextView value;

        ValueMetricWidget(@NonNull Context context, @NonNull WidgetSpec spec) {
            this.spec = spec;
            root = card(context, LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.addView(title(context, spec.title), matchWrap());
            value = new TextView(context);
            value.setText("-- " + spec.unit);
            value.setTextColor(Color.rgb(30, 30, 30));
            value.setTextSize(22f);
            value.setTypeface(Typeface.DEFAULT_BOLD);
            root.addView(value, matchWrap());
        }

        @NonNull @Override public View view() { return root; }

        @Override public void onSample(@NonNull BluetoothSample sample) {
            Float v = sample.metrics().get(spec.metricKey);
            if (v != null) value.setText(format(v) + " " + spec.unit);
        }

        @Override public void reset() { value.setText("-- " + spec.unit); }
    }

    private static final class StatsMetricWidget implements MetricWidget {
        private final LinearLayout root;
        private final WidgetSpec spec;
        private final TextView maxView;
        private final TextView minView;
        private final TextView ampView;
        @Nullable private Float max = null;
        @Nullable private Float min = null;

        StatsMetricWidget(@NonNull Context context, @NonNull WidgetSpec spec) {
            this.spec = spec;
            root = card(context, LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            maxView = statText(context, "Max: --");
            minView = statText(context, "Min: --");
            ampView = statText(context, "Amp: --");
            root.addView(maxView, weight());
            root.addView(minView, weight());
            root.addView(ampView, weight());
        }

        @NonNull @Override public View view() { return root; }

        @Override public void onSample(@NonNull BluetoothSample sample) {
            Float v = sample.metrics().get(spec.metricKey);
            if (v == null) return;
            max = max == null ? v : Math.max(max, v);
            min = min == null ? v : Math.min(min, v);
            float amp = max - min;
            maxView.setText("Max: " + format(max) + " " + spec.unit);
            minView.setText("Min: " + format(min) + " " + spec.unit);
            ampView.setText("Amp: " + format(amp) + " " + spec.unit);
        }

        @Override public void reset() {
            max = null;
            min = null;
            maxView.setText("Max: --");
            minView.setText("Min: --");
            ampView.setText("Amp: --");
        }
    }

    private static LinearLayout card(@NonNull Context context, int orientation) {
        LinearLayout v = new LinearLayout(context);
        v.setOrientation(orientation);
        v.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(context, 10));
        v.setBackground(bg);
        v.setElevation(dp(context, 2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 8));
        v.setLayoutParams(lp);
        return v;
    }

    private static TextView title(@NonNull Context context, @NonNull String text) {
        TextView v = new TextView(context);
        v.setText(text);
        v.setTextColor(Color.rgb(20, 20, 20));
        v.setTextSize(17f);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private static TextView statText(@NonNull Context context, @NonNull String text) {
        TextView v = new TextView(context);
        v.setText(text);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(Color.rgb(96, 96, 96));
        v.setTextSize(13f);
        return v;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private static int dp(@NonNull Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    private static String format(float value) {
        if (Math.abs(value) >= 100f) return String.format(Locale.getDefault(), "%.1f", value);
        return String.format(Locale.getDefault(), "%.3f", value);
    }
}
```

- [ ] **Step 3: Fix LineMetricWidget metric lookup**

In `LineMetricWidget`, add a `WidgetSpec spec` field and use it in `onSample`.

Replace the class field block:

```java
private final LinearLayout root;
private final RealtimeLineChart chart;
```

with:

```java
private final LinearLayout root;
private final WidgetSpec spec;
private final RealtimeLineChart chart;
```

Then in the constructor add:

```java
this.spec = spec;
```

Replace:

```java
Float value = sample.metrics().get(root.getTag());
```

with:

```java
Float value = sample.metrics().get(spec.metricKey);
```

- [ ] **Step 4: Run WidgetSpec tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.activity.tool.chart.MetricWidgetsTest"
```

Expected: PASS.

- [ ] **Step 5: Commit MetricWidgets**

Run:

```powershell
git add app\src\main\java\com\hc\mixthebluetooth\activity\tool\chart\MetricWidgets.java app\src\main\java\com\hc\mixthebluetooth\fragment\UnifiedMessageFragment.java app\src\test\java\com\hc\mixthebluetooth\activity\tool\chart\MetricWidgetsTest.java
git commit -m "feat: add metric widget specs"
```

---

## Task 3: Convert UnifiedMessageFragment To Regions And Widgets

**Files:**
- Modify: `app/src/main/res/layout/fragment_unified_message.xml`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragment.java`
- Test: `app/src/test/java/com/hc/mixthebluetooth/activity/tool/ProfilesTest.java`

- [ ] **Step 1: Replace unified layout containers**

Replace `app/src/main/res/layout/fragment_unified_message.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#F7F8FA"
    android:keepScreenOn="true">

    <LinearLayout
        android:id="@+id/actionRegion"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:paddingStart="10dp"
        android:paddingTop="8dp"
        android:paddingEnd="10dp"
        android:paddingBottom="6dp"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <ScrollView
        android:id="@+id/contentScroll"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:fillViewport="false"
        app:layout_constraintBottom_toTopOf="@id/recyclerMessage"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/actionRegion">

        <LinearLayout
            android:id="@+id/contentRoot"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingBottom="8dp">

            <LinearLayout
                android:id="@+id/summaryRegion"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:paddingStart="4dp"
                android:paddingEnd="4dp" />

            <LinearLayout
                android:id="@+id/mainRegion"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical" />

            <LinearLayout
                android:id="@+id/secondaryRegion"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical" />

            <LinearLayout
                android:id="@+id/debugRegion"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical" />
        </LinearLayout>
    </ScrollView>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerMessage"
        android:layout_width="0dp"
        android:layout_height="96dp"
        android:clipToPadding="false"
        android:paddingBottom="6dp"
        app:layout_constraintBottom_toTopOf="@id/tvBottomInfo"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent" />

    <TextView
        android:id="@+id/tvBottomInfo"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:background="@drawable/window_back"
        android:padding="10dp"
        android:text="Ready"
        android:textColor="@color/black"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

- [ ] **Step 2: Update HostView interface**

In `UnifiedMessageFragment.java`, replace `HostView` with:

```java
interface HostView {
    ViewGroup region(@NonNull Region region);
    RecyclerView messageList();
    TextView bottomInfo();
}
```

- [ ] **Step 3: Update BindingHost**

Replace `BindingHost` methods with:

```java
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
```

- [ ] **Step 4: Replace ChartSpec and IndicatorSpec imports/state**

Remove this import from `UnifiedMessageFragment.java`:

```java
import com.github.mikephil.charting.charts.LineChart;
import com.hc.mixthebluetooth.activity.tool.SampleRecorderImpl;
import com.hc.mixthebluetooth.activity.tool.chart.RealtimeLineChart;
```

Add:

```java
import com.hc.mixthebluetooth.activity.tool.chart.MetricWidgets;
import com.hc.mixthebluetooth.activity.tool.chart.MetricWidgets.MetricWidget;
import com.hc.mixthebluetooth.activity.tool.chart.MetricWidgets.WidgetSpec;
```

Replace controller fields:

```java
private final SampleRecorder recorder = new SampleRecorderImpl();
private final HashMap<String, RealtimeLineChart> charts = new HashMap<>();
private final HashMap<String, TextView> indicators = new HashMap<>();
```

with:

```java
private final SampleRecorder recorder = new SampleRecorder();
private final ArrayList<MetricWidget> widgets = new ArrayList<>();
private final HashMap<String, TextView> indicators = new HashMap<>();
```

- [ ] **Step 5: Replace ProfileSpec chart/indicator fields**

In `ProfileSpec`, replace:

```java
@NonNull public final List<ChartSpec> charts;
@NonNull public final List<IndicatorSpec> indicators;
```

with:

```java
@NonNull public final List<WidgetSpec> widgets;
```

In the constructor, replace:

```java
this.charts = new ArrayList<>(b.charts);
this.indicators = new ArrayList<>(b.indicators);
```

with:

```java
this.widgets = new ArrayList<>(b.widgets);
```

In `Builder`, replace:

```java
private final List<ChartSpec> charts = new ArrayList<>();
private final List<IndicatorSpec> indicators = new ArrayList<>();
```

with:

```java
private final List<WidgetSpec> widgets = new ArrayList<>();
```

Replace builder methods:

```java
public Builder chart(@NonNull ChartSpec chart) {
    charts.add(chart);
    return this;
}

public Builder indicator(@NonNull IndicatorSpec indicator) {
    indicators.add(indicator);
    return this;
}
```

with:

```java
public Builder widget(@NonNull WidgetSpec widget) {
    widgets.add(widget);
    return this;
}
```

Delete `ChartType`, `ChartSpec`, and `IndicatorSpec`.

- [ ] **Step 6: Replace createIndicators and createCharts**

Keep `createSystemIndicators()` but make it add to SUMMARY:

```java
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
    host.region(Region.SUMMARY).addView(tv, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
    ));
    return tv;
}
```

Replace `createActions()` so it uses `Region.ACTION`:

```java
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
```

Add `createWidgets()`:

```java
private void createWidgets() {
    ArrayList<WidgetSpec> ordered = new ArrayList<>(spec.widgets);
    ordered.sort((a, b) -> {
        if (a.region != b.region) return a.region.ordinal() - b.region.ordinal();
        return a.order - b.order;
    });
    for (WidgetSpec widgetSpec : ordered) {
        MetricWidget widget = MetricWidgets.create(context, widgetSpec);
        host.region(widgetSpec.region).addView(widget.view());
        widgets.add(widget);
    }
}
```

In `init()`, replace calls to old `createProfileIndicators()` and `createCharts()` with:

```java
createWidgets();
```

- [ ] **Step 7: Replace sample consumption**

In `consume(...)`, replace chart and indicator update logic with:

```java
private void consume(@NonNull BluetoothSample sample) {
    for (MetricWidget widget : widgets) {
        widget.onSample(sample);
    }
    if (recorder.isRecording() && spec.recordFormatter != null) {
        recorder.appendLine(spec.recordFormatter.format(sample));
    }
}
```

In `resetCharts()`, rename to `resetWidgets()` and replace body with:

```java
private void resetWidgets() {
    for (MetricWidget widget : widgets) {
        widget.reset();
    }
}
```

In `runBuiltIn(START_RECORD)`, replace `resetCharts();` with:

```java
resetWidgets();
```

- [ ] **Step 8: Run compile to catch binding and import errors**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: PASS.

- [ ] **Step 9: Do not commit until Profiles is updated**

Keep changes uncommitted because `ProfilesTest` still expects `Profiles.eis()` to register widgets.

---

## Task 4: Merge Recorder Into One Class

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/SampleRecorder.java`
- Delete: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/SampleRecorderImpl.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragment.java`

- [ ] **Step 1: Replace SampleRecorder interface with concrete class**

Replace `app/src/main/java/com/hc/mixthebluetooth/activity/tool/SampleRecorder.java` with:

```java
package com.hc.mixthebluetooth.activity.tool;

import android.content.Context;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SampleRecorder {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile boolean recording = false;
    private volatile File recordFile = null;
    private int sampleCount = 0;

    public void start(@NonNull Context context, @NonNull String prefix) {
        recordFile = createFile(context, prefix);
        recording = true;
        sampleCount = 0;
    }

    public void stop() {
        recording = false;
    }

    public boolean isRecording() {
        return recording;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    @NonNull
    public String exportPath() {
        return recordFile != null ? recordFile.getAbsolutePath() : "";
    }

    public void appendLine(@Nullable String json) {
        if (!recording || recordFile == null || json == null || json.trim().isEmpty()) return;
        File file = recordFile;
        String line = json.trim();
        sampleCount++;
        io.execute(() -> {
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
                writer.write(line);
                writer.newLine();
            } catch (Exception ignored) {
            }
        });
    }

    public void release() {
        io.shutdown();
    }

    @NonNull
    private File createFile(@NonNull Context context, @NonNull String prefix) {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) dir = context.getExternalFilesDir(null);
        if (dir == null) dir = context.getFilesDir();
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return new File(dir, prefix + "_" + ts + ".jsonl");
    }
}
```

- [ ] **Step 2: Delete SampleRecorderImpl**

Run:

```powershell
Remove-Item -LiteralPath app\src\main\java\com\hc\mixthebluetooth\activity\tool\SampleRecorderImpl.java
```

- [ ] **Step 3: Remove SampleRecorderImpl imports**

Run:

```powershell
rg -n "SampleRecorderImpl" app\src\main\java app\src\test\java
```

Expected: no results.

- [ ] **Step 4: Compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: PASS.

- [ ] **Step 5: Commit recorder merge**

Run:

```powershell
git add app\src\main\java\com\hc\mixthebluetooth\activity\tool\SampleRecorder.java app\src\main\java\com\hc\mixthebluetooth\activity\tool\SampleRecorderImpl.java app\src\main\java\com\hc\mixthebluetooth\fragment\UnifiedMessageFragment.java
git commit -m "refactor: merge sample recorder implementation"
```

---

## Task 5: Update Profiles.eis With Five Widgets

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/Profiles.java`
- Delete: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/EisSample.java`
- Test: `app/src/test/java/com/hc/mixthebluetooth/activity/tool/ProfilesTest.java`

- [ ] **Step 1: Update imports**

In `Profiles.java`, remove:

```java
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.ChartSpec;
```

Add:

```java
import com.hc.mixthebluetooth.activity.tool.chart.MetricWidgets.WidgetSpec;
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.Region;
```

- [ ] **Step 2: Replace EIS profile registration**

Replace `Profiles.eis()` with:

```java
@NonNull
public static ProfileSpec eis() {
    return ProfileSpec.builder("eis")
            .parser(new EisParser())
            .action(ActionSpec.inner("start_record", "开始记录", BuiltIn.START_RECORD))
            .action(ActionSpec.inner("stop_record", "结束记录", BuiltIn.STOP_RECORD))
            .action(ActionSpec.inner("export", "导出", BuiltIn.EXPORT))
            .widget(WidgetSpec.gauge("eis_conductance_gauge")
                    .title("电导率")
                    .metric(EisSample.METRIC_US)
                    .unit("uS")
                    .region(Region.SUMMARY)
                    .order(10)
                    .gaugeMax(10f)
                    .lineColor(Color.rgb(78, 224, 151))
                    .build())
            .widget(WidgetSpec.value("eis_ohm_value")
                    .title("阻抗")
                    .metric(EisSample.METRIC_OHM)
                    .unit("Ω")
                    .region(Region.SUMMARY)
                    .order(20)
                    .build())
            .widget(WidgetSpec.line("eis_ohm_line")
                    .title("电化学交流阻抗（EIS）")
                    .metric(EisSample.METRIC_OHM)
                    .unit("Ω")
                    .region(Region.MAIN)
                    .order(10)
                    .lineColor(Color.rgb(66, 133, 244))
                    .build())
            .widget(WidgetSpec.line("eis_us_line")
                    .title("电导率（uS）")
                    .metric(EisSample.METRIC_US)
                    .unit("uS")
                    .region(Region.MAIN)
                    .order(20)
                    .lineColor(Color.rgb(251, 188, 5))
                    .build())
            .widget(WidgetSpec.stats("eis_ohm_stats")
                    .title("阻抗统计")
                    .metric(EisSample.METRIC_OHM)
                    .unit("Ω")
                    .region(Region.MAIN)
                    .order(30)
                    .build())
            .recordJson(Profiles::eisJson)
            .build();
}
```

- [ ] **Step 3: Move EisSample into Profiles**

Add this nested class inside `Profiles`:

```java
public static class EisSample implements BluetoothSample {
    public static final String TYPE = "eis";
    public static final String METRIC_OHM = "ohm";
    public static final String METRIC_US = "us";

    public final float ohm;
    public final float us;
    @NonNull public final String raw;

    public EisSample(float ohm, float us, @NonNull String raw) {
        this.ohm = ohm;
        this.us = us;
        this.raw = raw;
    }

    @NonNull
    @Override
    public String type() {
        return TYPE;
    }

    @NonNull
    @Override
    public String raw() {
        return raw;
    }

    @NonNull
    @Override
    public java.util.Map<String, Float> metrics() {
        java.util.Map<String, Float> m = new java.util.LinkedHashMap<>();
        m.put(METRIC_OHM, ohm);
        m.put(METRIC_US, us);
        return m;
    }
}
```

- [ ] **Step 4: Delete old EisSample.java**

Run:

```powershell
Remove-Item -LiteralPath app\src\main\java\com\hc\mixthebluetooth\activity\tool\EisSample.java
```

- [ ] **Step 5: Replace EisSample references**

Run:

```powershell
rg -n "EisSample" app\src\main\java app\src\test\java
```

Expected remaining references should be either `Profiles.EisSample` or references inside `Profiles.java`. If old legacy classes still reference `EisSample`, delete them in Task 6 before final compile.

- [ ] **Step 6: Run profile tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.activity.tool.ProfilesTest" --tests "com.hc.mixthebluetooth.activity.tool.chart.MetricWidgetsTest"
```

Expected: PASS.

- [ ] **Step 7: Commit profile widget migration**

Run:

```powershell
git add app\src\main\java\com\hc\mixthebluetooth\activity\tool\Profiles.java app\src\main\java\com\hc\mixthebluetooth\activity\tool\EisSample.java app\src\test\java\com\hc\mixthebluetooth\activity\tool\ProfilesTest.java
git commit -m "feat: register eis metric widgets"
```

---

## Task 6: Remove Legacy New-Message Profile Model

**Files:**
- Delete: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/SampleConsumer.java`
- Delete: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/DeviceProfile.java`
- Delete: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/EisProfile.java`
- Delete: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/EisProfileNew.java`
- Delete: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessageNew.java`
- Delete: `app/src/main/res/layout/fragment_message_new.xml`
- Modify if needed: `app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java`

- [ ] **Step 1: Confirm CommunicationActivity no longer imports FragmentMessageNew**

Run:

```powershell
rg -n "FragmentMessageNew|EisProfileNew|EisProfile|DeviceProfile|SampleConsumer" app\src\main\java\com\hc\mixthebluetooth
```

Expected before deletion: matches in legacy files only. `CommunicationActivity.java` should not import `FragmentMessageNew`.

- [ ] **Step 2: Delete legacy files**

Run:

```powershell
Remove-Item -LiteralPath app\src\main\java\com\hc\mixthebluetooth\activity\tool\SampleConsumer.java
Remove-Item -LiteralPath app\src\main\java\com\hc\mixthebluetooth\activity\tool\DeviceProfile.java
Remove-Item -LiteralPath app\src\main\java\com\hc\mixthebluetooth\activity\tool\EisProfile.java
Remove-Item -LiteralPath app\src\main\java\com\hc\mixthebluetooth\activity\tool\EisProfileNew.java
Remove-Item -LiteralPath app\src\main\java\com\hc\mixthebluetooth\fragment\FragmentMessageNew.java
Remove-Item -LiteralPath app\src\main\res\layout\fragment_message_new.xml
```

- [ ] **Step 3: Search for stale references**

Run:

```powershell
rg -n "FragmentMessageNew|EisProfileNew|EisProfile|DeviceProfile|SampleConsumer|fragment_message_new|EisSample" app\src\main\java app\src\main\res app\src\test\java
```

Expected: no results except `Profiles.EisSample` and test references to `Profiles.EisSample`.

- [ ] **Step 4: Compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: PASS.

- [ ] **Step 5: Commit cleanup**

Run:

```powershell
git add app\src\main\java\com\hc\mixthebluetooth\activity\tool\SampleConsumer.java app\src\main\java\com\hc\mixthebluetooth\activity\tool\DeviceProfile.java app\src\main\java\com\hc\mixthebluetooth\activity\tool\EisProfile.java app\src\main\java\com\hc\mixthebluetooth\activity\tool\EisProfileNew.java app\src\main\java\com\hc\mixthebluetooth\fragment\FragmentMessageNew.java app\src\main\res\layout\fragment_message_new.xml
git commit -m "refactor: remove legacy message profile model"
```

---

## Task 7: Polish Unified Widget Layout

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/chart/MetricWidgets.java`
- Modify: `app/src/main/res/layout/fragment_unified_message.xml`

- [ ] **Step 1: Make summary widgets share horizontal width**

In `MessageController.createWidgets()`, before adding the view, insert:

```java
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
```

Add this helper inside `MessageController`:

```java
private int dp(int value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
}
```

Remove the older direct add:

```java
host.region(widgetSpec.region).addView(widget.view());
```

- [ ] **Step 2: Keep message list compact**

In `fragment_unified_message.xml`, set `recyclerMessage` height to `72dp`:

```xml
android:layout_height="72dp"
```

Expected visual behavior: summary widgets and line cards get most of the screen; raw message list remains available but does not dominate the page.

- [ ] **Step 3: Compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: PASS.

- [ ] **Step 4: Commit layout polish**

Run:

```powershell
git add app\src\main\java\com\hc\mixthebluetooth\fragment\UnifiedMessageFragment.java app\src\main\java\com\hc\mixthebluetooth\activity\tool\chart\MetricWidgets.java app\src\main\res\layout\fragment_unified_message.xml
git commit -m "style: polish unified metric widget layout"
```

---

## Task 8: Final Verification

**Files:**
- Verify: entire app module

- [ ] **Step 1: Run Java compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run all debug unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Verify old model is not referenced**

Run:

```powershell
rg -n "SampleConsumer|DeviceProfile|EisProfileNew|FragmentMessageNew|SampleRecorderImpl|ChartSpec|IndicatorSpec|RealtimeLineChart" app\src\main\java\com\hc\mixthebluetooth app\src\test\java\com\hc\mixthebluetooth
```

Expected:

- No results for `SampleConsumer`, `DeviceProfile`, `EisProfileNew`, `FragmentMessageNew`, `SampleRecorderImpl`, `ChartSpec`, `IndicatorSpec`.
- `RealtimeLineChart` may appear only in `MetricWidgets.java` and `RealtimeLineChart.java`.

- [ ] **Step 4: Verify unified channels remain clean**

Run:

```powershell
rg -n "CMD_BT_POST|CH_BT_EVENT|CMD_SEND_BT_DATA|CH_BT_DATA|EV_REC_SAMPLE" app\src\main\java\com\hc\mixthebluetooth
```

Expected:

- `UnifiedMessageFragment.java` uses `CMD_BT_POST` and `CH_BT_EVENT`.
- `CommunicationActivity.java` bridges old and new channels.
- Legacy `CMD_SEND_BT_DATA` and `CH_BT_DATA` remain only for old fragments.

- [ ] **Step 5: Check git status and recent commits**

Run:

```powershell
git status --short --branch
git log --oneline -8
```

Expected:

```text
## dev-1.3
```

Recent commits should include:

```text
style: polish unified metric widget layout
refactor: remove legacy message profile model
feat: register eis metric widgets
refactor: merge sample recorder implementation
feat: add metric widget specs
```

---

## Self-Review

Spec coverage:

- 旧模型清理：Task 4 and Task 6 cover recorder merge and old `DeviceProfile` / `SampleConsumer` deletion.
- ChartSpec -> WidgetSpec：Task 1 through Task 3 cover tests, implementation, and controller migration.
- Region 锚点：Task 3 updates XML and HostView.
- EIS gauge/value/stats 效果：Task 5 registers gauge/value/stats using real EIS metrics.
- 测试与验证：Task 1, Task 2, Task 5, Task 8 cover unit tests and full verification.

Type consistency:

- `Region` lives in `UnifiedMessageFragment`.
- `WidgetSpec`, `WidgetKind`, `WidgetStyle`, and `MetricWidget` live in `MetricWidgets`.
- `ProfileSpec.widgets` uses `List<MetricWidgets.WidgetSpec>`.
- `MessageController.widgets` uses `List<MetricWidgets.MetricWidget>`.

Scope:

- This plan does not migrate CGM.
- This plan does not rewrite `FragmentCustom`.
- This plan does not introduce YAML/XML profile files.
