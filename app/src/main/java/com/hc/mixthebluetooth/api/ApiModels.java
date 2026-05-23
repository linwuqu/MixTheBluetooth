package com.hc.mixthebluetooth.api;

import androidx.annotation.Nullable;

public final class ApiModels {
    private ApiModels() {
    }

    public static final class JsonData<T> {
        public int code;
        @Nullable
        public T data;
        @Nullable
        public String msg;
        public boolean success;
    }

    public static final class AccountRegisterReq {
        public String username;
        public String password;
        public String phone;
        @Nullable
        public String avatarUrl;

        public AccountRegisterReq(String username, String password, String phone, @Nullable String avatarUrl) {
            this.username = username;
            this.password = password;
            this.phone = phone;
            this.avatarUrl = avatarUrl;
        }
    }

    public static final class AccountLoginReq {
        public String phone;
        public String password;

        public AccountLoginReq(String phone, String password) {
            this.phone = phone;
            this.password = password;
        }
    }

    public static final class AccountInfo {
        public long accountId;
        public String username;
        public String phone;
        @Nullable
        public String avatarUrl;
        @Nullable
        public String token;
    }

    public static final class FileUploadResp {
        public long fileId;
        public String fileName;
        @Nullable
        public String path;
        @Nullable
        public String url;
    }
}
