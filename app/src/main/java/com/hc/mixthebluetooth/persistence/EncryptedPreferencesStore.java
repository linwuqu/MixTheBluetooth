package com.hc.mixthebluetooth.persistence;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.persistence.SessionStore;
import com.hc.mixthebluetooth.driver.capability.PreferencesStore;

import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class EncryptedPreferencesStore implements PreferencesStore, SessionStore.Store {
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS_PREFIX = "MixTheBluetoothPrefs.";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE_BITS = 256;
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final SharedPreferences preferences;
    private final SecretKey secretKey;

    public EncryptedPreferencesStore(@NonNull Context context, @NonNull String name) {
        Context app = context.getApplicationContext();
        this.preferences = app.getSharedPreferences(name, Context.MODE_PRIVATE);
        this.secretKey = createSecretKey(name);
    }

    @NonNull
    private static SecretKey createSecretKey(@NonNull String name) {
        try {
            String alias = KEY_ALIAS_PREFIX + name;
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
            keyStore.load(null);
            if (keyStore.containsAlias(alias)) {
                KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(alias, null);
                return entry.getSecretKey();
            }

            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
            )
                    .setKeySize(KEY_SIZE_BITS)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build();
            generator.init(spec);
            return generator.generateKey();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create encrypted preferences key: " + name, e);
        }
    }

    @Override
    public void putString(@NonNull String key, @Nullable String value) {
        SharedPreferences.Editor editor = preferences.edit();
        if (value == null) {
            editor.remove(key);
        } else {
            editor.putString(key, encrypt(value));
        }
        editor.apply();
    }

    @Nullable
    @Override
    public String getString(@NonNull String key, @Nullable String defaultValue) {
        String encrypted = preferences.getString(key, null);
        if (encrypted == null) return defaultValue;

        String value = decrypt(encrypted);
        return value == null ? defaultValue : value;
    }

    @Nullable
    @Override
    public String getString(@NonNull String key) {
        return getString(key, null);
    }

    @Override
    public void putBoolean(@NonNull String key, boolean value) {
        putString(key, Boolean.toString(value));
    }

    @Override
    public boolean getBoolean(@NonNull String key, boolean defaultValue) {
        String value = getString(key, null);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    @Override
    public void putLong(@NonNull String key, long value) {
        putString(key, Long.toString(value));
    }

    @Override
    public long getLong(@NonNull String key, long defaultValue) {
        String value = getString(key, null);
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    @Override
    public void putInt(@NonNull String key, int value) {
        putString(key, Integer.toString(value));
    }

    @Override
    public int getInt(@NonNull String key, int defaultValue) {
        String value = getString(key, null);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    @Override
    public void remove(@NonNull String key) {
        preferences.edit().remove(key).apply();
    }

    @Override
    public void clear() {
        preferences.edit().clear().apply();
    }

    @NonNull
    private String encrypt(@NonNull String value) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.encodeToString(payload, Base64.NO_WRAP);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to encrypt preference value", e);
        }
    }

    @Nullable
    private String decrypt(@NonNull String encoded) {
        try {
            byte[] payload = Base64.decode(encoded, Base64.NO_WRAP);
            if (payload.length <= IV_BYTES) return null;

            byte[] iv = new byte[IV_BYTES];
            byte[] encrypted = new byte[payload.length - IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            System.arraycopy(payload, IV_BYTES, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);
        } catch (RuntimeException | GeneralSecurityException e) {
            return null;
        }
    }
}
