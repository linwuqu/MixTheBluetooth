package com.hc.mixthebluetooth.fragment;

import android.content.Context;
import android.view.View;

import com.hc.basiclibrary.viewBasic.BaseFragment;
import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.bluetoothlibrary.tootl.ModuleParameters;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.databinding.FragmentThreeBinding;

public class FragmentThree extends BaseFragment<FragmentThreeBinding> {

    private enum State{hidden,unhidden}
    private State mState = State.hidden;

    @Override
    protected void updateState(String sign, Object o) {
        if (StaticConstants.FRAGMENT_UNHIDDEN.equals(sign)) setHiddenChanged(true);
        if (StaticConstants.FRAGMENT_THREE_HIDE.equals(sign)) setHiddenChanged(false);
        if (StaticConstants.FRAGMENT_STATE_CONNECT_STATE.equals(sign) && o !=null) viewBinding.generalFragmentState.setText(o.toString());
        if (StaticConstants.FRAGMENT_STATE_DATA.equals(sign) && o != null){
            DeviceModule deviceModule = (DeviceModule) o;
            viewBinding.generalFragmentName.setText(deviceModule.getName());
            viewBinding.generalFragmentMac.setText(deviceModule.getMac());
            viewBinding.generalFragmentType.setText(deviceModule.isBLE()?"BLE蓝牙":"2.0经典蓝牙");
            viewBinding.generalFragmentService.setText(deviceModule.getServiceUUID());
            viewBinding.generalFragmentSend.setText(deviceModule.getReadWriteUUID());
            viewBinding.generalFragmentRead.setText(deviceModule.getReadWriteUUID());
        }
    }


    public void onClickView(View view){
        viewBinding.generalFragmentHeight.setChecked(false);
        viewBinding.generalFragmentCentre.setChecked(false);
        viewBinding.generalFragmentLow.setChecked(false);

        if (isCheck(viewBinding.generalFragmentHeight) || isCheck(viewBinding.generalFragmentHeightText)){
            viewBinding.generalFragmentHeight.setChecked(true);
        } else if (isCheck(viewBinding.generalFragmentCentre) || isCheck(viewBinding.generalFragmentCentreText)){
            viewBinding.generalFragmentCentre.setChecked(true);
        } else if (isCheck(viewBinding.generalFragmentLow) || isCheck(viewBinding.generalFragmentLowText)){
            viewBinding.generalFragmentLow.setChecked(true);
        }
    }

