# 融雪阅读 0.11.5 验证记录

versionCode 22 / com.snowread.app。范围和升级行为见 UPDATE-v0.11.5.md；上一版记录保存在 docs/VALIDATION-v0.11.4.md。

## 本轮通过

- API 35 资源 / Java / D8 / APK 构建，156 个生产 class，minSdk 23 / targetSdk 35。
- ZIP CRC、4 字节对齐、v1/v2/v3 开发签名通过。原始 ZIP 中央目录和本地头文件名没有反斜杠，字体、OFL、ABOUT.json 与源码字节一致。
- APK 3,086,409 字节，SHA256 `097cb7b0dfa1b1d4e3f5db3e592a25faa9fe7ad650b8156e1dcbdd665f70984a`。
- 开发证书 SHA256 `b6db8f4795d1d1465ecf3bfe7501df337ab17037e722732d00ab86ab5e68d26f`。无新权限或依赖，数据格式和签名保持。
- Test-Core：TextDocument 1997、ReaderBehavior 183、ShelfOrganization 1548、SearchResults 168095、ChapterOrder 20005、ListenCore 55994、ReaderPolicy 5791、DirectoryRows 18，全部通过；逐字符断言不等于同数量产品场景。日志见 docs/regressions/0.11.5-core.log。
- LibraryStore、RecycleBin、BackupArchive、BatchImporter、TextDocument 与本轮开始的源码逐字节相同。PagedReaderView 仅缺省 rightSwipeNext 的 true→false，不改分页/手势算法。

## 原生测试准备及运行限制

新增原生用例源码已编译打包：未保存方向时的实际分页视图默认值、设置分组位置、保留显式右滑选择；旧窗口移除后才运行后续菜单、动作早于关闭资源清理、强制销毁取消待执行动作；点击不截断入场、真实遮罩标志启用。保留原有两种方向真实滑动、松手连续性、多选删除恢复及雪尘清理用例。

本轮未执行这些 Android 用例、冷启动、截图或真机观感验收。编译通过不代表新增 UI 用例已运行，也不沿用旧版的原生断言数作为本版结果。没有向已有模拟器或手机安装新包。

尚需在用户 Android 16 / Redmi K80 Ultra 确认：遮罩和面板衔接、连续菜单跳转、入场时快速点击/下拉、长内容与键盘、夜读/横屏/大字号、雪尘亮度及实际帧率。未做前后 gfxinfo 或耗电基准。

## 设备验收路径

1. 新安装/清除方向偏好后，Aa → 阅读方式与动效显示左滑下一页；手指向左实际进入下一页，反向恢复原页。覆盖升级保留已存选择。
2. 改为右滑下一页，关闭后重开，设置与实际手势都记忆；左右手点击和音量键配置保持。
3. 书架更多 → 分类管理、目录 → 输入章号、书签长按 → 管理、搜索 → 结果跳转：旧窗淡出后再进入下一层，操作只执行一次。
4. 入场尚未结束时轻点空白或开始下拉：不冻结半透明，不瞬间跳回起点；取消下拉后完整恢复。关闭中连续点击不重复提交。
5. 验证备份确认的资源生命周期、加载/停止与强制离开；强制销毁后不再补弹窗口。
6. 雪尘轻微增亮，关闭尾迹随 220ms 退出清除。减少动效/系统动画关闭/触摸探索仍直达终态。

环境为 JDK 21.0.12.1 / Build Tools 35.0.0；未运行完整 Gradle、lint、release 或 R8。javac 关闭只读 jar 时仍有已有沙箱 AccessDeniedException 诊断，退出码 0，生产 class、DEX、签名和测试包已生成。
