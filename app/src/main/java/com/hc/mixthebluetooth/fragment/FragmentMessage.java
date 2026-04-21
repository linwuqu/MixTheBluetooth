package com.hc.mixthebluetooth.fragment;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.media.audiofx.AudioEffect;
import android.os.Environment;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.hc.basiclibrary.dialog.CommonDialog;
import com.hc.basiclibrary.recyclerAdapterBasic.FastScrollLinearLayoutManager;
import com.hc.basiclibrary.titleBasic.DefaultNavigationBar;
import com.hc.basiclibrary.viewBasic.BaseFragment;
import com.hc.basiclibrary.viewBasic.HomeApplication;
import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.bluetoothlibrary.tootl.ModuleParameters;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.LoginActivity;
import com.hc.mixthebluetooth.activity.MainActivity;
import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.activity.single.HoldBluetooth;
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.customView.PopWindowFragment;
import com.hc.mixthebluetooth.customView.dialog.InvalidHint;
import com.hc.mixthebluetooth.databinding.FragmentMessageBinding;
import com.hc.mixthebluetooth.recyclerData.FragmentMessAdapter;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;
import com.hc.mixthebluetooth.storage.Storage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FragmentMessage extends BaseFragment<FragmentMessageBinding> {

    private DefaultNavigationBar mTitle;//activity的头部

    private FragmentMessAdapter mAdapter;

    private final List<FragmentMessageItem> mDataList = new ArrayList<>();

    private DeviceModule module = null;

    private Storage mStorage;

    private FragmentParameter mFragmentParameter;

    private int mCacheByteNumber = 0;//mCacheByteNumber: 缓存的字节数

    private boolean isShowMyData,isSendHex,isShowTime,isReadHex,isAutoClear,isSendNewline;//弹出窗的六个选择

    private int mFoldLayoutHeight = 0;

    private final Handler mTimeHandler = new Handler();

    private final Timer mTimer = new Timer();//循环发送的具体线程
    private TimerTask mTimerTask;//循环发送的定时器

    private DecimalFormat decimalFormat = new DecimalFormat("#.000"); //格式化小数点后三位

    //private int Round = 0;//表示运行的轮次

    private ArrayList<Entry> values = new ArrayList<>();
    private ArrayList<Entry> valuesBlood = new ArrayList<>();



    public String data;

    public Analysis analysis;

    //手机提示音
    private MediaPlayer mediaPlayer;

    private HomeApplication homeApplication;

    private View view2;

    //判断是否开始获取数据
    boolean getDataStart = false;

    //记录数组元素个数
    int floatSum = 0;

    //记录电流个数,以后是校准后的血糖值
    int bloodSum = 0;

    //是否设置过参数，确定只能设置一次
    boolean isSetParam = false;

    private String limit;

    //是否读取缓存
    private boolean readCache = false;

    private EditText control_ratio_Edit;//占空比
    private EditText extraction_time_edit ;//提取时间
    private EditText high_level_edit ;//高电平时间
    private EditText voltage_Edit ;//偏压
    private EditText detection_time_edit ;//检测时间
    private String detectionString="";

    List<Float> dataFloatList = new ArrayList<>(); //作图数据EIS和CA
    List<Float> currentDataFloatList = new ArrayList<>(); //连续血糖作图数据
    List<String> dataInIOList=new ArrayList<>();// 备份系统的数据：CA稳定的最后一点+EIS数据

    /*
    *
    * 初始化方法，fragment进入后会运行的地方
    * */
    @Override
    public void initAll(View view, Context context) {

        mStorage = new Storage(context);
        mFragmentParameter = FragmentParameter.getInstance();
        setListState();
        //setParameter();
        initRecycler();
        initEditView();
        initFoldLayout();
        initData();
        initLimit();

    }
    /*
    *
    * 这个方法是绑定发送按钮的方法
    * */
    @SuppressLint("SetTextI18n")
    @Override
    protected void updateState(String sign, Object o) {
        switch (sign){
            case StaticConstants.FRAGMENT_STATE_DATA:
                //logWarn("获取module信息...");
                byte[] data = null;
                if(o instanceof Object[]) {
                    Object[] objects = (Object[]) o;
                    data = objects.length > 1 ? ((byte[]) objects[1]).clone() : null;
                }else if (o instanceof DeviceModule) module = (DeviceModule) o;

                if (data != null) {
                    addListData(data);
                    mAdapter.notifyDataSetChanged();
                    viewBinding.recyclerMessageFragment.smoothScrollToPosition(mDataList.size());
                    viewBinding.sizeReadMessageFragment.setText(String.valueOf(Integer.parseInt(viewBinding.sizeReadMessageFragment.getText().toString())+data.length));
                    setClearRecycler(data.length);//判断是否清屏（清除缓存）
                }
                break;
            case StaticConstants.FRAGMENT_STATE_NUMBER:
                viewBinding.sizeSendMessageFragment.setText(String.valueOf(Integer.parseInt(viewBinding.sizeSendMessageFragment.getText().toString())+((int) o)));
                //setUnsentNumberTv();
                break;
            case StaticConstants.FRAGMENT_STATE_SEND_SEND_TITLE:
                mTitle = (DefaultNavigationBar) o;
                break;
            case StaticConstants.FRAGMENT_STATE_SERVICE_VELOCITY:
                int velocity = (int) o;
                viewBinding.readVelocityMessageFragment.setText("速度: "+velocity+"B/s");
                break;
            case StaticConstants.FRAGMENT_STATE_1:
                viewBinding.readHintMessageFragment.setVisibility(View.VISIBLE);
                break;
            case StaticConstants.FRAGMENT_STATE_2:
                viewBinding.readHintMessageFragment.setVisibility(View.GONE);
                break;
            case StaticConstants.FRAGMENT_STATE_STOP_LOOP_SEND:
                if(mTimerTask != null) {
                    viewBinding.sendMessageFragment.setText("发送");
                    mTimerTask.cancel();
                    mTimerTask = null;
                    logWarn("Fragment Message 停止发送");
                    HoldBluetooth.getInstance().stopSend(module, null);
                }
                break;
        }
    }
    /*
    * 设置系统参数，电压、占空比等
    * */
    public void setParameter(){
        //Toast.makeText(getActivity(),"单击", Toast.LENGTH_SHORT).show();
        AlertDialog.Builder builder = new AlertDialog.Builder(this.getActivity());
        /*View view2 = View.inflate(this.getActivity(),R.layout.parameter_setting,null);*/
        control_ratio_Edit = (EditText)view2.findViewById(R.id.control_ratio_Edit); //占空比
        extraction_time_edit = (EditText)view2.findViewById(R.id.extraction_time_edit);//提取时间
        high_level_edit = (EditText)view2.findViewById(R.id.high_level_edit);//高电平时间
        voltage_Edit = (EditText)view2.findViewById(R.id.voltage_Edit);//偏压
        detection_time_edit = (EditText)view2.findViewById(R.id.detection_time_edit);//检测时间
        builder.setTitle("Settings").setIcon(R.drawable.setting).setView(view2).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        }).setPositiveButton("Submit", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                //将参数完全拼接起来，五个参数一共是5*6=30位数字

                String control_ratio_EditString =  analysis.isGetZero(control_ratio_Edit.getText().toString().trim(),"000");

                String extractionTimeString = "0010"+extraction_time_edit.getText().toString().trim();

                String highLevelEditString = analysis.isGetZero(high_level_edit.getText().toString().trim(),"010");

                String voltageString = "011"+voltage_Edit.getText().toString().trim();

                String detectionTime = analysis.isGetZero(detection_time_edit.getText().toString().trim(),"100");

                //Toast.makeText(getActivity(),voltageString+control_ratio_EditString, Toast.LENGTH_SHORT).show();

                //将参数完全拼接起来，五个参数一共是5*6=30位数字

                data = control_ratio_EditString+extractionTimeString+highLevelEditString+voltageString+detectionTime;

                logWarn("数据："+data);
                //设置button不可按
                TextView isSetParam = viewBinding.sendMessageFragment;
                isSetParam.setEnabled(false);

                setSendData();
            }

        });
        builder.create().show();
    }
    /*
    * 用来控制控件是否可编辑，普通用户不可编辑，管理员用户可编辑
    * */
    public void initLimit()
    {
        //获取当前登录的权限
        homeApplication = (HomeApplication) this.getActivity().getApplication();
        limit = homeApplication.getLimits();
        if (limit.equals("ordinary"))
        {
            //普通用户
            Toast.makeText(this.getActivity(),"进入普通用户",Toast.LENGTH_SHORT).show();
            control_ratio_Edit.setEnabled(false);
            extraction_time_edit.setEnabled(false); ;//提取时间
            high_level_edit.setEnabled(false); ;//高电平时间
            voltage_Edit.setEnabled(false);//偏压
            detection_time_edit.setEnabled(false);//检测时间

        } else if (limit.equals("admin")) {
            Toast.makeText(this.getActivity(),"进入管理员用户",Toast.LENGTH_SHORT).show();
            //管理员用户
            control_ratio_Edit.setEnabled(true);
            extraction_time_edit.setEnabled(true); ;//提取时间
            high_level_edit.setEnabled(true); ;//高电平时间
            voltage_Edit.setEnabled(true);//偏压
            detection_time_edit.setEnabled(true);//检测时间
        }
    }
    public void onClickView(View view){
        //从这里单击发送按钮，setSendData()方法是单击后执行的方法
        if (isCheck(viewBinding.sendMessageFragment)) {
            //设置参数按钮
            if (!isSetParam) {
                setParameter();
            }
        }

            //setSendData();
       /* if (isCheck(viewBinding.dialogParameter)){
            //表示如果单击了dialog设置参数按钮，弹窗dialog
            logWarn("你单击我了");
        }*/
//        if (isCheck(viewBinding.foldSwitchMessageFragment)) setFoldLayout();
        // 开始测量按钮
      else if (isCheck(viewBinding.sentStartFlag)){
            setStartFlag();
        }
      //读取缓存中的数据，获取所有数据
      else if (isCheck(viewBinding.getAllData)){
           getBufferData();
      }
      else if(isCheck(viewBinding.deleteCacheData)){
          deleteBufferData();
        }
        if (false) {
            if (viewBinding.sendMessageFragment.getText().toString().equals("发送")) {
                //viewBinding.editMessageFragment.setText("");
            }else {
                toastShort("连续发送中，不能清除发送区的数据");
            }
        }else if (isCheck(viewBinding.pullMessageFragment)){
            viewBinding.pullMessageFragment.setImageResource(R.drawable.pull_up);
            new PopWindowFragment(view, getActivity(), new PopWindowFragment.DismissListener() {
                @Override
                public void onDismissListener() {
                    viewBinding.pullMessageFragment.setImageResource(R.drawable.pull_down);
                    setListState();
                }

                @Override
                public void clearRecycler() {
                    mDataList.clear();
                    viewBinding.sizeReadMessageFragment.setText(String.valueOf(0));
                    viewBinding.sizeSendMessageFragment.setText(String.valueOf(0));
                    mCacheByteNumber = 0;
                    mAdapter.notifyDataSetChanged();
                }
            });

        }
        //点击开始按钮

    }

    /**
     * 获取缓存中存放到数据
     */
    private void getBufferData(){
        data="ALL"+"\n\r";
        setSendData();
    }
    /**
     * 删除缓存中的数据
     */
    private void deleteBufferData(){
        data="DELETE"+"\n\r";
        setSendData();
    }

    /**
     * 设备开始工作按钮方法，读取对应时间
     */
    private void setStartFlag(){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy,MM,dd,HH,mm,ss", Locale.getDefault());
        String formattedTime = sdf.format(new Date());
        data = "TIME," + formattedTime+"\n\r";
        logWarn("sentStartFlag:"+data);
        setSendData();
    }

    @Override
    protected FragmentMessageBinding getViewBinding() {
        return FragmentMessageBinding.inflate(getLayoutInflater());
    }

    private void setSendData() {
        if (!mTitle.getParams().mRightText.equals("已连接")){
            //判断当前是否已经连接蓝牙
            toastShort("当前状态不可以发送数据");
            return;
        }
        if (data.equals("")){
            //判断输入框中的数据是否为空
            toastShort("不能发送空数据");
            return;
        }
        //判断是否选择循环发送复选框
        if (true) {
            //获取到输入框中的字符，就是发送的指令框
            /*
            * 调整成复选框的选择形式，就是把输入框中的内容改掉,注意：ViewBinding找到对应的xml中的方法，是通过ViewBinding.对应的UI组件的ID去掉下划线即可。
            * */
           // logWarn("发送后的："+data);
            //String data = viewBinding.editMessageFragment.getText().toString();
            if (isSendNewline) data += isSendHex?"0D0A":"\r\n";
            //判断是否是16进制数
            if(isSendHex) data = data.replaceAll(" ","");
            //这里应该是向硬件传输指令,经过测试这就是传输指令的部分
            sendData(new FragmentMessageItem(isSendHex, Analysis.getBytes(data,mFragmentParameter.getCodeFormat(getContext()), isSendHex), isShowTime ? Analysis.getTime() : null, true, module, isShowMyData));
            //弹出指令无效
            //dataScreening(viewBinding.editMessageFragment.getText().toString());
        }else {

            //这里也是传输数据的入口
            if (viewBinding.sendMessageFragment.getText().toString().equals("发送")){
                viewBinding.sendMessageFragment.setText("停止");
                final int time = Integer.parseInt("1");
                mTimerTask = new TimerTask() {
                    @Override
                    public void run() {
                        //String data = viewBinding.editMessageFragment.getText().toString();
                        if (isSendNewline) data += isSendHex?"0D0A":"\r\n";
                        if (isSendHex) data = data.replaceAll(" ","");
                        final String sendData = data;
                        mTimeHandler.post(()-> sendData(new FragmentMessageItem(isSendHex, Analysis.getBytes(sendData,mFragmentParameter.getCodeFormat(getContext()), isSendHex), isShowTime ? Analysis.getTime() : null, true, module, isShowMyData)));
                    }
                };
                mTimer.schedule(mTimerTask,0,time);
            }else {
                viewBinding.sendMessageFragment.setText("设置参数");
                HoldBluetooth.getInstance().stopSend(module,null);
                mTimerTask.cancel();
                mTimerTask = null;
            }
        }
    }
    //弹出提示框，警告AT指令设置无效
    private void dataScreening(String data) {
        String str = "AT+";
        if (data.length()<str.length()) return;
        String temp = data.substring(0,str.length());
        if (temp.equals(str) && mStorage.getInvalidAT()){
            CommonDialog.Builder invalidAtBuilder = new CommonDialog.Builder(getContext());
            invalidAtBuilder.setView(R.layout.hint_invalid_vessel).fullWidth().loadAnimation().create().show();
            InvalidHint invalidHint = invalidAtBuilder.getView(R.id.hint_invalid_vessel_view);
            invalidHint.setBuilder(invalidAtBuilder);
        }
    }

   /* private void setFoldLayout() {
        if ((int)viewBinding.foldSwitchMessageFragment.getTag() == R.drawable.pull_down){
            System.out.println("下来");
            viewBinding.foldSwitchMessageFragment.setImageResource(R.drawable.pull_up);
            viewBinding.foldSwitchMessageFragment.setTag(R.drawable.pull_up);
            Analysis.changeViewHeightAnimatorStart(viewBinding.foldLayoutMessageFragment,mFoldLayoutHeight,0);
        }else{
            System.out.println("上去");
            viewBinding.foldSwitchMessageFragment.setImageResource(R.drawable.pull_down);
            viewBinding.foldSwitchMessageFragment.setTag(R.drawable.pull_down);
            Analysis.changeViewHeightAnimatorStart(viewBinding.foldLayoutMessageFragment,0,mFoldLayoutHeight);
        }
    }*/

    private void setListState() {
        isShowMyData = mStorage.getData(PopWindowFragment.KEY_DATA);
        isShowTime = mStorage.getData(PopWindowFragment.KEY_TIME);
        isSendHex = mStorage.getData(PopWindowFragment.KEY_HEX_SEND);
        isReadHex = mStorage.getData(PopWindowFragment.KEY_HEX_READ);
        isAutoClear = mStorage.getData(PopWindowFragment.KEY_CLEAR);
        isSendNewline = mStorage.getData(PopWindowFragment.KEY_NEWLINE);
//        if (isSendHex && viewBinding.editMessageFragment.getHint().toString().trim().equals("任意字符")){
//            viewBinding.editMessageFragment.setHint("只可以输入16进制数据");
//            viewBinding.editMessageFragment.setText(Analysis.changeHexString(true,viewBinding.editMessageFragment.getText().toString().trim()));
//        }else if (!isSendHex && viewBinding.editMessageFragment.getHint().toString().trim().equals("只可以输入16进制数据")){
//            viewBinding.editMessageFragment.setHint("任意字符");
//            viewBinding.editMessageFragment.setText(Analysis.changeHexString(false,viewBinding.editMessageFragment.getText().toString().trim()));
//        }
    }

    private void setClearRecycler(int readNumber) {
        mCacheByteNumber += readNumber;
        if (isAutoClear){//开启清除缓存
            if (mCacheByteNumber>400000){//只缓存400K
                mDataList.clear();
                mAdapter.notifyDataSetChanged();
                mCacheByteNumber = 0;
            }
        }
    }

    /**
     * 根据数据末尾是否换行，来判定数据添加到{@link #mDataList}的上一个元素或重新创建一个元素
     * @param data 接收到的数据
     */
    private void addListData(byte[] data) {

        if (!ModuleParameters.isCheckNewline()){//不需要检查换行
            mDataList.add(new FragmentMessageItem( Analysis.getByteToString(data,mFragmentParameter.getCodeFormat(getContext()),
                    isReadHex, false), isShowTime ? Analysis.getTime() : null, false, module, isShowMyData));
            return;
        }
        boolean newline = data[data.length-1] == 10 && data[data.length-2] == 13;
        String dataString = Analysis.getByteToString(data,mFragmentParameter.getCodeFormat(getContext()),
                isReadHex, newline);
        logWarn("dataString:"+dataString);
        // 开始将缓存数据写入手机内存
        if (dataString.contains("Start Playback")){
            logWarn("Start playback");
            readCache = true;
        }
       else if (dataString.contains("Playback all done")){
            logWarn("Playback all done");
            readCache = false;
        }
       if(readCache){
            // 将数据写入到手机内存当中
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                //写入文件中
                String strFilePath = String.valueOf(this.getActivity().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS));
                logWarn("strFilePath："+strFilePath);
                //写入系统文件写入（追加写）
                Analysis.IO_input_data(dataString,strFilePath,"CGM_Cache_data.txt");
            }
            return;
        }
        //根据换行切割
        String []dataFloatString  = dataString.split("\n");

        logWarn("dataFloatStringLength:"+dataFloatString.length);
        for (int i=0;i<dataFloatString.length;i++) {
            //logWarn("dataFloatString:"+dataFloatString[i]);
            //状态是CA和EIS需要绘图，RI时不用绘图
            if(dataFloatString[i].contains("EIS")||dataFloatString[0].contains("CA")){
                getDataStart=true;
                if (dataFloatString[i].contains("EIS")){
                    dataInIOList.add(dataFloatString[i].split(":")[1]);
                }
            }
            else if (dataFloatString[i].contains("RI")){
                getDataStart=false;
            }
            //设置装置状态
            viewBinding.status.setText(dataFloatString[i].split(":")[0]);
            if (dataFloatString[i].contains("RI")){
                viewBinding.status.setText(dataFloatString[i]);
            }
            //获取作图数据
            if (getDataStart)
            {
                //加入到里面,创建一个新的点
                logWarn("chart_data:"+dataFloatString[i].split(":")[1].split(",")[1]);
                if (dataFloatString[i].contains("EIS")){
                    if (dataFloatList.size()>94){
                        dataFloatList.clear();
                        values.clear();
                    }
                    dataFloatList.add(Float.parseFloat(dataFloatString[i].split(":")[1].split(",")[2]));

                }
                else if (dataFloatString[i].contains("CA")){
                    int index=Integer.parseInt(dataFloatString[i].split(":")[1].split(",")[0]);
                    logWarn("currentIndex:"+index);
                    if (index==1){
                        dataFloatList.clear();
                        values.clear();
                    }
                    dataFloatList.add(Float.parseFloat(dataFloatString[i].split(":")[1].split(",")[1]));
                }
                //作图
                logWarn("dataFloatList:"+dataFloatList.toString());
                LineChart lineChart  =this.getActivity().findViewById(R.id.lineChart);
                getLineChart(dataFloatList,lineChart);
            }

            //CA监测30s结束时，,说明检测结束，写入文件
            if (dataFloatString[i].contains("CA:266")) {

                //最后的电流值写入到view中,并且将这个值放入到作图数据中
                viewBinding.currentData.setText(dataFloatString[i].split(":")[1].split(",")[1]);
                currentDataFloatList.add(Float.valueOf(dataFloatString[i].split(":")[1].split(",")[1]));
                LineChart lineChart_2 = this.getActivity().findViewById(R.id.lineChart_current);
                getLineBloodChart(currentDataFloatList,lineChart_2);

                //将时间和电流值写入到文件中
                LocalTime currentTime = null;
                DateTimeFormatter formatter = null;
                String currentTimeString = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

                    currentTime = LocalTime.now();
                    formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
                    currentTimeString = currentTime.format(formatter);
                    //logWarn("当天时间："+today);
                    //logWarn("此刻时分秒："+currentTime);
                    //写入文件中
                    String time_current = "time：" + currentTimeString + " " + dataFloatString[i].split(":")[1].split(",")[1];
                    String strFilePath = String.valueOf(this.getActivity().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS));
                    dataInIOList.add(time_current);
                    logWarn("写入信息："+dataInIOList.toString());
                    //写入系统文件写入（追加写）
                    Analysis.IO_input_data(dataInIOList.toString()+"\n",strFilePath,"的CGM_data.txt");
                    dataInIOList.clear();
                }
            }
        }

        if (mDataList.size()>0 && mDataList.get(mDataList.size()-1).isAddible()){//数组里最后一个元素没有换行符，可以添加数据
            logWarn("数据合并一次...");
            mDataList.get(mDataList.size()-1).addData(dataString,isShowTime?Analysis.getTime():null);
            mDataList.get(mDataList.size()-1).setDataEndNewline(newline);
        }else {//数组元素最后一个元素有换行符，且已经过处理，重新创建一个元素添加数据并放至最后
            logWarn("创建一个新的Item存储: newline is "+newline);
            mDataList.add(new FragmentMessageItem(dataString, isShowTime ? Analysis.getTime() : null, false, module, isShowMyData));
            mDataList.get(mDataList.size()-1).setDataEndNewline(newline);//填入是否有换行符
        }
    }

    /*
     * 根据接收来的数据作折线图
     * dataFloat：作图数据
     * */
    public void getLineChart(List<Float>dataFloat,LineChart lineChart){

        //设置是否可以触摸
        lineChart.setTouchEnabled(true);
        lineChart.setDragDecelerationFrictionCoef(0.99f);
        //设置是否可以拖拽
        lineChart.setDragEnabled(true);
        //设置是否可以缩放
        lineChart.setScaleEnabled(true);
        lineChart.setDrawGridBackground(false);
        lineChart.setHighlightPerDragEnabled(true);
        lineChart.setPinchZoom(true);
        //未接收到数据时
        lineChart.setNoDataText("未接收到传感器数据"); //没有数据时显示的文字
        lineChart.setNoDataTextColor(Color.BLACK);

        Entry entry = new Entry(dataFloat.size(),dataFloat.get(dataFloat.size()-1));
        values.add(entry);

        //通知数据已经改变
        lineChart.notifyDataSetChanged();
        lineChart.invalidate();
        //显示的时候是按照多大的比率缩放显示,1f表示不放大缩小
        lineChart.zoom(0.5f,1f,0,0);
        //描述图形标题
        Description description = new Description();
        //description.setText("CGM折线图");//设置文本
        description.setTextSize(40f); //设置文本大小
        description.setTypeface(Typeface.DEFAULT_BOLD);//设置文本样式加粗显示
        description.setTextColor(Color.RED);//设置文本颜色
        // 获取屏幕中间x轴的像素坐标
        WindowManager wm=(WindowManager)getActivity().getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        float x = dm.widthPixels / 2;
        description.setPosition(x,40);//设置文本的位置
        //lineChart.setDescription(description);//添加给LineChart
        /*
         * 设置Y轴特征
         * */
        //不显示右边Y轴
        lineChart.getAxisRight().setEnabled(false);
        YAxis yAxis  = lineChart.getAxisLeft();
        yAxis.setTextSize(14f);
        yAxis.setXOffset(15);
        yAxis.setDrawGridLines(false);
        /*
         * 设置X轴特征
         * */
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setTextSize(14f);
        xAxis.setTextColor(Color.RED);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);//X轴位于
        xAxis.setDrawGridLines(false);//不绘制网格
        //xAxis.setAxisMaximum(500f);//设置x轴的最大值
        xAxis.setGranularity(5f);//设置x轴间隔50
        /*
         * lineDataSet的设置简单的来说就是对线条的设置，如线条的颜色，线条的圆点代销大小等,也可以添加一条线
         * */
        LineDataSet set1 = new LineDataSet(values,"data"); //设置线条标签
        set1.setFillAlpha(110);
        //设置线条宽度
        set1.setLineWidth(1f);
        //设置线条颜色
        set1.setColor(Color.RED);
        //表示不设置圆点只有直线
        set1.setDrawCircles(false);
        //不显示数值
        set1.setDrawValues(false);
        ArrayList<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(set1);
        LineData lineData = new LineData(dataSets);
        //设置曲线的点在图中的最大数量:不设置
        //lineChart.setVisibleXRangeMaximum((Round+1)*50);
        //缩放:ocp缩放，这里不缩放
        //float ratio = (float) 1/(float)(Round+1);
        lineChart.zoom(0, 1f, 0, 0);
        lineChart.zoom(0.5f,1f,50,0);
        //折线移动到某个位置
        //lineChart.moveViewToX(1);
        lineChart.setData(lineData);
        lineChart.invalidate();

    }
    /*
    * 第二个图，一个周期出现一个点的累加，血糖累计变化趋势
    * */
    public void getLineBloodChart(List<Float>dataFloat,LineChart lineChart){

        //设置是否可以触摸
        lineChart.setTouchEnabled(true);
        lineChart.setDragDecelerationFrictionCoef(0.99f);
        //设置是否可以拖拽
        lineChart.setDragEnabled(true);
        //设置是否可以缩放
        lineChart.setScaleEnabled(true);
        lineChart.setDrawGridBackground(false);
        lineChart.setHighlightPerDragEnabled(true);
        lineChart.setPinchZoom(true);
        //未接收到数据时
        lineChart.setNoDataText("未接收到CGM数据"); //没有数据时显示的文字
        lineChart.setNoDataTextColor(Color.BLACK);
        Entry entry = new Entry(dataFloat.size(),dataFloat.get(dataFloat.size()-1));
        logWarn("entry:"+entry.toString());
        valuesBlood.add(entry);
        //通知数据已经改变
        lineChart.notifyDataSetChanged();
        lineChart.invalidate();
        //显示的时候是按照多大的比率缩放显示,1f表示不放大缩小
        lineChart.zoom(0.5f,1f,0,0);
        //描述图形标题
        Description description = new Description();
        //description.setText("CGM折线图");//设置文本
        description.setTextSize(40f); //设置文本大小
        description.setTypeface(Typeface.DEFAULT_BOLD);//设置文本样式加粗显示
        description.setTextColor(Color.RED);//设置文本颜色
        // 获取屏幕中间x轴的像素坐标
        WindowManager wm=(WindowManager)getActivity().getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        float x = dm.widthPixels / 2;
        description.setPosition(x,40);//设置文本的位置
        //lineChart.setDescription(description);//添加给LineChart
        /*
         * 设置Y轴特征
         * */
        //不显示右边Y轴
        lineChart.getAxisRight().setEnabled(false);
        YAxis yAxis  = lineChart.getAxisLeft();
        yAxis.setTextSize(14f);
        yAxis.setXOffset(15);
        yAxis.setDrawGridLines(false);
        /*
         * 设置X轴特征
         * */
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setTextSize(14f);
        xAxis.setTextColor(Color.RED);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);//X轴位于
        xAxis.setDrawGridLines(false);//不绘制网格
        //xAxis.setAxisMaximum(500f);//设置x轴的最大值
        xAxis.setGranularity(1f);//设置x轴1
        /*
         * lineDataSet的设置简单的来说就是对线条的设置，如线条的颜色，线条的圆点代销大小等,也可以添加一条线
         * */
        LineDataSet set1 = new LineDataSet(valuesBlood,"CA"); //设置线条标签
        set1.setFillAlpha(110);
        //设置线条宽度
        set1.setLineWidth(1f);
        //设置线条颜色
        set1.setColor(Color.RED);
        //表示不设置圆点只有直线
        set1.setDrawCircles(true);
        //显示数值
        set1.setDrawValues(true);
        ArrayList<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(set1);
        LineData lineData = new LineData(dataSets);
        //设置曲线的点在图中的最大数量:不设置
        //lineChart.setVisibleXRangeMaximum((Round+1)*50);
        //缩放:ocp缩放，这里不缩放
        //float ratio = (float) 1/(float)(Round+1);
        lineChart.zoom(0, 1f, 0, 0);
        lineChart.zoom(0.5f,1f,50,0);
        //折线移动到某个位置
        //lineChart.moveViewToX(1);
        lineChart.setData(lineData);
        lineChart.invalidate();

    }

    private void sendData(FragmentMessageItem item) {
        sendDataToActivity(StaticConstants.DATA_TO_MODULE,item);
        //logWarn("发送数据");
        if (isShowMyData) {
            mDataList.add(item);
            mAdapter.notifyDataSetChanged();
            viewBinding.recyclerMessageFragment.smoothScrollToPosition(mDataList.size());
        }
    }

    public void getLine(Object data){

    }

    private void initRecycler(){
        mAdapter = new FragmentMessAdapter(getContext(),mDataList,R.layout.item_message_fragment);

        viewBinding.recyclerMessageFragment.setLayoutManager(new FastScrollLinearLayoutManager(getContext()));
        viewBinding.recyclerMessageFragment.setAdapter(mAdapter);



    }

    private void initEditView() {
/*        viewBinding.editMessageFragment.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                log("charSequence is "+charSequence+" start is "+start+" before is "+before+" count is "+count);
                if (isSendHex) Analysis.setHex(charSequence.toString(),start,before,count,viewBinding.editMessageFragment);
            }
            @Override
            public void afterTextChanged(Editable s) {

            }
        });*/
        view2 = View.inflate(this.getActivity(),R.layout.parameter_setting,null);
        control_ratio_Edit = (EditText)view2.findViewById(R.id.control_ratio_Edit); //占空比
        //control_ratio_Edit.setEnabled(false);
        control_ratio_Edit.setText("60");

        extraction_time_edit = (EditText)view2.findViewById(R.id.extraction_time_edit);//提取时间
        high_level_edit = (EditText)view2.findViewById(R.id.high_level_edit);//高电平时间
        voltage_Edit = (EditText)view2.findViewById(R.id.voltage_Edit);//偏压
        detection_time_edit = (EditText)view2.findViewById(R.id.detection_time_edit);//检测时间
    }
    private void initFoldLayout() {
        /*
        * 设置延时时间
        * */
//        viewBinding.foldSwitchMessageFragment.setTag(R.drawable.pull_down);

        viewBinding.foldLayoutMessageFragment.post(() -> mFoldLayoutHeight = viewBinding.foldLayoutMessageFragment.getHeight());
    }
    // 设置了新的按钮需要在这里初始化
    private void initData() {
        subscription(StaticConstants.FRAGMENT_STATE_DATA,StaticConstants.FRAGMENT_STATE_NUMBER,
                StaticConstants.FRAGMENT_STATE_SEND_SEND_TITLE,StaticConstants.FRAGMENT_STATE_SERVICE_VELOCITY,
                StaticConstants.FRAGMENT_STATE_1,StaticConstants.FRAGMENT_STATE_2,StaticConstants.FRAGMENT_STATE_STOP_LOOP_SEND);
        bindOnClickListener(viewBinding.sendMessageFragment,viewBinding.pullMessageFragment,viewBinding.sentStartFlag,viewBinding.getAllData,viewBinding.deleteCacheData);
      //  viewBinding.editMessageFragment.setText(mStorage.getSaveInputData());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
       // mStorage.saveInputData(viewBinding.editMessageFragment.getText().toString().trim());
        HoldBluetooth.getInstance().stopSend(module,null);
        mTimer.cancel();
    }

}