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
