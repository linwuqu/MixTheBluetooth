package com.hc.mixthebluetooth.storage;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.persistence.SettingsStore;
import com.hc.mixthebluetooth.persistence.EncryptedSettingsStore;

public class Storage {
    private final String widthKey = "widthKey";
    private final String invalidAT = "invalidAT";
    private final String saveInputDateKey = "saveInputDateKey";
    private final String saveCheckShowDataState = "saveCheckShowDataState";
    private final SettingsStore settingsStore;

    public Storage(Context context) {
        this(EncryptedSettingsStore.create(context));
    }

    Storage(@NonNull SettingsStore settingsStore) {
        this.settingsStore = settingsStore;
    }

    public void saveData(String key, boolean value){
        settingsStore.putBoolean(key, value);
    }

    public void saveData(String key, String value){
        settingsStore.putString(key, value);
    }

    public void saveWidth(int value){
        settingsStore.putInt(widthKey, value);
    }

    public void saveFirstTime(){
        settingsStore.setFirstLaunch(false);
    }

    public void saveInvalidAT(){
        settingsStore.putBoolean(invalidAT, false);
    }

    public void saveCodedFormat(String code){
        settingsStore.setTextEncoding(code);
    }

    public void saveInputData(String data){
        settingsStore.putString(saveInputDateKey, data);
    }

    public void saveCheckShowDataState(boolean isCheck){
        settingsStore.putBoolean(saveCheckShowDataState, isCheck);
    }

    public boolean getData(String key){
        return settingsStore.getBoolean(key, false);
    }

    public String getDataString(String key){
        return settingsStore.getString(key, null);
    }

    public int getWidth(){return settingsStore.getInt(widthKey, -1);}

    public boolean getFirstTime(){
        return settingsStore.firstLaunch();
    }

    public boolean getInvalidAT(){ return settingsStore.getBoolean(invalidAT, true);}

    public String getCodedFormat(){ return settingsStore.textEncoding();}

    public String getSaveInputData(){return settingsStore.getString(saveInputDateKey, "");}

    public boolean getDataCheckState(){return settingsStore.getBoolean(saveCheckShowDataState, true);}

}
