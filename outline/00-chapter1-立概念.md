# Fragment Message 管线重构 — 代码审查路线图

## Chapter 1：立概念 — 这次重构在做什么

### 1.1 Before / After 对比

**改动前：** FragmentMessage.java 130+ 行字段 + 50+ 方法，全部业务混在一个类里

```
FragmentMessage (God Object)
├── 25+ 个字段：connected, module, mAdapter, values, valuesBlood, Timer...
├── 50+ 个方法：appendIncomingMessage, refreshMessageList, handleEISData...
├── 直接调用 LineChart API、MediaPlayer、Dialog
└── Fragment = 什么都干
```

**改动后：** FragmentMessage.java 9 个字段 + 9 个 init 方法

```
FragmentMessage (组装者)
├── 9 个字段：runtime, options, profile, messageList, chartRegistry...
├── 9 个 init 方法链
└── Fragment = 只负责组装，不写业务
```

---

### 1.2 核心理念：三个关键词

#### 1. 解耦 (Decoupling)
- **原来：** 一个类干所有事
- **现在：** 每件事一个组件，Fragment 只负责"组装"

#### 2. 抽象 (Abstraction)
- **原来：** 直接调用 `LineChart.setData()`、`MediaPlayer.play()`
- **现在：** 通过 `SampleConsumer` 接口，不关心谁消费

#### 3. 插件化 (Pluggable)
- **原来：** 硬编码 CGM 逻辑
- **现在：** `DeviceProfile` 接口，换设备就换个 Profile

---

### 1.3 改动文件分类总览

#### A. 通用 Lego 块（不依赖具体设备）

```
activity/tool/profile/          ← 设备策略模式
├── DeviceProfile.java         ← 接口：每个设备一个实现
├── ProfileContext.java        ← 运行时依赖容器
└── UserRolePolicy.java        ← 权限策略

activity/tool/sample/           ← 样本数据抽象
├── BluetoothSample.java       ← 样本接口（type + raw + metrics）
├── BluetoothSampleParser.java
├── BluetoothSampleRegistry.java  ← Parser 注册表
├── SampleConsumer.java        ← 消费者接口
└── SampleConsumerRegistry.java   ← Consumer 注册表

activity/tool/message/         ← 消息处理
├── MessagePipelineController.java  ← 管线大脑（收到数据→解析→分发）
├── MessageSender.java         ← 发送封装
├── MessageOptions.java        ← 选项（不可变 POJO）
├── MessageOptionStore.java    ← 从 Storage 加载
├── MessageListController.java ← RecyclerView 封装
├── ControlRegistry.java       ← 按钮事件注册表
├── ControlAction.java         ← 按钮动作接口
└── BluetoothPayloadDecoder.java  ← 字节解码

activity/tool/runtime/         ← 运行环境
└── FragmentRuntime.java       ← Fragment 的"上下文"

activity/tool/chart/           ← 图表管理
├── ChartRegistry.java
└── SampleChartBinder.java     ← Sample → Chart 的绑定器
```

#### B. CGM Schema（具体设备实现）

```
schema/cgm/
├── CgmProfile.java            ← 注册所有 CGM 组件
├── CgmSample.java             ← CGM 样本模型
├── CgmParser.java            ← 解析 "CA" "EIS" "RI" 等命令
├── CgmCommandSet.java        ← 生成命令字符串
├── CgmStatusConsumer.java    ← 更新状态 UI
├── CgmCurrentValueConsumer.java  ← 更新数值 UI
├── CgmRecorderConsumer.java  ← 录制时写 JSONL
├── CgmFileConsumer.java      ← 缓存读写
├── CgmJsonLineBuilder.java   ← JSON 行构造
├── CgmParameters.java        ← 参数模型
└── CgmParameterDialog.java   ← 参数弹窗
```

#### C. 测试

```
tests/schema/cgm/
├── CgmParserTest.java
└── CgmCommandSetTest.java
```

---

### 1.4 数据流全景图

```
┌─────────────────────────────────────────────────────────────────┐
│                        FragmentMessage                          │
│                      (组装者，不写业务)                          │
└─────────────────────────────────────────────────────────────────┘
                                │
              ┌─────────────────┼─────────────────┐
              ▼                 ▼                 ▼
     ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
     │   runtime   │    │   profile   │    │   options   │
     │ (运行环境)  │    │  (设备策略)  │    │   (配置)    │
     └─────────────┘    └─────────────┘    └─────────────┘
              │                 │                 │
              └────────┬────────┴────────┬────────┘
                       ▼                 ▼
              ┌─────────────────┐ ┌─────────────────┐
              │  MessagePipeline │ │   ControlReg    │
              │    Controller    │ │   (按钮事件)    │
              └────────┬────────┘ └────────┬────────┘
                       │                   │
         ┌─────────────┼───────────────────┘
         ▼             ▼
┌─────────────┐  ┌─────────────┐
│ SampleReg   │  │ ConsumerReg │
│ (解析器注册) │  │ (消费者注册) │
└──────┬──────┘  └──────┬──────┘
       │                │
       ▼                ▼
┌─────────────┐  ┌──────────────────────────────┐
│  CgmParser  │  │  CgmStatusConsumer           │
│  (解析数据)  │  │  CgmCurrentValueConsumer     │
└──────┬──────┘  │  CgmRecorderConsumer         │
       │         │  CgmFileConsumer             │
       ▼         │  SampleChartBinder           │
┌─────────────┐  └──────────────────────────────┘
│  CgmSample  │         ▲
│  (样本数据)  │         │ 消费
└─────────────┘         │
                        ▼
              ┌─────────────────────┐
              │  UI / Chart / File   │
              └─────────────────────┘
```

---

### 1.5 为什么要这样改？

**原来的痛点：**
1. FragmentMessage 130+ 行字段，改一个小功能要找半天
2. CGM 逻辑和 Fragment 强耦合，要支持新设备得大改
3. 测试困难，因为逻辑都埋在 UI 层

**现在的优势：**
1. 每个组件职责单一，容易理解
2. 设备逻辑抽到 Profile，换设备换 Profile 就行
3. Consumer 可以独立测试
4. Fragment 代码从 300+ 行减到 100 行

---

### 1.6 下一步：Chapter 2

我们将从 FragmentMessage 的生命周期开始，看它是如何"组装"这些 Lego 块的。
