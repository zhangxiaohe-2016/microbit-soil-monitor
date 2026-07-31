# micro:bit 土壤湿度监测系统

这个仓库包含系统的两部分核心代码：

- `android/`：安卓土壤湿度 App v1.5，显示每秒平滑后的湿度读数和 micro:bit 温度。
- `firmware/`：micro:bit MakeCode 固件，启用 Bluetooth IO Pin 与 Temperature 服务，读取 P0 土壤湿度。

## 硬件接线

- 传感器 `S` → 扩展板 `P0` 的 `S`
- 传感器 `V/VCC` → `V/3V`
- 传感器 `G/GND` → `G/GND`

## 当前功能

- P0 土壤湿度实时读取
- 手机蓝牙连接 micro:bit
- 湿度每秒平均刷新，减少数字闪跳
- micro:bit 温度显示
- 连接失败时延长扫描并自动重试

## 编译 micro:bit 固件

需要 Node.js，以及 Microsoft MakeCode 命令行工具：

```bash
npm install makecode
cd firmware
../node_modules/.bin/mkc build
```

固件生成在 `firmware/built/binary.hex`。把它复制到电脑中的 `MICROBIT` U 盘即可刷入板子。每次重新刷入蓝牙固件后，需要在官方 micro:bit App 中重新配对。

## 编译安卓 App

需要 Android SDK 35、JDK 和 Gradle。先在 `android/local.properties` 中配置 SDK 路径：

```properties
sdk.dir=/你的/Android/SDK/路径
```

然后运行：

```bash
cd android
gradle :app:assembleDebug
```

调试 APK 生成在 `android/app/build/outputs/apk/debug/app-debug.apk`。

## 运行顺序

1. 给 micro:bit 刷入 `firmware` 生成的固件。
2. 使用官方 micro:bit App 配对板子。
3. 关闭官方 App，打开本仓库的安卓 App。
4. 点击连接，查看平滑后的土壤湿度和温度。

## 问题原因总结

最初手机能够连接 micro:bit，但板子固件里只有系统和刷机服务，没有 Bluetooth IO Pin 服务，因此 App 无法读取 P0。MakeCode 模板默认的 `radio` 与 `bluetooth` 设置也会冲突。固件移除 `radio`、启用 `bluetooth`，并启动 IO Pin 与 Temperature 服务后，手机才能持续读取数据。

