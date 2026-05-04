package com.hc.mixthebluetooth.fragment;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
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
import com.hc.basiclibrary.viewBasic.HomeApplication;
import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.bluetoothlibrary.tootl.ModuleParameters;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.single.BTPackage;
import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.activity.single.HoldBluetooth;
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.activity.tool.message.BluetoothPayloadDecoder;
import com.hc.mixthebluetooth.activity.tool.message.MessageItemTools;
import com.hc.mixthebluetooth.customView.PopWindowFragment;
import com.hc.mixthebluetooth.customView.dialog.InvalidHint;
import com.hc.mixthebluetooth.databinding.FragmentMessageBinding;
import com.hc.mixthebluetooth.recyclerData.FragmentMessAdapter;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;
import com.hc.mixthebluetooth.storage.Storage;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class FragmentMessage extends BTFragment<FragmentMessageBinding> {

    private boolean connected = false;

    private FragmentMessAdapter mAdapter;

    private final List<FragmentMessageItem> mDataList = new ArrayList<>();

    private DeviceModule module = null;

    private Storage mStorage;

    private FragmentParameter mFragmentParameter;

    private int mCacheByteNumber = 0;//mCacheByteNumber: 缓存的字节数

    private boolean isShowMyData, isSendHex, isShowTime, isReadHex, isAutoClear, isSendNewline;//弹出窗的六个选择

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
    private EditText extraction_time_edit;//提取时间
    private EditText high_level_edit;//高电平时间
    private EditText voltage_Edit;//偏压
    private EditText detection_time_edit;//检测时间
    private String detectionString = "";

    List<Float> dataFloatList = new ArrayList<>(); //作图数据EIS和CA
    List<Float> currentDataFloatList = new ArrayList<>(); //连续血糖作图数据
    List<String> dataInIOList = new ArrayList<>();// 备份系统的数据：CA稳定的最后一点+EIS数据

    @Override
    protected void initChannels() {
        register(
                StaticConstants.CH_BT_DATA,
                StaticConstants.CH_SET_CONNECT_STATE,
                StaticConstants.CH_SENT_BYTES,
                StaticConstants.CH_VELOCITY,
                StaticConstants.CH_SET_SPEED_VISIBLE,
                StaticConstants.CH_STOP_LOOP_SEND
        );
    }


    @Override
    public void initAllImpl(View view, Context context) {

        mStorage = new Storage(context);
        mFragmentParameter = FragmentParameter.getInstance();
        setListState();
        //setParameter();
        initRecycler();
        initEditView();
        initFoldLayout();
        initLimit();

    }

    // ----------------- Bluetooth Callbacks -----------------
    @Override
    protected void onBtConnected(DeviceModule module) {
        this.module = module;
    }

    @Override
    protected void onBtDisconnected() {
        connected = false;
        module = null;
    }

    @Override
    protected void onBtData(BTPackage.BTData packet) {
        module = packet.module;
        handleIncomingBytes(packet.bytes.clone());
    }

    private void handleIncomingBytes(byte[] bytes) {
        appendIncomingMessage(bytes);
        refreshMessageList();
        addReadBytes(bytes.length);
        setClearRecycler(bytes.length);
    }

    private void appendIncomingMessage(byte[] bytes) {
        addListData(bytes);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void refreshMessageList() {
        mAdapter.notifyDataSetChanged();
        if (!mDataList.isEmpty()) {
            viewBinding.recyclerMessageFragment.smoothScrollToPosition(mDataList.size() - 1);
        }
    }

    private void addReadBytes(int byteCount) {
        int oldSize = parseCounter(viewBinding.sizeReadMessageFragment.getText());
        viewBinding.sizeReadMessageFragment.setText(String.valueOf(oldSize + byteCount));
    }

    private int parseCounter(CharSequence text) {
        if (text == null) return 0;

        try {
            return Integer.parseInt(text.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    protected void onConnectStateChanged(String state) {
        connected = "已连接".equals(state);
    }

    @Override
    protected void onSentBytesChanged(int number) {
        int oldSize = parseCounter(viewBinding.sizeSendMessageFragment.getText());
        viewBinding.sizeSendMessageFragment.setText(String.valueOf(oldSize + number));
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onVelocityChanged(int velocity) {
        viewBinding.readVelocityMessageFragment.setText("速度: " + velocity + "B/s");
    }

    @Override
    protected void onSpeedVisibleChanged(boolean visible) {
        viewBinding.readHintMessageFragment.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onStopLoopSend() {
        stopLoopSend();
    }

    private void stopLoopSend() {
        if (mTimerTask == null) return;

        viewBinding.sendMessageFragment.setText("发送");
        mTimerTask.cancel();
        mTimerTask = null;

        logWarn("Fragment Message 停止发送");
        HoldBluetooth.getInstance().stopSend(module, null);
    }


    /*
     * 设置系统参数，电压、占空比等
     * */
    public void setParameter() {
        //Toast.makeText(getActivity(),"单击", Toast.LENGTH_SHORT).show();
        AlertDialog.Builder builder = new AlertDialog.Builder(this.getActivity());
        /*View view2 = View.inflate(this.getActivity(),R.layout.parameter_setting,null);*/
        control_ratio_Edit = (EditText) view2.findViewById(R.id.control_ratio_Edit); //占空比
        extraction_time_edit = (EditText) view2.findViewById(R.id.extraction_time_edit);//提取时间
        high_level_edit = (EditText) view2.findViewById(R.id.high_level_edit);//高电平时间
        voltage_Edit = (EditText) view2.findViewById(R.id.voltage_Edit);//偏压
        detection_time_edit = (EditText) view2.findViewById(R.id.detection_time_edit);//检测时间
        builder.setTitle("Settings").setIcon(R.drawable.setting).setView(view2).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        }).setPositiveButton("Submit", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                //将参数完全拼接起来，五个参数一共是5*6=30位数字

                String control_ratio_EditString = analysis.isGetZero(control_ratio_Edit.getText().toString().trim(), "000");

                String extractionTimeString = "0010" + extraction_time_edit.getText().toString().trim();

                String highLevelEditString = analysis.isGetZero(high_level_edit.getText().toString().trim(), "010");

                String voltageString = "011" + voltage_Edit.getText().toString().trim();

                String detectionTime = analysis.isGetZero(detection_time_edit.getText().toString().trim(), "100");

                //Toast.makeText(getActivity(),voltageString+control_ratio_EditString, Toast.LENGTH_SHORT).show();

                //将参数完全拼接起来，五个参数一共是5*6=30位数字

                data = control_ratio_EditString + extractionTimeString + highLevelEditString + voltageString + detectionTime;

                logWarn("数据：" + data);
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
    public void initLimit() {
        //获取当前登录的权限
        homeApplication = (HomeApplication) this.getActivity().getApplication();
        limit = homeApplication.getLimits();
        if (limit.equals("ordinary")) {
            //普通用户
            Toast.makeText(this.getActivity(), "进入普通用户", Toast.LENGTH_SHORT).show();
            control_ratio_Edit.setEnabled(false);
            extraction_time_edit.setEnabled(false);
            ;//提取时间
            high_level_edit.setEnabled(false);
            ;//高电平时间
            voltage_Edit.setEnabled(false);//偏压
            detection_time_edit.setEnabled(false);//检测时间

        } else if (limit.equals("admin")) {
            Toast.makeText(this.getActivity(), "进入管理员用户", Toast.LENGTH_SHORT).show();
            //管理员用户
            control_ratio_Edit.setEnabled(true);
            extraction_time_edit.setEnabled(true);
            ;//提取时间
            high_level_edit.setEnabled(true);
            ;//高电平时间
            voltage_Edit.setEnabled(true);//偏压
            detection_time_edit.setEnabled(true);//检测时间
        }
    }

    public void onClickView(View view) {
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
        else if (isCheck(viewBinding.sentStartFlag)) {
            setStartFlag();
        }
        //读取缓存中的数据，获取所有数据
        else if (isCheck(viewBinding.getAllData)) {
            getBufferData();
        } else if (isCheck(viewBinding.deleteCacheData)) {
            deleteBufferData();
        }
        if (false) {
            if (viewBinding.sendMessageFragment.getText().toString().equals("发送")) {
                //viewBinding.editMessageFragment.setText("");
            } else {
                toastShort("连续发送中，不能清除发送区的数据");
            }
        } else if (isCheck(viewBinding.pullMessageFragment)) {
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
    private void getBufferData() {
        data = "ALL" + "\n\r";
        setSendData();
    }

    /**
     * 删除缓存中的数据
     */
    private void deleteBufferData() {
        data = "DELETE" + "\n\r";
        setSendData();
    }

    /**
     * 设备开始工作按钮方法，读取对应时间
     */
    private void setStartFlag() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy,MM,dd,HH,mm,ss", Locale.getDefault());
        String formattedTime = sdf.format(new Date());
        data = "TIME," + formattedTime + "\n\r";
        logWarn("sentStartFlag:" + data);
        setSendData();
    }

    @Override
    protected FragmentMessageBinding getViewBinding() {
        return FragmentMessageBinding.inflate(getLayoutInflater());
    }

    private void setSendData() {
        if (!canSend()) return;

        String text = prepareSendText(data);
        sendData(createOutgoingItem(text));

        // dataScreening(viewBinding.editMessageFragment.getText().toString());
    }

    private boolean canSend() {
        if (!connected) {
            toastShort("当前状态不可以发送数据");
            return false;
        }

        if (module == null) {
            toastShort("当前没有可发送的蓝牙模块");
            return false;
        }

        if (data == null || data.trim().isEmpty()) {
            toastShort("不能发送空数据");
            return false;
        }

        return true;
    }

    private String prepareSendText(String raw) {
        String text = raw;

        if (isSendNewline) {
            text += isSendHex ? "0D0A" : "\r\n";
        }

        if (isSendHex) {
            text = text.replaceAll(" ", "");
        }

        return text;
    }

    private FragmentMessageItem createOutgoingItem(String text) {
        byte[] bytes = Analysis.getBytes(
                text,
                mFragmentParameter.getCodeFormat(getContext()),
                isSendHex
        );

        return MessageItemTools.outgoing(
                isSendHex,
                bytes,
                isShowTime ? Analysis.getTime() : null,
                module,
                isShowMyData
        );
    }


    private void dataScreening(String data) {
        String str = "AT+";
        if (data.length() < str.length()) return;
        String temp = data.substring(0, str.length());
        if (temp.equals(str) && mStorage.getInvalidAT()) {
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
        if (isAutoClear) {//开启清除缓存
            if (mCacheByteNumber > 400000) {//只缓存400K
                mDataList.clear();
                mAdapter.notifyDataSetChanged();
                mCacheByteNumber = 0;
            }
        }
    }

    private void addListData(byte[] data) {
        if (data == null || data.length == 0) return;

        boolean checkNewline = ModuleParameters.isCheckNewline();
        BluetoothPayloadDecoder.Result decoded = decodeIncomingBytes(data, checkNewline);
        String dataString = decoded.text;

        if (!checkNewline) {
            appendIncomingItem(dataString, false);
            return;
        }

        handleDecodedLegacyMessage(dataString, decoded.endsWithLineBreak);
    }

    private void handleDecodedLegacyMessage(String dataString, boolean endsWithLineBreak) {
        logWarn("dataString:" + dataString);

        if (handlePlaybackCache(dataString)) {
            return;
        }

        handleLegacyMeasurementText(dataString);
        appendOrMergeIncomingItem(dataString, endsWithLineBreak);
    }

    private boolean handlePlaybackCache(String dataString) {
        if (dataString.contains("Start Playback")) {
            logWarn("Start playback");
            readCache = true;
        } else if (dataString.contains("Playback all done")) {
            logWarn("Playback all done");
            readCache = false;
        }

        if (readCache) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                String strFilePath = String.valueOf(this.getActivity().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS));
                logWarn("strFilePath: " + strFilePath);
                Analysis.IO_input_data(dataString, strFilePath, "CGM_Cache_data.txt");
            }
            return true;
        }
        return false;
    }

    private void handleLegacyMeasurementText(String dataString) {
        String[] dataFloatString = dataString.split("\n");

        logWarn("dataFloatStringLength:" + dataFloatString.length);
        for (int i = 0; i < dataFloatString.length; i++) {
            handleLegacyMeasurementLine(dataFloatString, i);
        }
    }

    private void handleLegacyMeasurementLine(String[] dataFloatString, int i) {
        String line = dataFloatString[i];

        if (line.contains("EIS") || dataFloatString[0].contains("CA")) {
            getDataStart = true;
            if (line.contains("EIS")) {
                dataInIOList.add(line.split(":")[1]);
            }
        } else if (line.contains("RI")) {
            getDataStart = false;
        }

        viewBinding.status.setText(line.split(":")[0]);
        if (line.contains("RI")) {
            viewBinding.status.setText(line);
        }

        if (getDataStart) {
            appendLegacyChartPoint(line);
        }

        if (line.contains("CA:266")) {
            appendLegacyCurrentPoint(line);
        }
    }

    private void appendLegacyChartPoint(String line) {
        logWarn("chart_data:" + line.split(":")[1].split(",")[1]);
        if (line.contains("EIS")) {
            if (dataFloatList.size() > 94) {
                dataFloatList.clear();
                values.clear();
            }
            dataFloatList.add(Float.parseFloat(line.split(":")[1].split(",")[2]));

        } else if (line.contains("CA")) {
            int index = Integer.parseInt(line.split(":")[1].split(",")[0]);
            logWarn("currentIndex:" + index);
            if (index == 1) {
                dataFloatList.clear();
                values.clear();
            }
            dataFloatList.add(Float.parseFloat(line.split(":")[1].split(",")[1]));
        }

        logWarn("dataFloatList:" + dataFloatList.toString());
        LineChart lineChart = this.getActivity().findViewById(R.id.lineChart);
        getLineChart(dataFloatList, lineChart);
    }

    private void appendLegacyCurrentPoint(String line) {
        viewBinding.currentData.setText(line.split(":")[1].split(",")[1]);
        currentDataFloatList.add(Float.valueOf(line.split(":")[1].split(",")[1]));
        LineChart lineChart = this.getActivity().findViewById(R.id.lineChart_current);
        getLineBloodChart(currentDataFloatList, lineChart);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            LocalTime currentTime = LocalTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            String currentTimeString = currentTime.format(formatter);
            String time_current = "time: " + currentTimeString + " " + line.split(":")[1].split(",")[1];
            String strFilePath = String.valueOf(this.getActivity().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS));
            dataInIOList.add(time_current);
            logWarn("write info: " + dataInIOList.toString());
            Analysis.IO_input_data(dataInIOList.toString() + "\n", strFilePath, "鐨凜GM_data.txt");
            dataInIOList.clear();
        }
    }
    private BluetoothPayloadDecoder.Result decodeIncomingBytes(byte[] data, boolean removeTrailingCrLf) {
        return BluetoothPayloadDecoder.decodeResult(
                data,
                mFragmentParameter.getCodeFormat(getContext()),
                new BluetoothPayloadDecoder.Options.Builder()
                        .hex(isReadHex)
                        .removeTrailingCrLf(removeTrailingCrLf)
                        .cleanNull(true)
                        .trim(false)
                        .build()
        );
    }

    private void appendIncomingItem(String text, boolean endsWithNewline) {
        MessageItemTools.appendIncoming(
                mDataList,
                text,
                endsWithNewline,
                isShowTime ? Analysis.getTime() : null,
                module,
                isShowMyData
        );
    }

    private void appendOrMergeIncomingItem(String text, boolean endsWithNewline) {
        MessageItemTools.appendOrMergeIncoming(
                mDataList,
                text,
                endsWithNewline,
                isShowTime ? Analysis.getTime() : null,
                module,
                isShowMyData
        );
    }

    /*
     * 根据接收来的数据作折线图
     * dataFloat：作图数据
     * */
    public void getLineChart(List<Float> dataFloat, LineChart lineChart) {

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

        Entry entry = new Entry(dataFloat.size(), dataFloat.get(dataFloat.size() - 1));
        values.add(entry);

        //通知数据已经改变
        lineChart.notifyDataSetChanged();
        lineChart.invalidate();
        //显示的时候是按照多大的比率缩放显示,1f表示不放大缩小
        lineChart.zoom(0.5f, 1f, 0, 0);
        //描述图形标题
        Description description = new Description();
        //description.setText("CGM折线图");//设置文本
        description.setTextSize(40f); //设置文本大小
        description.setTypeface(Typeface.DEFAULT_BOLD);//设置文本样式加粗显示
        description.setTextColor(Color.RED);//设置文本颜色
        // 获取屏幕中间x轴的像素坐标
        WindowManager wm = (WindowManager) getActivity().getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        float x = dm.widthPixels / 2;
        description.setPosition(x, 40);//设置文本的位置
        //lineChart.setDescription(description);//添加给LineChart
        /*
         * 设置Y轴特征
         * */
        //不显示右边Y轴
        lineChart.getAxisRight().setEnabled(false);
        YAxis yAxis = lineChart.getAxisLeft();
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
        LineDataSet set1 = new LineDataSet(values, "data"); //设置线条标签
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
        lineChart.zoom(0.5f, 1f, 50, 0);
        //折线移动到某个位置
        //lineChart.moveViewToX(1);
        lineChart.setData(lineData);
        lineChart.invalidate();

    }

    /*
     * 第二个图，一个周期出现一个点的累加，血糖累计变化趋势
     * */
    public void getLineBloodChart(List<Float> dataFloat, LineChart lineChart) {

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
        Entry entry = new Entry(dataFloat.size(), dataFloat.get(dataFloat.size() - 1));
        logWarn("entry:" + entry.toString());
        valuesBlood.add(entry);
        //通知数据已经改变
        lineChart.notifyDataSetChanged();
        lineChart.invalidate();
        //显示的时候是按照多大的比率缩放显示,1f表示不放大缩小
        lineChart.zoom(0.5f, 1f, 0, 0);
        //描述图形标题
        Description description = new Description();
        //description.setText("CGM折线图");//设置文本
        description.setTextSize(40f); //设置文本大小
        description.setTypeface(Typeface.DEFAULT_BOLD);//设置文本样式加粗显示
        description.setTextColor(Color.RED);//设置文本颜色
        // 获取屏幕中间x轴的像素坐标
        WindowManager wm = (WindowManager) getActivity().getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        float x = dm.widthPixels / 2;
        description.setPosition(x, 40);//设置文本的位置
        //lineChart.setDescription(description);//添加给LineChart
        /*
         * 设置Y轴特征
         * */
        //不显示右边Y轴
        lineChart.getAxisRight().setEnabled(false);
        YAxis yAxis = lineChart.getAxisLeft();
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
        LineDataSet set1 = new LineDataSet(valuesBlood, "CA"); //设置线条标签
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
        lineChart.zoom(0.5f, 1f, 50, 0);
        //折线移动到某个位置
        //lineChart.moveViewToX(1);
        lineChart.setData(lineData);
        lineChart.invalidate();

    }

    private void sendData(FragmentMessageItem item) {
        sendDataToActivity(StaticConstants.CMD_SEND_BT_DATA, item);
        //logWarn("发送数据");
        if (isShowMyData) {
            mDataList.add(item);
            mAdapter.notifyDataSetChanged();
            viewBinding.recyclerMessageFragment.smoothScrollToPosition(mDataList.size());
        }
    }

    public void getLine(Object data) {

    }

    private void initRecycler() {
        mAdapter = new FragmentMessAdapter(getContext(), mDataList, R.layout.item_message_fragment);

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
        view2 = View.inflate(this.getActivity(), R.layout.parameter_setting, null);
        control_ratio_Edit = (EditText) view2.findViewById(R.id.control_ratio_Edit); //占空比
        //control_ratio_Edit.setEnabled(false);
        control_ratio_Edit.setText("60");

        extraction_time_edit = (EditText) view2.findViewById(R.id.extraction_time_edit);//提取时间
        high_level_edit = (EditText) view2.findViewById(R.id.high_level_edit);//高电平时间
        voltage_Edit = (EditText) view2.findViewById(R.id.voltage_Edit);//偏压
        detection_time_edit = (EditText) view2.findViewById(R.id.detection_time_edit);//检测时间
    }

    private void initFoldLayout() {
        /*
         * 设置延时时间
         * */
//        viewBinding.foldSwitchMessageFragment.setTag(R.drawable.pull_down);

        viewBinding.foldLayoutMessageFragment.post(() -> mFoldLayoutHeight = viewBinding.foldLayoutMessageFragment.getHeight());
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        // mStorage.saveInputData(viewBinding.editMessageFragment.getText().toString().trim());
        HoldBluetooth.getInstance().stopSend(module, null);
        mTimer.cancel();
    }

}
