package com.hc.mixthebluetooth.activity.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.BuiltIn;
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.ChartSpec;
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.ProfileSpec;
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.Route;

import org.junit.Test;

public class ProfilesTest {

    @Test
    public void eisProfileDeclaresTwoChartsAndLocalRecordActions() {
        ProfileSpec spec = Profiles.eis();

        assertEquals("eis", spec.id);
        assertEquals(1, spec.parsers.size());
        assertEquals(2, spec.charts.size());
        assertEquals(3, spec.actions.size());
        assertEquals(Route.INNER, spec.actions.get(0).route);
        assertEquals(BuiltIn.START_RECORD, spec.actions.get(0).builtIn);
        assertEquals(BuiltIn.STOP_RECORD, spec.actions.get(1).builtIn);
        assertEquals(BuiltIn.EXPORT, spec.actions.get(2).builtIn);

        ChartSpec ohmChart = spec.charts.get(0);
        ChartSpec usChart = spec.charts.get(1);
        assertEquals(EisSample.METRIC_OHM, ohmChart.metricKey);
        assertEquals(EisSample.METRIC_US, usChart.metricKey);
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

        String json = Profiles.eisJson(sample);

        assertTrue(json.contains("\"ohm\":12.5"));
        assertTrue(json.contains("\"us\":3.25"));
        assertTrue(json.contains("\"raw\":\"12.5Ω,\\\"3.25uS\\\"\\n\""));
    }
}
