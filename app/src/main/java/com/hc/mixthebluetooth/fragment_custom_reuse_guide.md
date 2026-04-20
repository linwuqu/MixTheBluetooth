# FragmentCustom 复刻与新页面接入攻略

这份文档是给“第一次在这个项目里新增一个通信 Fragment，并且希望复用现有 `FragmentCustom` 逻辑”的人写的。

目标不是让你一次性重构漂亮，而是先让你:

1. 把新页面成功注册到通信页里
2. 把蓝牙数据成功送到新页面里
3. 把图画出来
4. 在页面已经跑通之后，再逐步把杂乱逻辑抽出去

如果你是第一次动这个项目，先记住一句话:

**不要从 `FragmentIonAnalysis` 开始抄，先从 `FragmentCustom` 开始。**

原因很简单:

- `FragmentCustom` 是当前真正挂在 `CommunicationActivity` 里的活跃页面
- `FragmentCustom` 已经走通了“订阅蓝牙数据 -> 解析 -> 刷新图表”的链路
- `FragmentIonAnalysis` 里有一套实验性质的写法，生命周期和 `BaseFragment` 的约定不一致，不适合当第一份学习模板

---

## 1. 先建立全局脑图

在你开始复制页面之前，先把这个链路背下来:

`MainActivity` 负责扫描和连接设备  
-> `HoldBluetooth` 持有蓝牙管理单例  
-> `AllBluetoothManage` 接底层 BLE / 经典蓝牙  
-> `CommunicationActivity` 接收所有通信事件  
-> `CommunicationActivity` 通过 `LiveEventBus` 把数据分发给各个 Fragment  
-> 各个 Fragment 在 `updateState()` 里接消息并刷新 UI

你新增页面时，真正要接的不是底层蓝牙库，而是 **`CommunicationActivity` 分发出来的总线消息**。

所以你的关注顺序必须是:

1. 先看 `BaseFragment`
2. 再看 `CommunicationActivity`
3. 再看 `FragmentCustom`

建议按这个顺序读代码:

- `basiclibrary/src/main/java/com/hc/basiclibrary/viewBasic/BaseFragment.java`
- `basiclibrary/src/main/java/com/hc/basiclibrary/viewBasic/BaseActivity.java`
- `app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java`
- `app/src/main/java/com/hc/mixthebluetooth/activity/single/StaticConstants.java`
- `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentCustom.java`

---

## 2. 先明确你要复刻的不是“所有功能”，而是“骨架”

第一次复刻页面，不要试图把下面这些东西一次做全:

- 自定义按钮区
- 折叠动画
- 历史日志列表
- 缓存清空
- 各种统计卡片
- 各种状态联动

第一次只复刻 4 个核心能力:

1. 页面能被注册并切换出来
2. 页面能收到蓝牙数据
3. 页面能解析出你要的核心数值
4. 页面能把这些数值画成图

剩下所有内容都属于第二阶段。

**最安全的策略是: 先复制 `FragmentCustom` 的结构，删功能，不要一开始就重写。**

---

## 3. 第一步先改通信页注册，而不是先写新 Fragment

很多人会先去新建 Java 和 XML，这样不算错，但很容易写完后不知道页面有没有真正接上。

更稳的顺序是:

1. 先在通信页里找到“页面注册点”
2. 搞清楚 tab 和 page 的索引关系
3. 再新建 Fragment 文件

### 3.1 通信页里跟“注册页面”有关的地方

你要同时看 3 个位置:

- `app/src/main/res/layout/activity_communication.xml`
- `app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java` 的 `onClickView()`
- `app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java` 的 `initFragment()`
- `app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java` 的 `setPositionListener()`

### 3.2 这里有一个现成的坑

当前项目里 tab 和 page 的索引映射本身有点乱。

你不要盲信变量名 `one / two / three / ionAnalysis` 对应的就是第 1/2/3/4 页。

你应该自己画一个表，写清楚:

| tab 控件 | 点击后跳转到第几页 | 第几页实际 add 的是谁 |
| --- | --- | --- |
| `one` | `0` | `FragmentMessage` |
| `two` | `1` | `FragmentCustom` |
| `three` | `2` | 现在又是一个 `FragmentCustom` |
| `ionAnalysis` | `3` | `FragmentThree` |

