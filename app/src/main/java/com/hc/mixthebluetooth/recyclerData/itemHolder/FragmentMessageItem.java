package com.hc.mixthebluetooth.recyclerData.itemHolder;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.bluetoothlibrary.tootl.ModuleParameters;
import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.activity.tool.Analysis;

public class FragmentMessageItem {

    private String data;

    private byte[] byteData;

    private final boolean isMyData;

    private String time;

    private final boolean isShowMe;

    private final DeviceModule module;

    private boolean isHex;

    private boolean isDataEndNewline = false;

    public FragmentMessageItem(String data,String time,boolean isMyData,DeviceModule module,boolean isShowMe){
        this.data = data;
        this.isMyData = isMyData;
        this.module = module;
        this.isShowMe = isShowMe;
        this.time = time;
    }

    public FragmentMessageItem(boolean isHex,byte[] data,String time,boolean isMyData,DeviceModule module,boolean isShowMe){
        this.byteData = data;
        this.isMyData = isMyData;
        this.module = module;
        this.isShowMe = isShowMe;
        this.time = time;
        this.isHex = isHex;
    }

    public DeviceModule getModule() {
        return module;
    }

    public String getData() {
        if (data!= null) {
            return data;
        }
        return Analysis.getByteToString(byteData, FragmentParameter.getInstance().getCodeFormat(null),isHex,false);
    }

    /**
     * 可以添加数据
     */
    public boolean isAddible(){
        if (isMyData) return false;
        return  !isDataEndNewline;
    }

    public void setDataEndNewline(boolean isNewline){
        isDataEndNewline = isNewline;
    }

    public void addData(String data,String time){
        this.data += data;
        this.time = time;
    }

    public byte[] getByteData() {
        return byteData;
    }

    public String getTime() {
        return time;
    }

    public String getSign(){

        if (time != null) {
            return isMyData ? " <- " : " -> ";
        } else {
            if (isShowMe) {
                return isMyData ? " <- " : " -> ";
            } else {
                return "";
            }
        }

    }
}
