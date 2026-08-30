package com.example.autowater;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int REQUEST_BLUETOOTH = 10;
    private static final UUID UART_SERVICE = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID UART_CHARACTERISTIC_2 = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID UART_CHARACTERISTIC_3 = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID IO_SERVICE = UUID.fromString("E95D127B-251D-470A-A062-FA1922DFA9A8");
    private static final UUID PIN_DATA = UUID.fromString("E95D8D00-251D-470A-A062-FA1922DFA9A8");
    private static final UUID PIN_AD_CONFIG = UUID.fromString("E95D5899-251D-470A-A062-FA1922DFA9A8");
    private static final UUID PIN_IO_CONFIG = UUID.fromString("E95DB9FE-251D-470A-A062-FA1922DFA9A8");
    private static final UUID TEMPERATURE_SERVICE = UUID.fromString("E95D6100-251D-470A-A062-FA1922DFA9A8");
    private static final UUID TEMPERATURE_DATA = UUID.fromString("E95D9250-251D-470A-A062-FA1922DFA9A8");
    private TextView status, reading, temperature, state, servoState;
    private Button servo0, servo90, servo180;
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic ioConfig;
    private BluetoothGattCharacteristic temperatureCharacteristic;
    private BluetoothGattCharacteristic uartRx;
    private int requestedServoAngle;
    private boolean connecting;
    private boolean connected;
    private boolean destroyed;
    private BluetoothDevice activeDevice;
    private boolean servicesRetried;
    private int retryCount;
    private static final long SCAN_TIMEOUT_MS = 30000;
    private long lastRenderAt;
    private int sampleSum;
    private int sampleCount;
    private final Handler handler = new Handler();

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        adapter = manager.getAdapter();
    }

    private void buildScreen() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(48, 72, 48, 48);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setBackgroundColor(Color.rgb(246, 251, 246));

        TextView title = text("土壤湿度监测 v1.9", 30, Color.rgb(35, 90, 40));
        page.addView(title);
        status = text("等待连接 micro:bit", 17, Color.DKGRAY);
        status.setPadding(0, 40, 0, 24);
        page.addView(status);
        reading = text("--", 72, Color.rgb(35, 90, 40));
        page.addView(reading);
        temperature = text("温度 -- °C", 24, Color.rgb(45, 85, 130));
        temperature.setPadding(0, 8, 0, 8);
        page.addView(temperature);
        state = text("打开蓝牙后，点击下方按钮连接", 18, Color.DKGRAY);
        state.setPadding(0, 16, 0, 44);
        page.addView(state);
        Button connect = new Button(this);
        connect.setText("连接 micro:bit");
        connect.setTextSize(18);
        connect.setOnClickListener(v -> begin());
        page.addView(connect);

        servoState = text("舵机：连接后可控制 P1", 17, Color.rgb(130, 75, 25));
        servoState.setPadding(0, 36, 0, 12);
        page.addView(servoState);

        LinearLayout servoControls = new LinearLayout(this);
        servoControls.setOrientation(LinearLayout.HORIZONTAL);
        servoControls.setGravity(Gravity.CENTER);
        servo0 = servoButton("0°", 0);
        servo90 = servoButton("90°", 90);
        servo180 = servoButton("180°", 180);
        LinearLayout.LayoutParams servoButtonParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        servoControls.addView(servo0, servoButtonParams);
        servoControls.addView(servo90, servoButtonParams);
        servoControls.addView(servo180, servoButtonParams);
        page.addView(servoControls, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setServoControlsEnabled(false);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(page);
        setContentView(scroll);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private Button servoButton(String label, int angle) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(17);
        button.setOnClickListener(v -> sendServoAngle(angle));
        return button;
    }

    private void setServoControlsEnabled(boolean enabled) {
        servo0.setEnabled(enabled);
        servo90.setEnabled(enabled);
        servo180.setEnabled(enabled);
    }

    private void sendServoAngle(int angle) {
        if (gatt == null || uartRx == null) {
            servoState.setText("舵机不可用：请连接并更新 micro:bit 固件");
            return;
        }
        requestedServoAngle = angle;
        setServoControlsEnabled(false);
        writeServoAngle(angle, 4);
    }

    private void writeServoAngle(int angle, int retriesRemaining) {
        BluetoothGatt connection = gatt;
        BluetoothGattCharacteristic characteristic = uartRx;
        if (connection == null || characteristic == null) {
            servoState.setText("舵机连接已断开");
            setServoControlsEnabled(false);
            return;
        }

        int properties = characteristic.getProperties();
        int writeType = (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
        characteristic.setWriteType(writeType);
        characteristic.setValue(("SERVO:" + angle + "\n").getBytes(StandardCharsets.UTF_8));

        if (connection.writeCharacteristic(characteristic)) {
            servoState.setText("舵机命令已发送：P1 → " + angle + "°");
            handler.postDelayed(() -> setServoControlsEnabled(gatt != null && uartRx != null), 500);
        } else if (retriesRemaining > 0) {
            servoState.setText("蓝牙繁忙，正在自动重试…");
            handler.postDelayed(() -> writeServoAngle(angle, retriesRemaining - 1), 250);
        } else {
            servoState.setText("舵机命令发送失败，请重新连接");
            setServoControlsEnabled(true);
        }
    }

    private BluetoothGattCharacteristic findWritableCharacteristic(BluetoothGattService service) {
        if (service == null) return null;
        BluetoothGattCharacteristic characteristic2 = service.getCharacteristic(UART_CHARACTERISTIC_2);
        BluetoothGattCharacteristic characteristic3 = service.getCharacteristic(UART_CHARACTERISTIC_3);
        if (isWritable(characteristic2)) return characteristic2;
        if (isWritable(characteristic3)) return characteristic3;
        return null;
    }

    private boolean isWritable(BluetoothGattCharacteristic characteristic) {
        if (characteristic == null) return false;
        int properties = characteristic.getProperties();
        return (properties & (BluetoothGattCharacteristic.PROPERTY_WRITE
                | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0;
    }

    private boolean allowed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void begin() {
        if (!allowed()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH);
            else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_BLUETOOTH);
            return;
        }
        if (adapter == null || !adapter.isEnabled()) { status.setText("请先打开手机蓝牙"); return; }
        if (scanner != null || connecting || connected) return;
        status.setText("正在寻找 micro:bit…");
        scanner = adapter.getBluetoothLeScanner();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        scanner.startScan(null, settings, scanCallback);
        handler.postDelayed(() -> {
            BluetoothLeScanner activeScanner = scanner;
            if (activeScanner == null || connecting) return;
            activeScanner.stopScan(scanCallback);
            scanner = null;
            BluetoothDevice paired = pairedMicrobit();
            if (paired != null) {
                status.setText("未收到广播，正在连接已配对的 micro:bit…");
                connectTo(paired);
            } else {
                status.setText("没有找到 micro:bit，请确认它已开启蓝牙程序");
            }
        }, SCAN_TIMEOUT_MS);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == REQUEST_BLUETOOTH && allowed()) begin();
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String name = device.getName();
            BluetoothDevice paired = pairedMicrobit();
            boolean knownPairedMicrobit = paired != null && paired.getAddress().equals(device.getAddress());
            if (knownPairedMicrobit || (name != null && name.toLowerCase().contains("micro"))) {
                scanner.stopScan(this);
                scanner = null;
                connectTo(device);
            }
        }
    };

    private BluetoothDevice pairedMicrobit() {
        Set<BluetoothDevice> devices = adapter.getBondedDevices();
        for (BluetoothDevice device : devices) {
            String name = device.getName();
            if (name != null && name.toLowerCase().contains("micro")) return device;
        }
        return null;
    }

    private void connectTo(BluetoothDevice device) {
        if (connecting || connected || destroyed) return;
        connecting = true;
        activeDevice = device;
        servicesRetried = false;
        String name = device.getName();
        status.setText("正在连接 " + (name == null ? "micro:bit" : name));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            gatt = device.connectGatt(MainActivity.this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } else {
            gatt = device.connectGatt(MainActivity.this, false, gattCallback);
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt connection, int statusCode, int newState) {
            if (connection != gatt) {
                connection.close();
                return;
            }
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                connecting = false;
                connected = true;
                retryCount = 0;
                connection.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                runOnUiThread(() -> status.setText("已连接，正在读取湿度…"));
                connection.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                connecting = false;
                connected = false;
                uartRx = null;
                gatt = null;
                connection.close();
                runOnUiThread(() -> {
                    status.setText("连接中断（状态 " + statusCode + "），正在自动重连…");
                    servoState.setText("舵机：连接后可控制 P1");
                    setServoControlsEnabled(false);
                });
                retryLater();
            }
        }
        @Override public void onServicesDiscovered(BluetoothGatt connection, int statusCode) {
            if (connection != gatt || statusCode != BluetoothGatt.GATT_SUCCESS) return;
            temperatureCharacteristic = connection.getService(TEMPERATURE_SERVICE) == null ? null
                    : connection.getService(TEMPERATURE_SERVICE).getCharacteristic(TEMPERATURE_DATA);
            uartRx = findWritableCharacteristic(connection.getService(UART_SERVICE));
            if (connection.getService(IO_SERVICE) != null) {
                BluetoothGattCharacteristic analogConfig = connection.getService(IO_SERVICE).getCharacteristic(PIN_AD_CONFIG);
                ioConfig = connection.getService(IO_SERVICE).getCharacteristic(PIN_IO_CONFIG);
                if (analogConfig != null && ioConfig != null) {
                    analogConfig.setValue(new byte[]{0x01}); // P0 uses analogue input.
                    connection.writeCharacteristic(analogConfig);
                    return;
                }
            }
            if (uartRx != null) {
                runOnUiThread(() -> status.setText("已连接，舵机可用；湿度服务不可用"));
                updateServoAvailability();
                return;
            }
            if (servicesRetried) {
                runOnUiThread(() -> status.setText("micro:bit 没有可用的蓝牙服务"));
                return;
            }
            servicesRetried = true;
            handler.postDelayed(() -> {
                if (connection.getService(IO_SERVICE) != null || connection.getService(UART_SERVICE) != null) {
                    onServicesDiscovered(connection, BluetoothGatt.GATT_SUCCESS);
                } else {
                    runOnUiThread(() -> status.setText("micro:bit 没有可用的蓝牙服务"));
                }
            }, 3000);
        }
        @Override public void onCharacteristicWrite(BluetoothGatt connection, BluetoothGattCharacteristic characteristic, int statusCode) {
            if (PIN_AD_CONFIG.equals(characteristic.getUuid()) && ioConfig != null) {
                ioConfig.setValue(new byte[]{0x01}); // P0 is an input pin.
                connection.writeCharacteristic(ioConfig);
            } else if (PIN_IO_CONFIG.equals(characteristic.getUuid())) {
                BluetoothGattCharacteristic pinData = connection.getService(IO_SERVICE).getCharacteristic(PIN_DATA);
                if (pinData != null) enableNotification(connection, pinData);
            } else if (uartRx != null && uartRx.getUuid().equals(characteristic.getUuid())) {
                runOnUiThread(() -> {
                    servoState.setText(statusCode == BluetoothGatt.GATT_SUCCESS
                            ? "舵机命令已发送：P1 → " + requestedServoAngle + "°"
                            : "舵机命令发送失败，请重试");
                    setServoControlsEnabled(true);
                });
            }
        }
        @Override public void onCharacteristicChanged(BluetoothGatt connection, BluetoothGattCharacteristic characteristic) {
            if (TEMPERATURE_DATA.equals(characteristic.getUuid())) {
                byte[] bytes = characteristic.getValue();
                if (bytes != null && bytes.length > 0) {
                    int degrees = bytes[0];
                    runOnUiThread(() -> temperature.setText("温度 " + degrees + " °C"));
                }
                return;
            }
            if (PIN_DATA.equals(characteristic.getUuid())) {
                byte[] bytes = characteristic.getValue();
                if (bytes != null && bytes.length >= 2) {
                    int raw = (bytes[1] & 0xff) * 4; // The IO service sends an 8-bit version of the analogue value.
                    runOnUiThread(() -> update(String.valueOf(raw)));
                }
                return;
            }
            String value = new String(characteristic.getValue(), StandardCharsets.UTF_8).trim();
            runOnUiThread(() -> update(value));
        }

        @Override public void onDescriptorWrite(BluetoothGatt connection, BluetoothGattDescriptor descriptor, int statusCode) {
            if (PIN_DATA.equals(descriptor.getCharacteristic().getUuid()) && temperatureCharacteristic != null) {
                enableNotification(connection, temperatureCharacteristic);
            } else {
                updateServoAvailability();
            }
        }
    };

    private void enableNotification(BluetoothGatt connection, BluetoothGattCharacteristic characteristic) {
        connection.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            connection.writeDescriptor(descriptor);
        }
        runOnUiThread(() -> status.setText("已连接，正在读取土壤湿度…"));
    }

    private void updateServoAvailability() {
        runOnUiThread(() -> {
            boolean available = uartRx != null;
            setServoControlsEnabled(available);
            servoState.setText(available ? "舵机已就绪：选择 P1 角度" : "当前固件不支持舵机，请更新 micro:bit 固件");
        });
    }

    private void update(String value) {
        try {
            int raw = Integer.parseInt(value.replaceAll("[^0-9]", ""));
            sampleSum += raw;
            sampleCount++;
            long now = SystemClock.elapsedRealtime();
            if (lastRenderAt != 0 && now - lastRenderAt < 1000) return;

            int smoothRaw = Math.round((float) sampleSum / sampleCount);
            sampleSum = 0;
            sampleCount = 0;
            lastRenderAt = now;

            reading.setText(String.valueOf(smoothRaw));
            status.setText("实时土壤湿度读数（每秒平滑更新）");
            state.setText(smoothRaw >= 153 ? "偏干：需要留意" : "湿度较高");
            state.setTextColor(smoothRaw >= 153 ? Color.rgb(190, 80, 30) : Color.rgb(35, 120, 60));
        } catch (NumberFormatException e) { status.setText("收到的数据：" + value); }
    }

    private void retryLater() {
        if (destroyed || retryCount >= 5) {
            runOnUiThread(() -> status.setText("自动重连失败，请确认 micro:bit 仍在供电"));
            return;
        }
        retryCount++;
        long delay = Math.min(10000, retryCount * 2000L);
        handler.postDelayed(() -> {
            if (destroyed || connected || connecting) return;
            if (activeDevice != null) connectTo(activeDevice);
            else if (scanner == null) begin();
        }, delay);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        if (scanner != null) {
            scanner.stopScan(scanCallback);
            scanner = null;
        }
        BluetoothGatt connection = gatt;
        gatt = null;
        if (connection != null) {
            connection.disconnect();
            connection.close();
        }
        super.onDestroy();
    }
}
