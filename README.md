# 醒了么 · Xingleme


<img src="docs/icon.svg" width="96" alt="醒了么图标">

一个只在浅色模式下工作的熬夜守夜 App。屏幕亮着（或按需息屏）却长时间没有真实触摸屏幕时，用震动、铃声和确认弹窗把你叫醒。

> 只认“手指触屏”。通知到达、通知栏弹出、页面自动切换都不会重置计时。

[**下载最新 APK**](https://github.com/WantHappy2333/xingleme/releases/latest) · [使用说明](使用说明.md) · [更新日志](CHANGELOG.md) · [隐私说明](PRIVACY.md)

## 功能

- **自定义检测节奏**：检查频率 5–300 秒；静止阈值 10–3600 秒，预设 1/2/3/5/10 分钟。
- **真实触屏判定**：只订阅触摸、点击、长按、滚动、输入事件；通知和窗口变化不计数。
- **三种提醒方式**：震动、铃声、悬浮确认弹窗，可独立开关，至少保留一种。
- **可调强度和声音**：震动三档、音量 0–100%、系统铃声选择与试听。
- **息屏也提醒**：熄屏后继续计时，唤醒锁 + 系统闹钟兜底，触发时响铃/震动并补发确认通知。
- **守夜时段**：自定义开始/结束时间，支持跨午夜；时段内自动计时，时段外待机。
- **五套浅色提神配色**：青柠气泡、薄荷清醒、橙子汽水、蓝莓冰沙、樱桃苏打。
- **原生 Android UI**：Material 3 / 原生 View，强制浅色模式。
- **隐私优先**：不申请网络权限，不读取屏幕内容，不上传数据。

## 下载与安装

1. 从 [Releases](https://github.com/WantHappy2333/xingleme/releases/latest) 下载 `醒了么-x.y.z.apk`。
2. 在手机上允许“安装未知来源应用”后安装。
3. 打开 App，按引导开启无障碍服务。
4. 点「开始守夜」，允许通知；建议允许电池优化白名单。

### Android 13+ 无障碍开关自动关闭？

侧载 APK 可能被 Android 的「受限设置」保护拦住：

设置 → 应用 → 醒了么 → 右上角 ⋮ → **允许受限设置**，再回到 设置 → 无障碍 → 已安装的服务 重新开启。

小米 / HyperOS、OPPO、vivo 等机型还需允许「自启动 / 后台运行 / 锁屏清理」。

## 如何测试息屏提醒

1. 打开「息屏也提醒」，至少保留震动或铃声。
2. 静止阈值临时设为 30 秒。
3. 开始守夜，按电源键熄屏，等待阈值。
4. 正常应震动/响铃，通知栏出现可确认提醒；解锁后还能看到悬浮弹窗。

如果仍不提醒，先检查：息屏开关、电池优化白名单、自启动/后台权限、铃声音量、震动开关，以及守夜时段是否覆盖当前时间。

## 构建

要求：JDK 17、Android SDK Platform 35。

```bash
git clone https://github.com/WantHappy2333/xingleme.git
cd xingleme
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleDebug
```

Release 签名：复制 `keystore.properties.example` 为 `keystore.properties` 并填写自己的 keystore。`keystore.properties`、`.jks`、`keystore/` 已被 `.gitignore` 排除。

GitHub Actions 模板在 [docs/github-actions-build.yml](docs/github-actions-build.yml)，复制到 `.github/workflows/build.yml` 即可启用（需要 GitHub token 拥有 `workflow` scope）。

## 项目结构

```
app/src/main/java/com/xingleme/wakeup/
├── MainActivity.kt              # 主界面、设置、权限引导
├── GuardianService.kt           # 前台守夜服务、息屏/时段逻辑
├── WakeAccessibilityService.kt  # 只订阅触屏相关事件
├── AlertController.kt           # 悬浮确认弹窗与提醒生命周期
├── NotificationHelper.kt        # 通知渠道和常驻通知
├── SettingsStore.kt             # 本地设置与阈值迁移
└── ScheduleUtils.kt             # 跨午夜时段计算
```

## 已知限制

- 不同 ROM 的后台策略差异很大，息屏提醒需要电池优化白名单和自启动权限配合；开启后也会增加耗电。
- 息屏提醒依赖前台服务、唤醒锁和系统闹钟；如果系统强行停止 App，守夜会停止。
- 息屏时悬浮弹窗可能被锁屏盖住，因此会额外发一条可确认的通知。
- 无障碍服务被关闭或 App 被强行停止时无法检测触屏。

## 贡献

欢迎 issue 和 PR。提交前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)，并确保无障碍事件订阅仍然只包含触摸相关类型。

## License

[Apache License 2.0](LICENSE)。第三方组件和 Material Design Icons 的归属见 [NOTICE](NOTICE)。
