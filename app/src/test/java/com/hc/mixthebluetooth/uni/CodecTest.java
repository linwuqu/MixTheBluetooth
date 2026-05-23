package com.hc.mixthebluetooth.uni;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.nio.charset.Charset;

public class CodecTest {

    @Test
    public void decodeReturnsNullForNullOrEmptyBytes() {
        Codec.Options options = new Codec.Options("UTF-8", false, false);

        assertNull(Codec.decode(null, options));
        assertNull(Codec.decode(new byte[0], options));
    }

    @Test
    public void decodeRemovesZeroCharactersAndTrims() {
        Codec.Options options = new Codec.Options("UTF-8", false, false);
        byte[] bytes = "  12.5\u0000Ω,3.2uS  ".getBytes(Charset.forName("UTF-8"));

        assertEquals("12.5Ω,3.2uS", Codec.decode(bytes, options));
    }

    @Test
    public void decodeUsesUtf8WhenCharsetIsNull() {
        Codec.Options options = new Codec.Options(null, false, false);
        byte[] bytes = "hello".getBytes(Charset.forName("UTF-8"));

        assertEquals("hello", Codec.decode(bytes, options));
    }

    @Test
    public void decodeRemovesNullsAndTrims() {
        Codec.Options options = new Codec.Options("UTF-8", false, false);

        assertEquals("hello", Codec.decode(new byte[]{' ', 'h', 'e', 'l', 'l', 'o', 0, ' '}, options));
    }
}