也就是说，现在代码里并不是“ionAnalysis tab 打开 ionAnalysis fragment”。

这一步你一定要先看懂，否则你以后每加一个页，索引会越来越乱。

### 3.3 新页面第一次接入时，最省事的做法

不要一上来新增很多 tab。

最简单的做法是:

- 先把当前重复的第二个 `FragmentCustom` 替换成你的新页面
- 这样你只需要改 `addFragment()` 和页签映射
- 等页面稳定以后，再决定要不要新增独立 tab 文案和图标

你可以把“第二个 `FragmentCustom` 的坑位”当成实验位。

这样做的好处:

- 改动面最小
- 出问题时更容易定位
- 你能确认问题是“新 Fragment 自己的问题”，不是“注册层映射错了”

---

## 4. 第二步再新建 XML，先保留结构，再逐步删

### 4.1 新布局不要从空白开始画

第一次最稳的方式:

1. 复制 `fragment_custom.xml`
2. 改一个新的文件名
3. 先保证能生成新的 ViewBinding
4. 先不急着删所有控件

原因:

- `FragmentCustom` 里初始化了很多控件
- 如果你一开始就把 XML 大删特删，Java 里会立刻产生一堆空引用或找不到 id 的问题
- 学习阶段更适合“先完整跑起来，再做减法”

### 4.2 你第一次复制 XML 时的原则

第一轮保留:

- 两个 `LineChart`
- 顶部状态卡片
- 关键 `TextView`
- 最外层容器

第一轮可以暂时不保留:

- 底部按钮区切换容器
- 自定义按钮组容器
- 读数原文 RecyclerView
- 折叠箭头和折叠区域

也就是说:

**如果你的目标是“新页面只展示蓝牙数据并画图”，那第一版完全可以比 `FragmentCustom` 更简单。**

### 4.3 XML 里最容易踩的坑

如果你删了下面这些控件，但是 Java 里还在 `findViewById()` 或 `viewBinding.xxx` 访问，就会出错:

- `chart_sodium`
- `chart_potassium`
- `tv_bluetooth_device_name`
- `tv_ion_value`
- `tv_sweat_flow_speed_value`
- 各类 max / min / fluctuation / time 的 `TextView`

所以第一次复制后，建议你先做这件事:

- 打开 `FragmentCustom.initView()`
- 把里面访问到的控件 id 全列出来
- 对照新 XML 检查是否都存在

如果你打算删某块 UI，那就同步删掉它在 `initView()`、`updateSummaryData()`、`updateDisplayValue()` 里的使用。

---

## 5. 第三步新建 Fragment 类，只搭骨架

你的新类第一次只需要做到“像 `FragmentCustom` 一样活着”，不需要一开始就有完整业务。

你要先搭出这 4 个固定入口:

- `getViewBinding()`
- `initAll(View view, Context context)`
- `updateState(String sign, Object o)`
- `onClickView(View view)` 或者先留空

### 5.1 先理解 `BaseFragment` 的约定

这个项目里 `BaseFragment` 的核心约定是:

- `onCreateView()` 里自动创建 `viewBinding`
- 然后马上调用 `initAll(viewBinding.getRoot(), getContext())`
- 总线订阅统一走 `subscription(...)`
- Activity / Fragment 之间通信统一走 `LiveEventBus`

所以在这个项目里:

- `initAll()` 是页面初始化入口
- `updateState()` 是页面接收异步消息的入口

你后面新增的页面，思路都一样。

### 5.2 第一次复制时，`initAll()` 应该做什么

第一次最推荐的顺序是:

1. 初始化本地存储对象
2. 初始化全局参数对象
3. 初始化页面控件引用
4. 初始化图表
5. 初始化数据集合
6. 订阅总线消息

如果你要做一个“只接收 + 画图”的新页面，第一次的 `initAll()` 可以非常克制:

- `initView(view)`
- `initDataSet()`
- `initData()`

其它像 `initRecycler()`、`initFragment()`、`setViewHeight()` 都可以先不要。

