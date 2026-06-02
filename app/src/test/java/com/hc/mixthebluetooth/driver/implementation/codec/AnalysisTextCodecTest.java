package com.hc.mixthebluetooth.driver.implementation.codec;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.charset.Charset;

public class AnalysisTextCodecTest {
    @Test
    public void encodeUsesRequestedCharset() {
        AnalysisTextCodec codec = new AnalysisTextCodec();

        byte[] bytes = codec.encode("hello", "UTF-8");

        assertEquals("hello", new String(bytes, Charset.forName("UTF-8")));
    }

    @Test
    public void decodeRemovesNullsAndTrims() {
        AnalysisTextCodec codec = new AnalysisTextCodec();

        String text = codec.decode(new byte[]{' ', 'h', 'e', 'l', 'l', 'o', 0, ' '}, "UTF-8");

        assertEquals("hello", text);
    }
}
