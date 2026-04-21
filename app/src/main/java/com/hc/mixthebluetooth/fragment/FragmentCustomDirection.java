package com.hc.mixthebluetooth.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
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
import com.hc.mixthebluetooth.databinding.FragmentCustomButtonDirectionBinding;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;
import com.hc.mixthebluetooth.storage.Storage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FragmentCustomDirection extends BaseFragment<FragmentCustomButtonDirectionBinding> {

    private static final String mSeparator = "//**$$/separator/$$**//";

    private  static final String TIME_ID = "0x44557788";

    private DefaultNavigationBar mTitle;

    private Storage mStorage;

    private FragmentParameter mFragmentParameter;

    private int mSendTime = 500;

    private boolean mIsContinueSend = false;//是否为按住持续发送

    private ExecutorService mService;

    private boolean isSend = false;

    private int mButtonMinWidth = -1;

    private boolean mIsSendNewline = false;

    @Override
    protected FragmentCustomButtonDirectionBinding getViewBinding() {
        return FragmentCustomButtonDirectionBinding.inflate(getLayoutInflater());
    }

    @Override
    public void initAll(View view, Context context) {
        mStorage = new Storage(context);//时间重写
        mFragmentParameter = FragmentParameter.getInstance();
        String data = mStorage.getDataString(TIME_ID);
        if (data!=null) {
            mSendTime = Integer.parseInt(data.substring(0, data.indexOf(mSeparator)));
            mIsContinueSend = data.substring(data.indexOf(mSeparator) + mSeparator.length()).equals("true");
        }
        mButtonMinWidth = mStorage.getWidth();
        setListener();
        setDirection();
        subscription(StaticConstants.FRAGMENT_STATE_SEND_SEND_TITLE,StaticConstants.FRAGMENT_CUSTOM_NEWLINE);
    }

    @Override
    protected void updateState(String sign, Object o) {
        if (StaticConstants.FRAGMENT_STATE_SEND_SEND_TITLE.equals(sign)){
            mTitle = (DefaultNavigationBar) o;
        }
        if (StaticConstants.FRAGMENT_CUSTOM_NEWLINE.equals(sign)){
            mIsSendNewline = (boolean) o;
        }
    }

    private void setListener() {
        View.OnClickListener listener = v -> {
            if (v.getId() == R.id.custom_fragment_direction_hex){
                viewBinding.customFragmentDirectionHex.toggle();
                return;
            }else if (v.getId() == R.id.custom_fragment_direction_set){
                if (!viewBinding.customFragmentDirectionSet.isChick()) toastShort("单击方向按钮即可编辑按钮内容");
                viewBinding.customFragmentDirectionSet.toggle();
                return;
            }

            int[] ids = {R.id.custom_fragment_direction_top,R.id.custom_fragment_direction_left,R.id.custom_fragment_direction_bottom,
                    R.id.custom_fragment_direction_right,R.id.custom_fragment_direction_middle};
            for (int id : ids) {
                if (v.getId() == id){
                    if (viewBinding.customFragmentDirectionSet.isChick()) {
                        setButtonWindow(v, true);
                        return;
                    }
                    if (mIsContinueSend) return;
                }
            }

            String data = mStorage.getDataString(String.valueOf(v.getId()));
            if (data != null) {
                send(data.substring(data.indexOf(mSeparator) + mSeparator.length()));
            }else {
                Toast.makeText(getContext(), "此按钮还没有初始化", Toast.LENGTH_SHORT).show();
            }
        };
        View.OnLongClickListener longClickListener = v -> {
            setButtonWindow(v,false);
            return false;
        };
        setItemClickListener(viewBinding.customFragmentDirectionLinear,listener);
        setItemClickLongListener(viewBinding.customFragmentDirectionLinear,longClickListener);
        TextView[] buttons = {viewBinding.customFragmentDirectionLeft,
                viewBinding.customFragmentDirectionRight,viewBinding.customFragmentDirectionBottom,
                viewBinding.customFragmentDirectionTop,viewBinding.customFragmentDirectionMiddle};
        for (TextView button : buttons) {
            button.setOnClickListener(listener);
            String data = mStorage.getDataString(String.valueOf(button.getId()));
            if (data != null){
                button.setText(data.substring(0,data.indexOf(mSeparator)));
            }
        }
        viewBinding.customFragmentDirectionSet.setOnClickListener(listener);
        viewBinding.customFragmentDirectionHex.setOnClickListener(listener);
    }

    /**
     * 设置子View的ClickListener
     */
    private void setItemClickListener(final View view, View.OnClickListener listener) {
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
                ((TextView)view).setText("长按设置");
                if (mButtonMinWidth <= 0) {
                    view.post(() -> {
                        mButtonMinWidth = view.getWidth();
                        mStorage.saveWidth(mButtonMinWidth);
                    });
                }
            }else {
                ((TextView)view).setText(data.substring(0,data.indexOf(mSeparator)));
                view.post(() -> {
                    log("设置按钮，按钮值为: "+((TextView)view).getText().toString()+" 宽度为："+view.getWidth());
                    if (view.getWidth()<mButtonMinWidth){
                        ViewGroup.LayoutParams params=view.getLayoutParams();
                        params.width= mButtonMinWidth;
                        view.setLayoutParams(params);
                        log("设置完成，设置的宽度为："+view.getWidth());
                    }
                });
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


    @SuppressLint("ClickableViewAccessibility")
    private void setDirection() {
        View.OnTouchListener touch = (v, event) -> {
            switch(event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isSend = true;
                    startSend(v);
                    break;
                case MotionEvent.ACTION_UP:
                    isSend = false;
                    break;
            }
            return false;
        };

        viewBinding.customFragmentDirectionTop.setOnTouchListener(touch);
        viewBinding.customFragmentDirectionLeft.setOnTouchListener(touch);
        viewBinding.customFragmentDirectionMiddle.setOnTouchListener(touch);
        viewBinding.customFragmentDirectionRight.setOnTouchListener(touch);
        viewBinding.customFragmentDirectionBottom.setOnTouchListener(touch);
    }

    @SuppressWarnings("all")
    private void startSend(final View view){
        if (!mIsContinueSend) return;
        if (mService == null) mService = Executors.newScheduledThreadPool(2);
        Runnable runnable = () -> {
            String data = mStorage.getDataString(String.valueOf(view.getId()));
            if (data == null){
                isSend = false;
                if (!viewBinding.customFragmentDirectionSet.isChick())
                toastShort("这个按钮还没有初始化,请打开\"设置方向按钮\",然后设置此键");
                return;
            }
            while (isSend){
                try {
                    send(data.substring(data.indexOf(mSeparator) + mSeparator.length()));
                    Thread.sleep(mSendTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        mService.execute(runnable);
    }


    //设置窗口
    private void setButtonWindow(final View view,boolean isDirection) {
        CommonDialog.Builder collectBuilder = new CommonDialog.Builder(getContext());
        collectBuilder.setView(R.layout.hint_set_button_vessel).fullWidth().loadAnimation().create().show();
        SetButton setButton = collectBuilder.getView(R.id.hint_set_button_vessel_view);
        String data = mStorage.getDataString(String.valueOf(view.getId()));
        String name = data != null?data.substring(0,data.indexOf(mSeparator)):"";
        String content = data != null?data.substring(data.indexOf(mSeparator)+mSeparator.length()):"";
        if (isDirection) setButton.showMove(mIsContinueSend);
        setButton.setEditText(name,content).setTime(mSendTime).setBuilder(collectBuilder).setCallback(new SetButton.OnCollectCallback() {
            @Override
            public void callback(String name, String content) {
                mStorage.saveData(String.valueOf(view.getId()),name+mSeparator+content);
                setListener();
            }

            @Override
            public void callLongClick(String name, String content, boolean isLongClick, String time) {
                mStorage.saveData(String.valueOf(view.getId()),name+mSeparator+content);
                mSendTime = Integer.parseInt(time);
                mIsContinueSend = isLongClick;
                mStorage.saveData(TIME_ID,time+mSeparator+isLongClick);
                log("mIsContinueSend: "+mIsContinueSend);
                setListener();
            }
        });
    }

    private void send(String data) {
        if (mTitle != null && !mTitle.getParams().mRightText.equals("已连接")){//代表当前没有连接上
            toastShort("当前状态不能发送数据，请连接完再尝试发送数据");
            return;
        }
        boolean isHex = viewBinding.customFragmentDirectionHex.getState() == CustomButtonView.State.Open;
        byte[] bytes;
        if (mIsSendNewline) data += isHex? "0D0A":"\r\n";
        if (isHex) data = Analysis.getFiltrationHexString(data);
        bytes = Analysis.getBytes(data,mFragmentParameter.getCodeFormat(getContext()),isHex);

        sendDataToActivity(StaticConstants.DATA_TO_MODULE,new FragmentMessageItem(isHex,bytes,null,
                true, HoldBluetooth.getInstance().getConnectedArray().get(0),false));
    }
}
