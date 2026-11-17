# 隐私说明 / Privacy

「醒了么」是一款完全在本机运行的应用。

- 不申请 `INTERNET` 权限，不上传任何数据。
- 无障碍服务只订阅触摸、点击、长按、滚动、输入这几类事件，用于计算“最后一次真实触屏时间”。
- 不读取窗口内容、不读取输入文字、不截图，也不保存屏幕内容。
- 设置、主题、铃声 URI 和运行状态仅保存在本机 SharedPreferences 中。
- 卸载应用会删除这些本地数据。

如果未来版本改变以上行为，会在此文件和更新日志中明确说明。

---

Xingleme runs entirely on your device.

- No `INTERNET` permission and no data upload.
- The accessibility service only subscribes to touch, click, long-click, scroll,
  and text-change events to calculate the last real touch time.
- It does not read window content, input text, or screenshots.
- Settings and runtime state are stored locally in SharedPreferences.
- Uninstalling the app removes local data.
