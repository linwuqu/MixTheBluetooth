# Workflow 目录

这里只放工作流目录，不放长篇解释。

## 1. 已有工作流

| 顺序 | 工作流 | 文件 | 状态 |
|---|---|---|---|
| 00 | 父子编排器 | [00-father-son orchestrator.md](<./00-father-son orchestrator.md>) | 已落地 |
| 01 | 注册 / 登录 | [01-register-login.md](./01-register-login.md) | 初稿 |
| 02 | 设备连接 | [02-device-connection.md](./02-device-connection.md) | 初稿 |
| 03 | 拉取缓存明文流 | [03-read-cache-text-stream.md](./03-read-cache-text-stream.md) | 初稿 |
| 04 | 解析 / 校验 / 重试决策 | [04-parse-validate-cache-text.md](./04-parse-validate-cache-text.md) | 初稿 |
| 06 | 端口挂载：能力与业务适配器分离（架构） | [06-port-capability-separation.md](./06-port-capability-separation.md) | 已落地 |
| 07 | 指令直达，分层上报：Effect/Command 映射收敛（架构） | [07-direct-command-hierarchical-report.md](./07-direct-command-hierarchical-report.md) | 已落地 |
| 08 | UI 分层：Route 业务转移 / Screen 纯呈现 + 组件拆分与 Preview（架构） | [08-ui-route-screen-split.md](./08-ui-route-screen-split.md) | 已落地 |
| 09 | 流的分类与建模：三类流如何回答抗压与低能耗（架构） | [09-flow-landscape-and-combine.md](./09-flow-landscape-and-combine.md) | 梳理稿 |
| 10 | 刷新链路的两点改进：反馈可见、重启无成本（架构） | [10-refresh-chain-improvements.md](./10-refresh-chain-improvements.md) | 已落地 |

## 2. 待整理工作流

| 顺序 | 工作流 | 说明 |
|---|---|---|
| 05 | 生成文件 | 形成本地缓存文件 |
| 06 | 推送文件 | 上传远端任务 |
| 07 | 拉取任务 | 轮询远端生成结果 |
| 08 | 前端展示 | 结果回传、状态展示、删除确认 |

## 3. 沉淀顺序

1. 先写单个工作流初稿。
2. 讨论后修正 `Intent / Callback`、`State / Event / Effect`、`Command / Result`。
3. 稳定后回填到总蓝图。