    @Override
    protected FragmentThreeBinding getViewBinding() {
        return FragmentThreeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initAll(View view, Context context) {
        initView();
        subscription(StaticConstants.FRAGMENT_UNHIDDEN,StaticConstants.FRAGMENT_THREE_HIDE,
                StaticConstants.FRAGMENT_STATE_CONNECT_STATE,StaticConstants.FRAGMENT_STATE_DATA);
    }

    /**
     * 编码格式点击的实现
     */
    private void codedFormatOnClick(View view){
        viewBinding.generalFragmentGbk.setChecked(false);
        viewBinding.generalFragmentUtf.setChecked(false);
        viewBinding.generalFragmentUnicode.setChecked(false);
        viewBinding.generalFragmentAscii.setChecked(false);
        int id = view.getId();
        if (id == R.id.general_fragment_gbk || id == R.id.general_fragment_gbk_text) {
            viewBinding.generalFragmentGbk.setChecked(true);
            FragmentParameter.getInstance().setCodeFormat("GBK", getContext());
        } else if (id == R.id.general_fragment_utf || id == R.id.general_fragment_utf_text) {
            viewBinding.generalFragmentUtf.setChecked(true);
            FragmentParameter.getInstance().setCodeFormat("UTF-8", getContext());
        } else if (id == R.id.general_fragment_unicode || id == R.id.general_fragment_unicode_text) {
            viewBinding.generalFragmentUnicode.setChecked(true);
            FragmentParameter.getInstance().setCodeFormat("Unicode", getContext());
        } else if (id == R.id.general_fragment_ascii || id == R.id.general_fragment_ascii_text) {
            viewBinding.generalFragmentAscii.setChecked(true);
            FragmentParameter.getInstance().setCodeFormat("ASCII", getContext());
        }
    }


    /**
     * 发送速率加减的点击实现
     */
    private void onClickValue(View view){
        int id = view.getId();
        if (id == R.id.general_fragment_pack_add) {
            viewBinding.generalFragmentPackValue.setText(String.valueOf(ModuleParameters.addLevel()));
        } else if (id == R.id.general_fragment_pack_minus) {
            viewBinding.generalFragmentPackValue.setText(String.valueOf(ModuleParameters.minusLevel()));
        }
    }

    private void onClickNewline(View view){
        viewBinding.checkNewline.toggle();
    }

    private void initView(){
        viewBinding.generalFragmentHeight.setChecked(false);
        viewBinding.generalFragmentCentre.setChecked(false);
        viewBinding.generalFragmentLow.setChecked(false);
        viewBinding.checkNewline.setChecked(ModuleParameters.isCheckNewline());
        viewBinding.generalFragmentTime.setText(String.valueOf(ModuleParameters.getTime()));
        viewBinding.generalFragmentBleBuff.setText(String.valueOf(ModuleParameters.getBleReadBuff()));
        viewBinding.generalFragmentClassicBuff.setText(String.valueOf(ModuleParameters.getClassicReadBuff()));
        viewBinding.generalFragmentPackValue.setText(String.valueOf(ModuleParameters.getLevel()));
        int state = ModuleParameters.system()?ModuleParameters.getState()-2:ModuleParameters.getState();
        switch (state){
            case 0:
                viewBinding.generalFragmentHeight.setChecked(true);
                break;
            case 1:
                viewBinding.generalFragmentCentre.setChecked(true);
                break;
            case 2:
                viewBinding.generalFragmentLow.setChecked(true);
                break;
        }

        switch (FragmentParameter.getInstance().getCodeFormat(getContext())){
            case "GBK":
                viewBinding.generalFragmentGbk.setChecked(true);
                break;
            case "UTF-8":
                viewBinding.generalFragmentUtf.setChecked(true);
                break;
            case "Unicode":
                viewBinding.generalFragmentUnicode.setChecked(true);
                break;
            case "ASCII":
                viewBinding.generalFragmentAscii.setChecked(true);
                break;
        }

        bindOnClickListener(viewBinding.generalFragmentHeight,viewBinding.generalFragmentCentre,viewBinding.generalFragmentLow,
                viewBinding.generalFragmentHeightText,viewBinding.generalFragmentCentreText,viewBinding.generalFragmentLowText);
        bindOnClickListener(this::codedFormatOnClick,viewBinding.generalFragmentGbk,viewBinding.generalFragmentGbkText,
                viewBinding.generalFragmentUnicode,viewBinding.generalFragmentUnicodeText, viewBinding.generalFragmentUtf,
                viewBinding.generalFragmentUtfText,viewBinding.generalFragmentAscii,viewBinding.generalFragmentAsciiText);
        bindOnClickListener(this::onClickValue,viewBinding.generalFragmentPackAdd,viewBinding.generalFragmentPackMinus);
        bindOnClickListener(this::onClickNewline,viewBinding.checkNewline,viewBinding.checkNewlineText);
    }



    private int getSate(){
        if (viewBinding.generalFragmentHeight.isChecked()) {
            return 0;
        }else if (viewBinding.generalFragmentCentre.isChecked()) {
            return 1;
        }else {
            return 2;
        }
    }


    //设置fragment隐藏与非隐藏下view的改变
    private void setHiddenChanged(boolean unHidden){

        if (unHidden && mState == State.unhidden) return;//传来非隐藏，与当前状态值相同，则退出
        if (!unHidden && mState == State.hidden) return;//同上

        if (!unHidden){//隐藏
            int classicBuff = Integer.parseInt(viewBinding.generalFragmentClassicBuff.getText().toString());
            int time = Integer.parseInt(viewBinding.generalFragmentTime.getText().toString());
            ModuleParameters.setParameters(getSate(),Integer.parseInt(viewBinding.generalFragmentBleBuff.getText().toString()),classicBuff,time,getContext());
            ModuleParameters.saveLevel(Integer.parseInt(viewBinding.generalFragmentPackValue.getText().toString()),getContext());
            ModuleParameters.setNewline(viewBinding.checkNewline.isChecked());
            mState = State.hidden;//隐藏
        }else {
            initView();
            mState = State.unhidden;//非隐藏
        }
    }
}
