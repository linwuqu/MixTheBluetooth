package com.hc.mixthebluetooth.remote;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class ServerModels {
    private ServerModels() {
    }

    public static final class LoginReq {
        public String phone;
        public String password;

        public LoginReq(String phone, String password) {
            this.phone = phone;
            this.password = password;
        }
    }

    public static final class RegisterReq {
        public String username;
        public String password;
        public String phone;
        @Nullable
        public String avatarUrl;

        public RegisterReq(String username, String password, String phone, @Nullable String avatarUrl) {
            this.username = username;
            this.password = password;
            this.phone = phone;
            this.avatarUrl = avatarUrl;
        }
    }

    public static final class AccountResp {
        public long accountId;
        @Nullable
        public String username;
        @Nullable
        public String phone;
        @Nullable
        public String avatarUrl;
        @Nullable
        public String token;
    }

    public static final class FileResp {
        public long fileId;
        @Nullable
        public String fileName;
        @Nullable
        public String path;
        @Nullable
        public String url;
    }

    public static final class CgmJobResp {
        public int code;
        @Nullable
        public String message;
        @Nullable
        public CgmJobData data;
    }

    public static final class CgmJobData {
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
        public CgmSummary summaryJson;
        @Nullable
        public String status;
        @Nullable
        public String gmtCreate;
    }

    public static final class CgmSummary {
        @SerializedName("avg_mard")
        public double avgMard;
        @SerializedName("mard_std")
        @Nullable
        public Double mardStd;
        @SerializedName("point_count")
        public int pointCount;
        @SerializedName("unit_count")
        public int unitCount;
        @SerializedName("prediction_stats")
        @Nullable
        public CgmPredictionStats predictionStats;
        @Nullable
        public List<CgmUnit> units;
    }

    public static final class CgmPredictionStats {
        public double min;
        public double max;
        public double mean;
        public double std;
    }

    public static final class CgmUnit {
        public int unit;
        @SerializedName("unit_title")
        @Nullable
        public String unitTitle;
        @SerializedName("point_count")
        public int pointCount;
        public double mard;
        @Nullable
        public List<CgmPoint> points;
    }

    public static final class CgmPoint {
        public int index;
        public int time;
        public double predicted;
        @Nullable
        public Double actual;
    }
}
