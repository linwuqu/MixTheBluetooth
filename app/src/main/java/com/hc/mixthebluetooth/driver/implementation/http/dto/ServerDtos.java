package com.hc.mixthebluetooth.driver.implementation.http.dto;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class ServerDtos {
    private ServerDtos() {
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
        public long id;
        @Nullable
        public String username;
        @Nullable
        public String phone;
        @Nullable
        public String avatarUrl;
        // 【后续】在这里考虑添加特权用户/普通用户的判别 在DefaultService中
        @Nullable
        public String role;
        @Nullable
        public Long rootFileId;
        @Nullable
        public String rootFileName;
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

    public static final class CgmUploadResp {
        @Nullable
        public Long datasetId;
        @Nullable
        public String datasetStatus;
        @Nullable
        public Long rawAccountFileId;
        @Nullable
        public Integer rowCount;
        @Nullable
        public Integer unitCount;
        @Nullable
        public Long jobId;
        @Nullable
        public String jobNo;
        @Nullable
        public String jobStatus;
        @Nullable
        public Long resultId;
        @Nullable
        public String errorMsg;
        @Nullable
        public List<String> parseWarnings;
    }

    public static final class CgmJobData {
        @Nullable
        public Long jobId;
        @Nullable
        public String jobNo;
        @Nullable
        public String status;
        @Nullable
        public Long datasetId;
        @Nullable
        public Long resultId;
        @Nullable
        public String errorMsg;
        @Nullable
        public String startedAt;
        @Nullable
        public String finishedAt;
    }

    public static final class CgmResultInfo {
        @Nullable
        public Double min;
        @Nullable
        public Double max;
        @Nullable
        public Double mean;
        @Nullable
        public Double std;
        @Nullable
        public Double tir;
        @Nullable
        public Double tirLow;
        @Nullable
        public Double tirHigh;
    }

    public static final class CgmCurveResp {
        @Nullable
        public Long resultId;
        @Nullable
        public Integer unitCount;
        @Nullable
        public List<CgmUnit> units;
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
        @SerializedName("rawTime")
        @Nullable
        public String rawTime;
        public double predicted;
        @Nullable
        public Double actual;
    }
}
