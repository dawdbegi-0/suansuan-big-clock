# 算算大时钟 · Suansuan Big Clock

一个极简的全屏横屏时钟 / 秒表 Android 应用。

- 默认纯黑背景 + 纯白超大数字
- 当前时间 / 秒表一键切换
- **音量键控制**，不占用屏幕空间
- 长按屏幕可修改数字颜色和背景颜色，设置会自动记住
- 前台常亮，不自动息屏
- 沉浸式全屏，隐藏状态栏和导航栏

![icon](docs/icon.png)

## 操作方式

| 操作 | 功能 |
|---|---|
| 音量 + | 在当前时间 / 秒表之间切换 |
| 音量 −（短按） | 秒表开始 / 暂停 / 继续 |
| 音量 −（长按约 0.65 秒） | 秒表清零 |
| 屏幕单击 | 时间模式切到秒表并开始；秒表模式开始 / 暂停 |
| 屏幕长按 | 修改数字颜色 / 背景颜色 |

音量键会被应用拦截，不会改变系统音量。秒表运行中切回时间，计时仍在后台继续。

## 安装

从 [Releases](../../releases) 下载 APK，或直接使用仓库里的：

```text
dist/算算大时钟-v1.2.apk
```

最低支持 Android 5.0（API 21），目标 Android 15（API 35）。

## 构建

本项目不依赖 Gradle，使用 `javac + d8 + aapt2 + apksigner` 构建。

环境需要：

- JDK 21
- Android SDK Platform 35
- `d8`、`aapt2`、`apksigner`、`zip`

Termux 下示例：

```bash
export ANDROID_HOME="$HOME/android-sdk"
export PATH="$PREFIX/bin:/system/bin:$PATH"
sh build.sh
```

产物位于 `build/算算大时钟.apk`。

## 项目结构

```text
AndroidManifest.xml
build.sh
src/com/suansuan/bigclock/MainActivity.java
res/
docs/icon.png
dist/
```

## License

MIT
