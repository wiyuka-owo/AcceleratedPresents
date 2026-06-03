# AcceleratedPresents

适用于 Minecraft 1.21.11 NeoForge

---

AcceleratedPresents 是一个面向 Minecraft 客户端的优化模组，解决服务器上大量纯色背景文本展示实体带来的渲染开销。

## 性能测试

在同一场景下对比开启本模组与原版渲染的帧率

| 纯色背景 TextDisplay 数量 | 原版 FPS | 启用 AcceleratedPresents | 提升 |
| --- | --- | --- | --- |
| 6,000 | 125 | 195 | +56% |
| 20,000 | 54 | 102 | +89% |
| 40,000 | 22 | 45 | +105% |


## 安装

把模组放进 `mods` 文件夹即可, 该模组**只需要在客户端安装**

- `blockSolidBackgroundTextDisplays`（默认 `true`）：总开关，关掉即恢复原版渲染。
- `minBackgroundAlpha`（默认 `1`）：背景透明度达到多少（0–255）才算纯色背景，`1` 表示任何可见背景。

## 兼容性

效果完全在客户端，连接任意服务器都可安全使用。

## 如何反馈问题

请使用本页顶部链接的 issue tracker 来反馈 bug、崩溃和其它问题。提交时请附上你正在使用的模组列表，以及相关的崩溃报告或日志文件，这能帮助更快地定位问题。

## 许可证

MIT
