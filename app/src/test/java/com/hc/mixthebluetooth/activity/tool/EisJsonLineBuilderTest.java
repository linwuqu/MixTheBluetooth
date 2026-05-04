package com.hc.mixthebluetooth.activity.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.hc.mixthebluetooth.schema.eis.EisJsonLineBuilder;
import com.hc.mixthebluetooth.schema.eis.EisSample;

import org.junit.Test;

public class EisJsonLineBuilderTest {

    @Test
    public void escapeJsonEscapesControlCharacters() {
        String escaped = EisJsonLineBuilder.escapeJson("a\"b\\c\n\r\t");

        assertEquals("a\\\"b\\\\c\\n\\r\\t", escaped);
    }

    @Test
    public void buildIncludesSampleMetricsAndRawText() {
        EisSample sample = new EisSample(12.5f, 3.25f, "12.5ohm,3.25uS");

        String json = EisJsonLineBuilder.build(null, sample);

        assertTrue(json.contains("\"mac\":\"\""));
        assertTrue(json.contains("\"name\":\"\""));
        assertTrue(json.contains("\"ohm\":12.5"));
        assertTrue(json.contains("\"us\":3.25"));
        assertTrue(json.contains("\"raw\":\"12.5ohm,3.25uS\""));
    }
}
