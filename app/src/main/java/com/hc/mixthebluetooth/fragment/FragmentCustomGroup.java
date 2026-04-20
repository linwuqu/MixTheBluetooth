package com.hc.mixthebluetooth.fragment;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.hc.basiclibrary.dialog.CommonDialog;
import com.hc.basiclibrary.titleBasic.DefaultNavigationBar;
import com.hc.basiclibrary.viewBasic.BaseFragment;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.activity.single.HoldBluetooth;
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.customView.CustomButtonView;
import com.hc.mixthebluetooth.customView.dialog.SetButton;
import com.hc.mixthebluetooth.databinding.FragmentCustomButtonGroupBinding;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;
import com.hc.mixthebluetooth.storage.Storage;

public class FragmentCustomGroup extends BaseFragment<FragmentCustomButtonGroupBinding> {

    private Storage mStorage;

    private FragmentParameter mFragmentParameter;

    private static final String mSeparator = "//**$$/separator/$$**//";

    private DefaultNavigationBar mTitle;

    private boolean mIsSendNewline = false;


    @Override
    protected FragmentCustomButtonGroupBinding getViewBinding() {
        return FragmentCustomButtonGroupBinding.inflate(getLayoutInflater());
    }

    @Override
    public void initAll(View view, Context context) {
        mStorage = new Storage(context);
        mFragmentParameter = FragmentParameter.getInstance();
        setListener();
        subscription(StaticConstants.FRAGMENT_STATE_SEND_SEND_TITLE,StaticConstants.FRAGMENT_CUSTOM_NEWLINE);
    }

    @Override
    protected void updateState(String sign, Object o) {
        if (StaticConstants.FRAGMENT_STATE_SEND_SEND_TITLE.equals(sign)){
            mTitle = (DefaultNavigationBar) o;
        }else if (StaticConstants.FRAGMENT_CUSTOM_NEWLINE.equals(sign)){
            mIsSendNewline = (boolean) o;
        }
    }

    private void setListener() {
        View.OnClickListener listener = v -> {
            if (v.getId() == R.id.custom_fragment_direction_hex){
                viewBinding.customFragmentDirectionHex.toggle();
                return;
            }
            String data = mStorage.getDataString(String.valueOf(v.getId()));
            if (data != null) {
                send(data.substring(data.indexOf(mSeparator) + mSeparator.length()));
            }else {
                Toast.makeText(getContext(), "此按钮还没有初始化", Toast.LENGTH_SHORT).show();
            }
        };
        View.OnLongClickListener longClickListener = v -> {
            setButtonWindow(v);
            return false;
        };
        setItemClickListener(viewBinding.customFragmentLinear,listener);
        setItemClickLongListener(viewBinding.customFragmentLinear,longClickListener);
        viewBinding.customFragmentDirectionHex.setOnClickListener(listener);
    }

    /**
     * 设置子View的ClickListener
     */
    private void setItemClickListener(View view, View.OnClickListener listener) {
        if(view instanceof ViewGroup){
            ViewGroup viewGroup = (ViewGroup) view;
            int childCount = viewGroup.getChildCount();
            for (int i=0;i<childCount;i++){
                //不断的递归给里面所有的View设置OnClickListener
                View childView = viewGroup.getChildAt(i);
                setItemClickListener(childView,listener);
            }
        }else{
            String data = mStorage.getDataString(String.valueOf(view.getId()));
            if (data == null){
                ((Button)view).setText("长按设置");
            }else {
                ((Button)view).setText(data.substring(0,data.indexOf(mSeparator)));
            }
            view.setOnClickListener(listener);
        }
    }

    /**
     * 设置子View的ClickListener
     */
    private void setItemClickLongListener(View view, View.OnLongClickListener listener) {
        if(view instanceof ViewGroup){
            ViewGroup viewGroup = (ViewGroup) view;
            int childCount = viewGroup.getChildCount();
            for (int i=0;i<childCount;i++){
                //不断的递归给里面所有的View设置OnClickListener
                View childView = viewGroup.getChildAt(i);
                setItemClickLongListener(childView,listener);
            }
        }else{
            view.setOnLongClickListener(listener);
        }
    }

    //设置窗口
    private void setButtonWindow(final View view) {
        CommonDialog.Builder collectBuilder = new CommonDialog.Builder(getContext());
        collectBuilder.setView(R.layout.hint_set_button_vessel).fullWidth().loadAnimation().create().show();
        SetButton setButton = collectBuilder.getView(R.id.hint_set_button_vessel_view);
        String data = mStorage.getDataString(String.valueOf(view.getId()));
        String name = data != null?data.substring(0,data.indexOf(mSeparator)):"";
        String content = data != null?data.substring(data.indexOf(mSeparator)+mSeparator.length()):"";
        setButton.setEditText(name,content).setBuilder(collectBuilder).setCallback(new SetButton.OnCollectCallback() {
            @Override
            public void callback(String name, String content) {
                mStorage.saveData(String.valueOf(view.getId()),name+mSeparator+content);
                setListener();
            }

            @Override
            public void callLongClick(String name, String content, boolean isLongClick, String time) {

            }
        });
    }

    private void send(String data){
        if (mTitle != null && !mTitle.getParams().mRightText.equals("已连接")){//代表当前没有连接上
            toastShort("当前状态不能发送数据，请连接完再尝试发送数据");
            return;
        }
        boolean isHex = viewBinding.customFragmentDirectionHex.getState() == CustomButtonView.State.Open;
        byte[] bytes;

        if (mIsSendNewline) data += isHex? "0D0A":"\r\n";
        if (isHex) data = Analysis.getFiltrationHexString(data);
        log("过滤后的数据: "+data);
        bytes = Analysis.getBytes(data,mFragmentParameter.getCodeFormat(getContext()),isHex);

        sendDataToActivity(StaticConstants.DATA_TO_MODULE,new FragmentMessageItem(false, bytes, null,
                true, HoldBluetooth.getInstance().getConnectedArray().get(0), false));
    }

}
