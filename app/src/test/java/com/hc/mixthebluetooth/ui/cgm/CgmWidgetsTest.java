package com.hc.mixthebluetooth.ui.cgm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.hc.mixthebluetooth.ui.cgm.CgmController.Region;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class CgmWidgetsTest {

    @Test
    public void lineWidgetSpecKeepsMetricRegionAndOrder() {
        CgmWidgets.WidgetSpec spec = CgmWidgets.WidgetSpec.line("eis_ohm")
                .title("EIS Ohm")
                .metric("ohm")
                .unit("ohm")
                .region(Region.MAIN)
                .order(20)
                .lineColor(0xFFFF0000)
                .yRange(0f, 100f)
                .build();

        assertEquals("eis_ohm", spec.id);
        assertEquals(CgmWidgets.WidgetKind.LINE, spec.kind);
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
        CgmWidgets.WidgetSpec spec = CgmWidgets.WidgetSpec.gauge("conductance")
                .title("Conductance")
                .metric("us")
                .unit("uS")
                .gaugeMax(10f)
                .build();

        assertEquals(CgmWidgets.WidgetKind.GAUGE, spec.kind);
        assertEquals(Region.SUMMARY, spec.region);
        assertEquals(0, spec.order);
        assertEquals(Float.valueOf(10f), spec.gaugeMax);
        assertNull(spec.yMin);
        assertNull(spec.yMax);
    }

    @Test
    public void orderedForDisplaySortsByRegionThenOrderWithoutMutatingInput() {
        CgmWidgets.WidgetSpec mainEarly = CgmWidgets.WidgetSpec.line("main_early")
                .metric("ohm")
                .region(Region.MAIN)
                .order(10)
                .build();
        CgmWidgets.WidgetSpec summaryLate = CgmWidgets.WidgetSpec.value("summary_late")
                .metric("ohm")
                .region(Region.SUMMARY)
                .order(20)
                .build();
        CgmWidgets.WidgetSpec summaryEarly = CgmWidgets.WidgetSpec.gauge("summary_early")
                .metric("us")
                .region(Region.SUMMARY)
                .order(10)
                .build();
        List<CgmWidgets.WidgetSpec> original = Arrays.asList(mainEarly, summaryLate, summaryEarly);

        List<CgmWidgets.WidgetSpec> ordered = CgmWidgets.orderedForDisplay(original);

        assertEquals("summary_early", ordered.get(0).id);
        assertEquals("summary_late", ordered.get(1).id);
        assertEquals("main_early", ordered.get(2).id);
        assertEquals("main_early", original.get(0).id);
    }
}

