package com.hc.mixthebluetooth.application.cgm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class CgmReplayCompletionDetectorTest {
    @Test
    public void sampleReplayDoesNotCompleteBeforeLastLine() throws Exception {
        CgmReplayCompletionDetector detector = new CgmReplayCompletionDetector();
        List<String> lines = replaySample();

        for (int i = 0; i < lines.size() - 1; i++) {
            assertNotEquals(CgmReplayCompletionDetector.Event.COMPLETED, detector.consume(lines.get(i)));
        }
    }

    @Test
    public void sampleReplayCompletesOnCurrentKnownTerminator() throws Exception {
        CgmReplayCompletionDetector detector = new CgmReplayCompletionDetector();
        List<String> lines = replaySample();
        CgmReplayCompletionDetector.Event event = CgmReplayCompletionDetector.Event.IDLE;

        for (String line : lines) {
            event = detector.consume(line);
        }

        assertEquals(CgmReplayCompletionDetector.Event.COMPLETED, event);
    }

    @Test
    public void emptyLineDoesNotCompleteReplay() {
        CgmReplayCompletionDetector detector = new CgmReplayCompletionDetector();

        detector.consume("Start Playback");

        assertNotEquals(CgmReplayCompletionDetector.Event.COMPLETED, detector.consume(""));
    }

    private static List<String> replaySample() throws Exception {
        InputStream input = CgmReplayCompletionDetectorTest.class
                .getClassLoader()
                .getResourceAsStream("device/device_replay_sample.txt");
        if (input == null) {
            throw new AssertionError("Missing device replay sample");
        }

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }
}
