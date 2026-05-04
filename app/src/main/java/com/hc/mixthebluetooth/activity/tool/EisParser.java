package com.hc.mixthebluetooth.activity.tool;

import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses EIS text lines, for example:
 * 670258.375Ω,1.492uS
 */
public final class EisParser {

    private static final Pattern EIS_PATTERN = Pattern.compile(
            "\\s*([+-]?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\s*Ω\\s*,\\s*([+-]?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\s*(?:uS|μS|碌S)\\s*",
            Pattern.CASE_INSENSITIVE
    );

    private EisParser() {
    }

    @Nullable
    public static EisSample parse(@Nullable String line) {
        if (line == null) return null;

        String clean = line;

        int idx = clean.lastIndexOf("dataString:");
        if (idx >= 0) {
            clean = clean.substring(idx + "dataString:".length());
        }

        clean = clean.replace("\u0000", "").trim();

        Matcher matcher = EIS_PATTERN.matcher(clean);
        if (!matcher.find()) return null;

        try {
            float ohm = Float.parseFloat(matcher.group(1));
            float us = Float.parseFloat(matcher.group(2));
            return new EisSample(ohm, us, clean);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
