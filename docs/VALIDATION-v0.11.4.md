# 融雪阅读 0.11.4 验证记录

versionCode 21 / com.snowread.app。实现与范围见 UPDATE-v0.11.4.md。上一版记录保存于 docs/VALIDATION-v0.11.3.md。

## 已完成

- 资源 / Java / D8 / APK 构建完成，156 个生产 class；minSdk 23 / targetSdk 35。
- 最终 APK ZIP CRC、4 字节对齐、v1/v2/v3 开发签名通过。原始 ZIP 中央目录及本地头文件名没有反斜杠，字体、OFL、ABOUT.json 与源码逐字节一致。
- APK 3,082,313 字节；SHA256 `92fb9a62b13b501c72da8ddfb2f33c3179b9ec15a18e109539f99c5d9be7fa67`。
- 参考输入 APK SHA256 `b08f9b0d2a486753603d1738f9efdd65d4e8700e83f7b74ac47811377d81c707`，与提供的修改说明匹配；badging 为 0.11.3 / code 20 / min 23 / target 35。
- 输入与输出的开发证书均为 SHA256 `b6db8f4795d1d1465ecf3bfe7501df337ab17037e722732d00ab86ab5e68d26f`。本版 code 21，可覆盖该雪尘版及同签名旧版。
- LibraryStore、RecycleBin、BackupArchive、BatchImporter、TextDocument、PagedReaderView 与本轮开始的源码字节一致。无数据格式、新权限、依赖或字体变更。
- 原生测试源码已更新并编译打包：关闭时从“立即清除”调整为“160ms 淡尾迹，面板消失后完全清除”；保留减少动效、布局、触摸和局部反馈生命周期检查。摘要见 docs/regressions/0.11.4-build-summary.log。

## 未完成 / 不作保证

本轮没有执行新增 Android 原生用例、冷启动、真机录屏或界面截图验收；编译成功不等于实际观感通过。没有复用旧版本的原生断言数作为本版结果。上一轮模拟器环境问题未在本轮修复，也未安装到已有模拟器或手机。

核心/存储/分页生产代码未改，因此未重复运行不受影响的套件。JDK 21.0.12.1 / Build Tools 35.0.0；未运行完整 Gradle、lint、release 或 R8。关闭只读 jar 时仍有沙箱 javac AccessDeniedException 诊断，退出码 0，后续 class、DEX、签名及测试包均生成。

## 真机重点

1. 普通菜单、目录、章号跳转、书签、搜索、排版等白名单弹窗：打开时只出现一缕白色微尘，逐渐散开；没有大号蓝色雪花、强光晕或常驻飘雪。
2. 关闭 / 下滑关闭时只留很淡的短尾迹；不延长原 160ms 关闭时间，快速连续开关不残留旧粒子。
3. 顶部颗粒避开拖动横条、品牌文字、标题和关闭按钮；补查长书名、大字号、横屏和键盘弹起时按钮可达。
4. 减少动效、系统动画关闭或触摸探索时不播放；后台、强制关闭、移除/旋转后清理。
5. 夜读不过亮、原纸/暖纸不过脏；阅读正文没有粒子覆盖。粒子只请求约 33ms 一次自身重绘，不宣称整页固定 30fps，也未验证耗电或掉帧。
