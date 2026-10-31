# 融雪阅读 · SnowRead

> 雪渐融，故事正生长。

一款以本地 TXT 小说阅读为主的原生 Android 阅读器。纸色阅读背景搭配冷色界面，支持分页与滚动，尽量让操作简单、正文安静。

当前源码：**v0.16.2（待发布测试版）** · Android 6.0 / API 23 及以上 · 应用标识 `com.snowread.app`

## 开发者与 AI 辅助

由 **[nhwjzs-cyber](https://github.com/nhwjzs-cyber)** 发起和维护；开发过程中使用 **ChatGPT / Codex（OpenAI）**、**GLM（智谱）** 和 **DeepSeek** 辅助代码实现、问题排查、方案与体验审查。

具体分工见 [开发者与 AI 辅助贡献](ACKNOWLEDGEMENTS.md)。这些标注不代表相应厂商官方参与或背书。

## 功能

- 本地 TXT 单本、批量和文件夹导入，书架分类、多分类、置顶与多选管理。
- 章节/卷识别、目录跳转、阅读进度、书签和正文搜索。
- 分页/滚动阅读、翻页动效、夜读、按书排版与自定义字体。
- 常驻页脚（默认开启）：进度、时间、电量；可选章节页眉（默认关闭），主题跟随阅读纸色。
- 选段高亮、下划线、备注，笔记管理与 Markdown 导出。
- 系统离线语音听书、睡眠定时与后台播放。
- 整库备份/恢复、回收站，以及手动开启的 Wi-Fi 局域网传书。
- 手选天气氛围、本地时间光线，以及克制的融雪与新芽动效。

v0.16.2 使用与验证边界见 [UPDATE-v0.16.2.md](UPDATE-v0.16.2.md)。当前公开安装包仍见下方 v0.16.1 下载页。

EPUB 暂未支持。截图和更多真机体验记录待补充。

## 安装与数据

在 [Releases 下载 v0.16.1 测试版](https://github.com/nhwjzs-cyber/SnowRead/releases/tag/v0.16.1)，源码目录不放安装包。下载时选择 `release.apk`；“隔离测试应用”和“隔离仪器测试”仅用于开发验证。

覆盖更新前建议在应用内导出书库备份。同应用标识、同签名的新版可以覆盖旧版；自行编译会使用你自己的签名，通常不能直接覆盖原作者签名的安装包。

## 隐私

- 已移除 IP 定位和外部天气查询；天气由用户选择，时间取自本机。
- 没有接入广告、统计分析或崩溃上报 SDK。书籍、笔记、字体和阅读记录存于应用私有目录。
- 网络权限用于用户手动开启的前台局域网传书。HTTP 未加密，只在可信局域网使用；关闭面板或进入后台停止接收。
- 听书只选择引擎声明为离线的语音；系统语音引擎是独立应用，无法由本项目保证其全部联网行为。
- 主动导出的整库备份包含书籍、笔记与进度，请勿把私人备份上传到公开仓库。

详见 [v0.16.1 隐私检查记录](PRIVACY-REVIEW-v0.16.1.md)。

## 构建

建议用 Android Studio 打开仓库根目录，安装 Android SDK 35，使用 JDK 17 或更高版本。项目使用 Gradle Wrapper，不需要下载第三方阅读框架。

```powershell
# Windows：使用 Android 工具链的默认本地 debug 签名
.\gradlew.bat assembleDebug
```

```sh
# macOS / Linux
sh ./gradlew assembleDebug
```

仓库不包含开发者签名私钥。公开源码中的 Gradle 配置使用标准本地 debug 签名，不引用原作者的私有签名文件。

另提供 Windows 便捷脚本 `Build-WithSdk.ps1`，支持 `-SdkRoot`、`-JdkHome` 和 `-Release`。该脚本需要你自行配置本地 `dev/debug.keystore`，不会随源码分发密钥。`-Release` 关闭调试开关，但不会替你生成或更换签名。

## 验证状态

核心宿主回归、笔记、局域网真实回环 Socket 和隐私字段过滤检查已通过；发布 APK 的签名、资源表压缩/对齐与非调试清单已检查。

Android 隔离仪器测试已编译，当前版本尚未完成真机运行、抓包和视觉验收。请将它视为持续开发的测试版。

```powershell
.\Test-Core.ps1 -JdkHome <JDK目录>
```

其他存储与界面验证见 [VALIDATION.md](VALIDATION.md)。测试只能使用独立沙箱，不对日常书库清除数据。

## 项目结构

- `app/src/main/java/com/snowread/app/`：阅读、书架、存储、听书与原生界面。
- `app/src/main/assets/`：内置字体及虚构示例小说。
- `tests/`：宿主回归和隔离 Android 测试。
- `docs/`：方案、审查记录与历史文档；以当前源码和最新验证记录为准。

## 授权

本项目自有代码采用 [PolyForm Noncommercial License 1.0.0](LICENSE)，允许许可范围内的学习、研究、非商业修改和分享。除正式许可明确允许的情形或另行授权外，不授予将源码或改版用于商业/盈利目的的许可，包括打包出售、付费会员、订阅、付费解锁和广告变现。

本项目属于**源码可用、非商业许可**，不按允许商用的标准开源项目宣传。公益、教育等组织的标准许可例外和完整规则见 [中文使用说明](NONCOMMERCIAL.zh-CN.md) 与 [LICENSE](LICENSE)。分发时保留 [NOTICE](NOTICE) 和许可声明。

内置宋体子集来自 Adobe 思源宋体，继续按 SIL OFL 1.1 分发；保留 [字体来源说明](app/src/main/assets/fonts/ABOUT.json) 和 [OFL 授权全文](app/src/main/assets/fonts/OFL.txt)。Gradle Wrapper 保留其自带的 Apache 2.0 许可。
