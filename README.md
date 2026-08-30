# micro:bit 土壤湿度监测系统

这个仓库包含系统的三部分核心代码：

- `android/`：安卓土壤湿度 App v1.9，显示每秒平滑后的湿度读数和 micro:bit 温度，并控制 P1 舵机。
- `firmware/`：micro:bit MakeCode 固件，当前为 USB 串口调试模式，读取 P0 土壤湿度并接收舵机命令。
- `desktop-debug/`：电脑端 Python 串口监视与舵机控制工具。

## 硬件接线

- 传感器 `S` → 扩展板 `P0` 的 `S`
- 传感器 `V/VCC` → `V/3V`
- 传感器 `G/GND` → `G/GND`
- 舵机信号线 → 扩展板 `P1` 的 `S`
- 舵机电源线 → 与舵机规格匹配的外部电源，电源地与 micro:bit `GND` 共地

## 当前功能

- P0 土壤湿度实时读取
- 手机蓝牙连接 micro:bit
- 湿度每秒平均刷新，减少数字闪跳
- micro:bit 温度显示
- App 按钮控制 P1 舵机转到 0°、90° 或 180°
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

## 电脑 USB 串口调试（当前模式）

1. 按上文编译并把 `firmware/built/binary.hex` 刷入 micro:bit。
2. 使用 USB 数据线连接电脑；在 Windows 设备管理器中查看 micro:bit 的 `COM` 端口号。
3. 在新终端中运行：

```bash
cd desktop-debug
python -m pip install -r requirements.txt
python monitor.py COM5
```

将 `COM5` 替换为实际端口。工具会每秒显示类似 `DATA,moisture=512,temperature=24` 的数据；输入 `0`、`90` 或 `180` 后回车，即可控制 P1 舵机。输入 `quit` 退出。

## 手机蓝牙模式（暂未启用）

1. 给 micro:bit 刷入支持蓝牙服务的固件。
2. 使用官方 micro:bit App 配对板子。
3. 关闭官方 App，打开本仓库的安卓 App。
4. 点击连接，查看平滑后的土壤湿度和温度。

## 问题原因总结

最初手机能够连接 micro:bit，但板子固件里只有系统和刷机服务，没有 Bluetooth IO Pin 服务，因此 App 无法读取 P0。MakeCode 模板默认的 `radio` 与 `bluetooth` 设置也会冲突。固件移除 `radio`、启用 `bluetooth`，并启动 IO Pin 与 Temperature 服务后，手机才能持续读取数据。

