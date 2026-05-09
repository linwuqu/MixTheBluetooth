package com.hc.mixthebluetooth.activity.tool.chart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.Region;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class MetricWidgetsTest {

    @Test
    public void lineWidgetSpecKeepsMetricRegionAndOrder() {
        MetricWidgets.WidgetSpec spec = MetricWidgets.WidgetSpec.line("eis_ohm")
                .title("EIS Ohm")
                .metric("ohm")
                .unit("ohm")
                .region(Region.MAIN)
                .order(20)
                .lineColor(0xFFFF0000)
                .yRange(0f, 100f)
                .build();

        assertEquals("eis_ohm", spec.id);
        assertEquals(MetricWidgets.WidgetKind.LINE, spec.kind);
        assertEquals("EIS Ohm", spec.title);
        assertEquals("ohm", spec.metricKey);
        assertEquals("ohm", spec.unit);
        assertEquals(Region.MAIN, spec.region);
        assertEquals(20, spec.order);
        assertEquals(0xFFFF0000, spec.color);
        assertEquals(Float.valueOf(0f), spec.yMin);
        assertEquals(Float.valueOf(100f), spec.yMax);
    }

    @Test
    public void gaugeWidgetSpecHasSummaryDefaults() {
        MetricWidgets.WidgetSpec spec = MetricWidgets.WidgetSpec.gauge("conductance")
                .title("Conductance")
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

    @Test
    public void orderedForDisplaySortsByRegionThenOrderWithoutMutatingInput() {
        MetricWidgets.WidgetSpec mainEarly = MetricWidgets.WidgetSpec.line("main_early")
                .metric("ohm")
                .region(Region.MAIN)
                .order(10)
                .build();
        MetricWidgets.WidgetSpec summaryLate = MetricWidgets.WidgetSpec.value("summary_late")
                .metric("ohm")
                .region(Region.SUMMARY)
                .order(20)
                .build();
        MetricWidgets.WidgetSpec summaryEarly = MetricWidgets.WidgetSpec.gauge("summary_early")
                .metric("us")
                .region(Region.SUMMARY)
                .order(10)
                .build();
        List<MetricWidgets.WidgetSpec> original = Arrays.asList(mainEarly, summaryLate, summaryEarly);

        List<MetricWidgets.WidgetSpec> ordered = MetricWidgets.orderedForDisplay(original);

        assertEquals("summary_early", ordered.get(0).id);
        assertEquals("summary_late", ordered.get(1).id);
        assertEquals("main_early", ordered.get(2).id);
        assertEquals("main_early", original.get(0).id);
    }
}
