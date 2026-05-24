package com.hc.mixthebluetooth.remote;

import androidx.annotation.Nullable;

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
}
