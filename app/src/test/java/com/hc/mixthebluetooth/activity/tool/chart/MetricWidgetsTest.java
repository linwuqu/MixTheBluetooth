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
