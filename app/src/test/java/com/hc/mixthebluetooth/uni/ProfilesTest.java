package com.hc.mixthebluetooth.uni;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.hc.mixthebluetooth.activity.tool.BluetoothSample;
import com.hc.mixthebluetooth.uni.profile.eis.EisProfile;
import com.hc.mixthebluetooth.uni.profile.eis.EisProfile.EisSample;

import org.junit.Test;

public class ProfilesTest {

    @Test
    public void eisProfileDeclaresActionsAndFiveWidgets() {
        Controller.ProfileSpec spec = Profiles.eis();

        assertEquals("eis", spec.id);
        assertEquals(1, spec.parsers.size());
        assertEquals(3, spec.actions.size());
        assertEquals(Controller.Route.INNER, spec.actions.get(0).route);
        assertEquals(Controller.BuiltIn.START_RECORD, spec.actions.get(0).builtIn);
        assertEquals(Controller.BuiltIn.STOP_RECORD, spec.actions.get(1).builtIn);
        assertEquals(Controller.BuiltIn.EXPORT, spec.actions.get(2).builtIn);

        assertEquals(5, spec.widgets.size());
        assertWidget(spec.widgets.get(0), "eis_conductance_gauge", Widgets.WidgetKind.GAUGE, "us", Controller.Region.SUMMARY);
        assertWidget(spec.widgets.get(1), "eis_ohm_value", Widgets.WidgetKind.VALUE, "ohm", Controller.Region.SUMMARY);
        assertWidget(spec.widgets.get(2), "eis_ohm_line", Widgets.WidgetKind.LINE, "ohm", Controller.Region.MAIN);
        assertWidget(spec.widgets.get(3), "eis_us_line", Widgets.WidgetKind.LINE, "us", Controller.Region.MAIN);
        assertWidget(spec.widgets.get(4), "eis_ohm_stats", Widgets.WidgetKind.STATS, "ohm", Controller.Region.MAIN);
    }

    @Test
    public void eisParserProducesOhmAndUsMetrics() {
        BluetoothSample sample = Profiles.eis().parsers.get(0).parse("670258.375Ω,1.492uS");

        assertNotNull(sample);
        assertEquals(EisSample.TYPE, sample.type());
        assertEquals(670258.375f, sample.metrics().get(EisSample.METRIC_OHM), 0.001f);
        assertEquals(1.492f, sample.metrics().get(EisSample.METRIC_US), 0.001f);
    }

    @Test
    public void eisRecordJsonIncludesBothMetricsAndEscapedRawText() {
        BluetoothSample sample = new EisSample(12.5f, 3.25f, "12.5Ω,\"3.25uS\"\n");

        String json = EisProfile.eisJson(sample);

        assertTrue(json.contains("\"ohm\":12.5"));
        assertTrue(json.contains("\"us\":3.25"));
        assertTrue(json.contains("\"raw\":\"12.5Ω,\\\"3.25uS\\\"\\n\""));
    }

    private static void assertWidget(
            Widgets.WidgetSpec widget,
            String id,
            Widgets.WidgetKind kind,
            String metricKey,
            Controller.Region region
    ) {
        assertEquals(id, widget.id);
        assertEquals(kind, widget.kind);
        assertEquals(metricKey, widget.metricKey);
        assertEquals(region, widget.region);
    }
}
