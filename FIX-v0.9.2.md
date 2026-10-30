# 0.9.2 启动闪退修复

2026-10-09；versionCode 13，com.snowread.app，沿用开发签名。

## 用户报告与定位

用户设备：红米 K80 Ultra，Android 16，点击图标立即退出，没有显示书架或报错文字。

对比已交付的 0.9 和 0.9.1 源码归档后，定位到全屏控制接入时新增的初始化顺序错误。`MainActivity.onCreate → showLibrary` 在调用 `frame(false)`、创建窗口内容之前，先调用了 `applyReaderWindow → EdgeToEdge.immersive`。API 30+ 分支直接执行 `Window.getInsetsController()`；系统 PhoneWindow 在该方法内部直接访问 `mDecor.getWindowInsetsController()`，并不会自动创建 mDecor。

因此即使应用写了 `if (controller != null)`，也没有保护作用：异常发生在获取 controller 的方法内部，还未执行到这个判断。0.9 先通过 `colors → EdgeToEdge.apply` 的 `getDecorView()` 建立 DecorView，再调用窗口控制，没有触发此路径。

在现有 Android 17 / API 37 模拟器中实际对照：

- 0.9：安装、冷启动成功，进程存活。
- 原版 0.9.1：覆盖安装后立即崩溃，没有存活的应用进程。
- 堆栈：`NullPointerException → PhoneWindow.getInsetsController → EdgeToEdge.immersive:32 → MainActivity.applyReaderWindow:742 → showLibrary:131 → onCreate:102`。

完整原始崩溃堆栈保留在 docs/regressions/0.9.1-startup-crash.log。这是本地模拟器日志，尚未取得用户手机本身的 logcat；模拟器版本与用户 Android 16 不相同，不能声称已在该手机运行通过。

核对系统源码：[AOSP PhoneWindow](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-15.0.0_r5/core/java/com/android/internal/policy/PhoneWindow.java)。实际模拟器堆栈与系统这条调用路径相符。

## 修正

1. `showLibrary` 先调用 `frame(false)` 创建窗口内容，再应用亮度、方向和全屏策略。
2. `EdgeToEdge.immersive` 自身先调用 `window.getDecorView()`，确保视图已创建，再从 decor 获取控制器。未附着时控制器为空便跳过，后续窗口获得焦点时再应用。兼容旧系统的分支也复用已取得的 decor。
3. 版本递增至 0.9.2 / 13，供已装 0.9.1 的设备直接覆盖升级。

没有清空书库或偏好，没有数据迁移，没有移除 0.9.1 的功能，也没有新权限。

## 验证

- 同一个专用模拟器覆盖安装 0.9.2，连续三次 `am start -S -W` 冷启动成功；逐次检查应用 PID、MainActivity 的 resumed 状态和崩溃缓冲区。不能仅凭 am 的 Status: ok 判定成功，因为原版崩溃时也可能返回 ok。
- 实际读取书架 UI hierarchy，确认有品牌、测试分类、测试书籍和继续阅读卡片。
- 测试书库使用旧版可读的 v3 元数据，包含一本正文、自定义封面、分类、置顶、offset=37、两个书签和备注。覆盖后完整索引逐字段相等；正文与封面 SHA256 均相等。
- 从书架打开测试书，读取到真实正文并检查截图；返回书架、进入后台再回前台，进程存活且无新增崩溃。
- 现有核心和存储回归全部通过，具体结果见 VALIDATION.md。

新增 `Test-AndroidStartup.ps1`：只接受显式指定的专用 emulator 序列号，覆盖安装并重复冷启动，检查进程/前台 Activity/崩溃日志，保存每次结果；不卸载、不清空应用数据。例：

```powershell
.\Test-AndroidStartup.ps1 -Adb '<SDK>\platform-tools\adb.exe' -Serial emulator-5556 -Apk '.\RongxueReader-debug.apk' -TestOutput '<工作目录>\startup-check'
```

仍需在用户的 Android 16 / 红米系统覆盖安装后确认启动。此前文档列出的听书锁屏、完整无障碍、各版本手机及帧率等未验收项保持，不因本次启动冒烟测试而标为通过。
