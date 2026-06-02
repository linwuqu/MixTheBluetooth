package com.hc.mixthebluetooth.api.cgm;

import androidx.annotation.Nullable;

import java.util.List;

public final class CgmResult {
    public long resultId;
    public long jobId;
    public long datasetId;
    public int pointCount;
    public int unitCount;
    public double predictionMin;
    public double predictionMax;
    public double predictionMean;
    public double predictionStd;
    public double avgMard;
    @Nullable
    public Double mardStd;
    @Nullable
    public Summary summaryJson;
    @Nullable
    public String status;
    @Nullable
    public String gmtCreate;

    public static final class Summary {
        public double avgMard;
        @Nullable
        public Double mardStd;
        public int pointCount;
        public int unitCount;
        @Nullable
        public PredictionStats predictionStats;
        @Nullable
        public List<Unit> units;
    }

    public static final class PredictionStats {
        public double min;
        public double max;
        public double mean;
        public double std;
    }

    public static final class Unit {
        public int unit;
        @Nullable
        public String unitTitle;
        public int pointCount;
        public double mard;
        @Nullable
        public List<Point> points;
    }

    public static final class Point {
        public int index;
        public int time;
        public double predicted;
        @Nullable
        public Double actual;
    }
}