这一步的关键思想是:

**只保留你当前目标真正需要的初始化。**

---

## 6. 第四步接蓝牙数据，先只订阅一个 key

第一次接数据，你只需要订阅一个总线 key:

- `StaticConstants.FRAGMENT_STATE_DATA`

### 6.1 这个 key 的数据有两种形态

这是这个项目里最重要、也最容易忽略的点。

`FRAGMENT_STATE_DATA` 收到的对象不是固定类型，它会有两种情况:

#### 情况 A: 连接刚成功

这时候 `CommunicationActivity` 发出来的是:

- `DeviceModule`

这类消息的作用是:

- 告诉 Fragment 当前连接的是哪个设备
- 让页面有机会显示设备名、mac、类型等信息

#### 情况 B: 真正收到蓝牙数据包

这时候 `CommunicationActivity` 发出来的是:

- `Object[]{ DeviceModule, byte[] }`

这类消息的作用是:

- 把设备信息和一帧原始数据一起送给 Fragment

所以你在 `updateState()` 里一定要先分型:

1. 先判断是不是 `DeviceModule`
2. 再判断是不是 `Object[]`
3. 再从 `Object[]` 里取出 `byte[]`

**不要直接把收到的 `o` 强转成 `byte[]`。**

### 6.2 真实数据到底是谁发过来的

你要知道数据不是 Fragment 主动去蓝牙层拿的，而是这条链路推过来的:

`AllBluetoothManage` 底层回调  
-> `HoldBluetooth.OnReadDataListener.readData()`  
-> `CommunicationActivity.initDataListener().readData()`  
-> `sendDataToFragment(StaticConstants.FRAGMENT_STATE_DATA, new Object[]{module, data})`

也就是说:

你的新页面不需要主动碰 BLE API。

你真正要做的是:

- 订阅
- 接收
- 解析
- 刷 UI

---

## 7. 第五步先把“解析”和“画图”分成两个方法

第一次复制 `FragmentCustom` 时，最不应该做的事情就是把所有逻辑都堆在 `updateState()` 里。

推荐你一开始就分成两个层次:

### 7.1 `updateState()` 只负责“分发”

它只负责:

- 判断消息类型
- 拿到 `byte[]`
- 调用解析方法

它不应该负责:

- 长篇字符串拆分
- 图表数据计算
- UI 汇总卡片计算

### 7.2 单独写一个“原始数据 -> 业务数据”的方法

比如你自己脑子里要建立这个层次:

- 原始输入: `byte[]`
- 第一层结果: 解码后的字符串
- 第二层结果: 解析后的数值对象
- 第三层结果: 图表点 + 文本统计

你哪怕暂时还不真的新建 helper 类，也要先在类内分方法。

原因很简单:

- 以后你要换协议时，只改解析方法
- 以后你要换图表时，只改画图方法
- 以后你要单独测协议时，也更容易看懂

### 7.3 你现在可以参考的两种解析路线

#### 路线 A: 参考 `FragmentCustom`

适合你的蓝牙数据本来就是文本，比如:

- `1.23,456.7,20.1,0.8,5.0`

这种情况下:

- 先把 `byte[]` 转成字符串
- 再按逗号切分
- 再按下标取值

这是 `FragmentCustom.processBluetoothData()` 的思路。

#### 路线 B: 参考 `FragmentMessage`

适合你的蓝牙数据带协议头，比如:

- `EIS:1,2,3`
- `CA:1,2,3`
- `RI:...`

这种情况下:

- 先按换行切分
- 再按前缀判断当前这行是哪种数据
- 再决定是否入图

这是 `FragmentMessage.addListData()` 里的思路。

### 7.4 第一次不要急着支持两种协议

你先选一种。

如果当前硬件真实发过来的就是 `FragmentCustom` 能解析的格式，那就先完全按它那条路走。

学习阶段最容易犯的错是:

- 一边复制页面
- 一边改协议
- 一边改 UI
- 一边想封装

这样最后你根本不知道是哪一层出错。

---

## 8. 第六步图表初始化，先照着 `FragmentCustom` 切两层

你应该把画图逻辑分成下面两类:

### 8.1 一次性初始化

这类方法只在 `initAll()` 期间跑一次:

- `initView()`
- `setupChart()`
- `initDataSet()`

它们的职责是:

- 拿到控件引用
- 配置图表外观
- 创建 `LineDataSet`
- 绑定 `LineData`

### 8.2 每次来数据都要跑

这类方法会在收到新数据时反复跑:

- `addDataPoint(...)`
- `updateCharts()`
- `updateDisplayValue(...)`
- `updateSummaryData()`

它们的职责是:

- 往数据集合里塞点
- 刷新图表
- 更新卡片数值
- 更新最大值最小值统计

你要形成一个稳定意识:

**初始化方法负责“搭舞台”，实时方法负责“演员上台”。**

不要把 `setupChart()` 这种重配置操作放到每次收包里执行。

---

## 9. 第七步第一次复刻时，建议你删掉这些杂乱功能

如果你的目标是尽快学懂并自己做出新页面，我建议你第一版先不要带上下面这些东西:

- `RecyclerView` 原始数据展示
- `BaseFragmentManage` 的子 Fragment 嵌套按钮区
- 折叠区域动画
- 模拟数据逻辑
- 复杂统计文字
- 所有和命令发送相关的按钮

为什么建议删:

- 这些都不是“接收蓝牙数据并画图”的主链
- 它们会制造很多额外状态
- 一旦你同时改它们，排查成本会急剧上升

你第一版最好只保留:

- 顶部设备名
- 一个或两个核心数值展示
- 一个或两个折线图

先把最核心的功能做成一个最小闭环。

---

## 10. 第八步等页面跑通后，再开始“封装杂乱”

这个项目里很多逻辑现在是堆在 Fragment 里的。

你如果一上来就大拆，很容易拆崩。

更稳的节奏是:

### 阶段 1: 页面先跑通

先容忍类里有一些丑代码，只要:

- 数据能进来
- 图能刷新
- 页面不崩

这就算达标。

### 阶段 2: 再从最明显的杂乱开始抽

第一次优先抽这两类:

#### A. 协议解析器

把“`byte[]` / `String` -> 业务数值”的逻辑抽出来。

推荐你脑中把它当成一个单独职责:

- 输入是原始蓝牙数据
- 输出是你页面真正需要的业务字段

这一层最适合后面抽成一个独立类。

#### B. 图表更新器

把“如何往图里加点、如何滚动、如何更新摘要统计”抽出来。

这层适合后面抽成:

- 图表配置 helper
- 数据集管理 helper
- 摘要统计 helper

### 阶段 3: 最后再抽页面状态管理

比如:

- 当前设备是谁
- 是否已经开始接收数据
- 当前显示哪类图
- 当前摘要统计的缓存

这一层不适合第一次就抽，因为你一开始自己都还没完全搞清楚状态边界。

---

## 11. 我建议你的实际落地顺序

下面这个顺序最适合第一次自己做。

### Phase 1: 只做页面注册

- 在 `CommunicationActivity` 里确认你要替换哪个现有页
- 改 `addFragment()` 顺序
- 改 tab 点击映射
- 改 `setPositionListener()` 映射

结果标准:

- 你点到目标 tab 时，能进入新页

### Phase 2: 只做空白新页

- 复制 `fragment_custom.xml`
- 新建新的 Fragment 类
- 先只让页面能正常显示，不接数据

结果标准:

- 切页不崩
- 新布局能正常显示

### Phase 3: 只做订阅和打印

- 订阅 `FRAGMENT_STATE_DATA`
- 在 `updateState()` 里先只识别 `DeviceModule` 和 `Object[]`
- 先只把收到的字符串打日志或显示到一个 `TextView`

结果标准:

- 连接成功后设备名能显示
- 收到数据时页面有肉眼可见变化

### Phase 4: 再做图表

- 先初始化一个图
- 每次来包只更新一个核心数值和一条曲线

结果标准:

- 至少一张图能稳定刷新

### Phase 5: 再恢复次要功能

- 第二张图
- 汇总统计
- 摘要卡片
- 历史列表

结果标准:

- 页面功能逐步完整
- 每多一块功能，你都知道它加在哪里

