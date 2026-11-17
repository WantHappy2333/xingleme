# Contributing

欢迎提交 issue 和 pull request。

## 本地构建

1. 安装 JDK 17 和 Android SDK（compileSdk 35）。
2. 在根目录创建 `local.properties`，写入 `sdk.dir=/path/to/android-sdk`。
3. 调试构建：

```bash
./gradlew assembleDebug
```

Release 构建需要本地 `keystore.properties`（参考 `keystore.properties.example`）。
该文件、`.jks` 和 `keystore/` 已被 `.gitignore` 排除，请勿提交。

## 代码约定

- Kotlin + 原生 Android Views，不引入 Compose。
- 保持浅色主题。
- 所有用户可见文案放在 `res/values/strings.xml`。
- 无障碍服务只能订阅触摸相关事件，不要加入窗口、通知事件，否则会重现“通知被当成操作”的 bug。
- 修改息屏/时段逻辑后，请至少手动测试：亮屏、熄屏、跨午夜、系统重建服务。

## 提交前

```bash
./gradlew lintDebug
```

不要在 issue 或 PR 中粘贴签名密码、keystore、设备隐私信息。
