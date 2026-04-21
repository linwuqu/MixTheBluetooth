package com.hc.mixthebluetooth.activity;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.hc.basiclibrary.dialog.CommonDialog;
import com.hc.basiclibrary.file.GetFilesUtils;
import com.hc.basiclibrary.permission.PermissionUtil;
import com.hc.basiclibrary.titleBasic.DefaultNavigationBar;
import com.hc.basiclibrary.viewBasic.BaseActivity;
import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.bluetoothlibrary.tootl.ModuleParameters;
import com.hc.bluetoothlibrary.tootl.VelocityCorrection;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.single.HoldBluetooth;
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.customView.NumPickView;
import com.hc.mixthebluetooth.databinding.ActivitySendFileBinding;
import com.hc.mixthebluetooth.recyclerData.FileRecyclerAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class SendFileActivity extends BaseActivity<ActivitySendFileBinding> {

    private FileRecyclerAdapter mAdapter;

    private final List<Map<String, Object>> mList = new ArrayList<>();

    private int mFileSizeMax,mFileSendSize;

    private byte[] mTargetFileData = null;

    private DeviceModule mDeviceModule;

    private int mSendNumber;
    private Timer mTimer;
    private TimerTask mTimerTask;
    private final Handler mUpdateViewHandler = new Handler();

    private int mModuleParameters;//模块发送延迟参数暂存

    private int mSendFileVelocity = 5;//自定义发送到模块的速度

    @Override
    public void initAll() {
        initTitle();
        initRecycler();
        initDeviceModule();
        initView();
        initTimer();
        initModuleParameters();
    }

    @Override
    protected ActivitySendFileBinding getViewBinding() {
        return ActivitySendFileBinding.inflate(getLayoutInflater());
    }


    public void onClickView(View view){

        if (isCheck(viewBinding.aFileCallback)){
            updateRecycler(GetFilesUtils.getInstance()
                    .getParentPath(viewBinding.aFilePath.getText().toString().trim()));
        }else if (isCheck(viewBinding.aFileSelect)){
            selectFile();
        }else if(isCheck(viewBinding.aFileSend)){
            sendFile();
        }

    }


    /**
     * 点击事件的响应
     */
    private void onCheckedChanged(View view){
        if (!viewBinding.aFileSend.getText().toString().trim().equals("发送文件")){
            toastShort("正处于发送过程中，请勿切换速度");
            return;
        }
        resetCheck();//重置所有的选择
        int id = view.getId();
        if (id == R.id.a_file_velocity_1 || id == R.id.a_file_velocity_1_check) {
            viewBinding.aFileVelocity1Check.setChecked(true);
            HoldBluetooth.getInstance().setSendFileVelocity(mDeviceModule, 1);
        } else if (id == R.id.a_file_velocity_2 || id == R.id.a_file_velocity_2_check) {
            viewBinding.aFileVelocity2Check.setChecked(true);
            HoldBluetooth.getInstance().setSendFileVelocity(mDeviceModule, 2);
        } else if (id == R.id.a_file_velocity_3 || id == R.id.a_file_velocity_3_check) {
            viewBinding.aFileVelocity3Check.setChecked(true);
            HoldBluetooth.getInstance().setSendFileVelocity(mDeviceModule, 3);
        } else if (id == R.id.a_file_velocity_4 || id == R.id.a_file_velocity_4_check) {
            viewBinding.aFileVelocity4Check.setChecked(true);
            HoldBluetooth.getInstance().setSendFileVelocity(mDeviceModule, 4);
        } else if (id == R.id.a_file_velocity_5 || id == R.id.a_file_velocity_5_check) {
            viewBinding.aFileVelocity5Check.setChecked(true);
            HoldBluetooth.getInstance().setSendFileVelocity(mDeviceModule, 5, mSendFileVelocity);
        }
    }


    /**
     * 点击事件的响应
     */
    private void setCustomVelocity(View view){
        final CommonDialog dialog = new CommonDialog.Builder(view.getContext()).setCancelable(false)
                .setView(R.layout.hint_velocity_set).fullWidth().fromBottom().create();
        TextView accomplish = dialog.findViewById(R.id.hint_velocity_accomplish);
        final NumPickView numPickView = dialog.findViewById(R.id.hint_velocity_pick);
        numPickView.select(mSendFileVelocity-1);
        accomplish.setOnClickListener(v -> {
            String s = "速度五:自定义速度 "+numPickView.getPickNumber()+"k/s";
            mSendFileVelocity = numPickView.getPickNumber();
            HoldBluetooth.getInstance().setSendFileVelocity(mDeviceModule,5,mSendFileVelocity);
            viewBinding.aFileVelocity5.setText(s);
            dialog.dismiss();
        });
        dialog.show();
    }

    private void sendFile() {

        if (mFileSizeMax == 0){
            toastShort("请先选择文件");
            return;
        }

        if (mFileSizeMax == -1){
            toastShort("文件解析异常");
            return;
        }

        if (mDeviceModule == null){
            toastShort("初始化失败");
            return;
        }

        if (viewBinding.aFileSend.getText().toString().trim().equals("停止发送")){
            viewBinding.aFileSend.setText("发送文件");
            HoldBluetooth.getInstance().stopSend(mDeviceModule, () -> viewBinding.aFileSend.setText("发送文件"));
            return;
        }

        viewBinding.aFileProgress.setProgress(0);

        mFileSendSize = 0;

        viewBinding.aFileSend.setText("停止发送");

        ModuleParameters.setSendFile(true);

        HoldBluetooth.getInstance().sendData(mDeviceModule, mTargetFileData);

    }


    private void selectFile() {
        PermissionUtil.requestEach(this, new PermissionUtil.OnPermissionListener() {
            @Override
            public void onSucceed() {
                viewBinding.aFileSelectVelocity.setVisibility(View.GONE);
                String baseFile = GetFilesUtils.getInstance().getBasePath();
                updateRecycler(baseFile);
            }

            @Override
            public void onFailed(boolean showAgain) {
                toastShort("无法获取到手机文件");
            }
        },PermissionUtil.STORAGE);
    }


    private void updateRecycler(String fileName) {
        String baseFile = GetFilesUtils.getInstance().getBasePath();
        if (baseFile.compareTo(fileName)>0){
            toastShort("已到根目录");
            return;
        }
        mList.clear();
        mList.addAll(GetFilesUtils.getInstance().getSonNode(fileName));
        mAdapter.notifyDataSetChanged();
        viewBinding.aFilePath.setText(fileName);
        viewBinding.aFileCallback.setVisibility(View.VISIBLE);
        viewBinding.aFileList.setVisibility(View.VISIBLE);
    }

    private void updateView() {
        String data = mFileSendSize+"/"+mFileSizeMax;
        viewBinding.aFileData.setText(data);
        float progress = ((float) mFileSendSize/mFileSizeMax)*1000;
        viewBinding.aFileProgress.setProgress((int) progress);
        if (progress == 1000) viewBinding.aFileSend.setText("发送文件");
    }


    /**
     * 读取文件内容，并更新到Activity的View
     * @param position RecyclerView 列表上的文件位置
     */
    private void readFile(int position){
        Object object = mList.get(position).get(GetFilesUtils.FILE_INFO_PATH);
        if(!(object instanceof File)){
            toastShort("无法识别此后缀的文件，请选择其他文件");
            return;
        }
        File file = (File)mList.get(position).get(GetFilesUtils.FILE_INFO_PATH);
        if (file.length()>50*1024*1024){
            toastShort("暂不支持发送大于50M的文件");
            return;
        }
        Analysis.readFileDate(file, this, (readResults, fileDate) -> {
            if (!readResults){
                mFileSizeMax = -1;
                toastShort("读取此文件失败，请选择其他文件");
                return;
            }
            log("回调成功...");
            mTargetFileData = fileDate;
            mFileSizeMax = mTargetFileData.length;
            mFileSendSize = 0;
            viewBinding.aFileProgress.setProgress(0);
            String fileSize = mFileSendSize+"/"+mFileSizeMax;
            viewBinding.aFileName.setText(file.getName());
            viewBinding.aFileData.setText(fileSize);
            viewBinding.aFileCallback.setVisibility(View.INVISIBLE);
            viewBinding.aFileList.setVisibility(View.GONE);
            viewBinding.aFileSelectVelocity.setVisibility(View.VISIBLE);
            log("设置完成...");
        });
    }


    private void initTitle() {
        new DefaultNavigationBar
                .Builder(this, findViewById(R.id.activity_send_file))
                .setTitle("HC蓝牙助手("+HoldBluetooth.getInstance().getConnectedArray().get(0).getName()+")")
                .hideLeftText()
                .builer();
    }

    private void initRecycler() {
        mAdapter = new FileRecyclerAdapter(this,mList,R.layout.item_file);
        viewBinding.aFileList.setLayoutManager(new LinearLayoutManager(this));
        viewBinding.aFileList.setAdapter(mAdapter);

        mAdapter.setOnItemClickListener((position, view) -> {
            if (!(boolean)mList.get(position).get(GetFilesUtils.FILE_INFO_ISFOLDER)){
                log("启用此方法时间: "+System.currentTimeMillis());
                readFile(position);
                log("结束时间: "+System.currentTimeMillis());
            }else {
                File fileName = (File) mList.get(position).get(GetFilesUtils.FILE_INFO_PATH);
                updateRecycler(fileName.toString());
            }
        });
    }

    @Override
    protected void update(String sign, Object data) {
        if (!StaticConstants.FRAGMENT_STATE_NUMBER.equals(sign)) return;
        Integer integer = (Integer) data;
        mFileSendSize += integer;
        mSendNumber += integer;
        updateView();
    }

    private void initDeviceModule() {
        mDeviceModule = HoldBluetooth.getInstance().getConnectedArray().get(0);
    }

    private void initTimer() {
        mTimerTask = new TimerTask() {
            @Override
            public void run() {
                if (mSendNumber == 0 && viewBinding.aFileVelocity.getText().toString().equals("0B/s")) return;
                if (mSendNumber == 0 && (!mDeviceModule.isBLE()&& viewBinding.aFileVelocity5Check.isChecked()&&mFileSendSize != mFileSizeMax)) return;
                final String velocity = Analysis.getSpeed(mSendNumber,!mDeviceModule.isBLE()&&viewBinding.aFileVelocity5Check.isChecked(),
                        mFileSendSize==mFileSizeMax);

                //当选定自定义发送时，修正发送速率 根据MTU的值来动态修改
                if (mDeviceModule.isBLE() && viewBinding.aFileVelocity5Check.isChecked()
                        && VelocityCorrection.getVelocity(mSendNumber*5,mSendFileVelocity)) {
                    ModuleParameters.setSendFileDelayedTime(VelocityCorrection.getDifferenceValue(
                            mSendFileVelocity, ModuleParameters.getSendFileDelayedTime()));
                }
                mSendNumber = 0;
                mUpdateViewHandler.post(() -> viewBinding.aFileVelocity.setText(velocity));
            }
        };

        mTimer = new Timer();

        mTimer.schedule(mTimerTask,200,200);
    }

    @SuppressLint("SetTextI18n")
    private void initView() {
        viewBinding.aFileVelocity1Check.setChecked(true);
        subscription(StaticConstants.FRAGMENT_STATE_NUMBER);
        if (!mDeviceModule.isBLE()){
            viewBinding.aFileVelocitySet.setVisibility(View.GONE);
            viewBinding.aFileVelocity5.setText("速度五:设置当前波特率下最大发送速度（适用于1MB之上的文件）");
        }
        bindClickListener(this::setCustomVelocity, viewBinding.aFileVelocitySet);
        bindClickListener(viewBinding.aFileCallback,viewBinding.aFileSelect,viewBinding.aFileSend);
        bindClickListener(this::onCheckedChanged,viewBinding.aFileVelocity1,viewBinding.aFileVelocity2,
                viewBinding.aFileVelocity3,viewBinding.aFileVelocity4,viewBinding.aFileVelocity5,
                viewBinding.aFileVelocity1Check,viewBinding.aFileVelocity2Check,viewBinding.aFileVelocity3Check,
                viewBinding.aFileVelocity4Check,viewBinding.aFileVelocity5Check);
    }


    /**
     * 获取模块的发送延迟数值，并默认设置9600的发送速率
     */
    private void initModuleParameters() {
        mModuleParameters = ModuleParameters.getSendFileDelayedTime();
        HoldBluetooth.getInstance().setSendFileVelocity(mDeviceModule,1);
    }

    private void resetCheck() {
        viewBinding.aFileVelocity1Check.setChecked(false);
        viewBinding.aFileVelocity2Check.setChecked(false);
        viewBinding.aFileVelocity3Check.setChecked(false);
        viewBinding.aFileVelocity4Check.setChecked(false);
        viewBinding.aFileVelocity5Check.setChecked(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        HoldBluetooth.getInstance().stopSend(mDeviceModule,null);
        ModuleParameters.setSendFile(false);
        ModuleParameters.setSendFileDelayedTime(mModuleParameters);
        if (mTimer != null) mTimer.cancel();
        if (mTimerTask != null) mTimerTask.cancel();
    }
}