### Phase 6: 最后再封装

- 抽解析器
- 抽图表 helper
- 抽状态结构

结果标准:

- Fragment 只保留“接消息 + 调方法 + 刷少量 UI”的职责

---

## 12. 你第一次复刻时，最应该抄的不是代码，而是“方法分层”

你真正该学的不是某几行实现，而是 `FragmentCustom` 的分层方式:

- 初始化层
- 消息接收层
- 协议解析层
- 图表刷新层
- 摘要统计层

如果你只是把大段代码复制到一个新类里，那你只是“搬运成功”。

如果你能回答下面 5 个问题，才算真的学懂:

1. 为什么 `initAll()` 只做一次性初始化
2. 为什么 `updateState()` 只应该做消息分流
3. 为什么 `FRAGMENT_STATE_DATA` 要区分两种对象类型
4. 为什么图表配置和图表刷新要分开
5. 为什么要先跑通页面，再去抽 parser 和 chart helper

如果这 5 个问题你能讲清楚，你后面再加第三个、第四个图表页就会很快。

---

## 13. 最常见的报错和排查顺序

### 13.1 页面切不过去

优先检查:

- `activity_communication.xml` 里 tab 是否存在
- `onClickView()` 是否跳到了正确 index
- `initFragment()` 是否真的 `addFragment(new YourFragment())`
- `setPositionListener()` 是否把对应 tab 点亮

### 13.2 页面一打开就崩

优先检查:

- 新 XML 是否生成了正确的 ViewBinding
- `getViewBinding()` 返回的是否是新 binding
- `initView()` 里访问的控件 id 在 XML 里是否都存在

### 13.3 页面能打开，但收不到数据

优先检查:

- 有没有 `subscription(StaticConstants.FRAGMENT_STATE_DATA)`
- `updateState()` 里有没有先判断 `sign`
- `Object[]` 下标有没有取对
- 你的页面是否真的已经被 `addFragment()`

### 13.4 收到数据了，但图不动

优先检查:

- 解析后的数值是不是空或异常
- `addDataPoint()` 有没有真的往 `entries` 塞点
- `LineDataSet` 和 `LineChart` 有没有绑定
- `updateCharts()` 有没有调用

### 13.5 文本能更新，但图一会儿就乱

优先检查:

- 你是不是每次都在重新 `setupChart()`
- 你是不是把初始化逻辑放进了收包逻辑
- 你是不是没有限制点数，导致数据集合无限增长

---

## 14. 我对你这次学习路线的建议

如果你的目标是“学懂并能自己做下一个页面”，那最好的训练方式不是让我直接写完，而是你按下面节奏来:

1. 你先只改通信页注册
2. 你确认 tab 和页面索引彻底理顺
3. 你再复制出一个最小版新 Fragment
4. 你先让页面显示设备名和一个数值
5. 你再把第一张图画出来
6. 你把你改到哪一步、卡在哪个方法，再拿着这份文档来问

这样你每次遇到问题时，问题边界都非常小。

这是最适合真正学会的路径。

---

## 15. 一句话版执行清单

如果你只想看超短版，按这个走:

1. 先不要碰 `FragmentIonAnalysis`
2. 先在 `CommunicationActivity` 里确定新页坑位
3. 复制 `fragment_custom.xml`，先不大删
4. 新建 Fragment，先把 `getViewBinding / initAll / updateState` 搭起来
5. 只订阅 `FRAGMENT_STATE_DATA`
6. 先区分 `DeviceModule` 和 `Object[]{DeviceModule, byte[]}`
7. 先把字符串显示出来，再上图
8. 先让一张图动起来，再补第二张图
9. 页面跑通后，再抽 parser 和 chart helper

---

## 16. 后面你来问我时，最好按这个格式

你后面如果卡住，直接按下面格式问，我会最快接上你的上下文:

```md
我现在做到第 X 步
我改了哪些文件:
- xxx
- xxx

我现在看到的现象:
- xxx

我预期应该是:
- xxx

我怀疑的问题点:
- xxx
```

这样我能直接沿着这份文档接着帮你排，而不是重新读一遍整个项目。
