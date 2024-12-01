package com.example.bt_03;

import android.Manifest;
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
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Build;
import androidx.core.content.ContextCompat;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private UUID serviceUUID, characteristicUUID;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private static final int REQUEST_LOCATION_PERMISSION_CODE = 1;
    private ArrayAdapter<String> deviceListAdapter;
    private ArrayList<BluetoothDevice> deviceList = new ArrayList<>();
    private BluetoothDevice selectedDevice;

    private Button btnSearch, btnConnect, btnDisconnect,btnListen,btnSend;
    private ListView deviceListView;
    private EditText editMessage;

    private boolean isScanning = false;
    private Handler handler = new Handler();
    private TextView receivedMessageTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnSearch = findViewById(R.id.btnSearch);
        btnConnect = findViewById(R.id.btnConnect);
        btnSend = findViewById(R.id.btnSend);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        btnListen = findViewById(R.id.btnListen);
        deviceListView = findViewById(R.id.deviceListView);
        editMessage = findViewById(R.id.editMessage);
        receivedMessageTextView = findViewById(R.id.receivedMessage);
        btnDisconnect.setEnabled(true);
        btnListen.setEnabled(true);
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "此设备不支持蓝牙", Toast.LENGTH_SHORT).show();
            finish();
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        deviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        deviceListView.setAdapter(deviceListAdapter);

        btnSearch.setOnClickListener(v -> searchBleDevices());
        deviceListView.setOnItemClickListener((parent, view, position, id) -> {
            selectedDevice = deviceList.get(position);
            btnConnect.setEnabled(true);
        });
        btnConnect.setOnClickListener(v -> connectToDevice());
        btnSend.setOnClickListener(v -> sendMessage());
        btnDisconnect.setOnClickListener(v -> disconnectToDevice());
        btnListen.setOnClickListener(v -> listenMsg());

        btnSend.setEnabled(true);
    }

    private void searchBleDevices() {
        if (!isScanning) {
            deviceList.clear();
            deviceListAdapter.clear();
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, REQUEST_ENABLE_BT);
                return;
            }

            bluetoothLeScanner.startScan(scanCallback);
            isScanning = true;

            handler.postDelayed(() -> {
                bluetoothLeScanner.stopScan(scanCallback);
                isScanning = false;
                Toast.makeText(this, "扫描完成", Toast.LENGTH_SHORT).show();
            }, 10000); // 扫描 10 秒
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();

            // 检查权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // 如果没有权限，则请求权限
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_ENABLE_BT);
                    return;
                }
            }

            // 确保设备名称不为空且不重复添加
            if (device != null && device.getName() != null && !deviceList.contains(device)) {
                deviceList.add(device);
                deviceListAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        }
    };

    private void connectToDevice() {
        // 检查是否选择了设备
        if (selectedDevice == null) {
            Toast.makeText(this, "请选择一个设备", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查是否已获得蓝牙连接权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // 如果没有权限，则请求权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_ENABLE_BT);
            return;
        }

        // 如果权限已获得，则开始连接设备
        try {
            bluetoothGatt = selectedDevice.connectGatt(this, false, gattCallback);
            Toast.makeText(this, "正在连接...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            // 捕获可能的连接异常
            Toast.makeText(this, "连接失败，请重试", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d("BLE", "Device connected");
                // 开始发现服务
                gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.d("BLE", "Device disconnected");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (BluetoothGattService service : gatt.getServices()) {
                    Log.d("BLE", "Service discovered: " + service.getUuid());

                    // 保存服务 UUID
                    serviceUUID = service.getUuid();

                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        Log.d("BLE", "Characteristic discovered: " + characteristic.getUuid());



                        // 这里可以根据特征的属性或 UUID 选择需要的特征
                        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            gatt.setCharacteristicNotification(characteristic, true);

                            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                            if (descriptor != null) {
                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                gatt.writeDescriptor(descriptor);
                                Log.d("BLE", "Notification enabled for characteristic: " + characteristic.getUuid());
                            }
                        }

                        // 检查特征是否支持写操作
                        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) == 1 ||
                            (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0) {
                            // 保存服务 UUID
                            serviceUUID = service.getUuid();
                            // 保存特征 UUID
                            characteristicUUID = characteristic.getUuid();
                            Log.d("BLE", "Write characteristic available: " + characteristic.getUuid());
                            // 这里可以保存写特征的 UUID 或进行其他初始化操作
                        }
                    }
                }
            } else {
                Log.e("BLE", "Service discovery failed, status: " + status);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "Descriptor write successful for " + descriptor.getUuid());
            } else {
                Log.e("BLE", "Descriptor write failed for " + descriptor.getUuid() + " with status " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            final String receivedData = new String(characteristic.getValue());
            Log.d("BLE", "Received data: " + receivedData);

            BluetoothDevice device = gatt.getDevice();
            String deviceName = device != null ? device.getName() : "未知设备";

            runOnUiThread(() -> {
                receivedMessageTextView.setText("来自 " + deviceName + " 的消息: " + receivedData);
            });
        }
    };

    private void disconnectToDevice(){

    }
    private void listenMsg(){
    }
    private void sendMessage() {
        Log.d("BLE", "Characteristic222 Attempting to send message");
        if (bluetoothGatt == null) {
            runOnUiThread(() -> Toast.makeText(this, "蓝牙未连接", Toast.LENGTH_SHORT).show());
            return;
        }

        BluetoothGattService service = bluetoothGatt.getService(serviceUUID);
        if (service == null) {
            runOnUiThread(() -> Toast.makeText(this, "未找到服务", Toast.LENGTH_SHORT).show());
            return;
        }

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
        if (characteristic == null) {
            runOnUiThread(() -> Toast.makeText(this, "未找到特征值", Toast.LENGTH_SHORT).show());
            return;
        }

        // 检查特征是否支持写操作
        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0 &&
            (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0) {
            runOnUiThread(() -> Toast.makeText(this, "特征不支持写操作", Toast.LENGTH_SHORT).show());
            return;
        }

        String message = editMessage.getText().toString() + "\r\n";
        characteristic.setValue(message.getBytes());

        // 打印特征值的 UUID 和属性
        Log.d("BLE", "Characteristic222 UUID: " + characteristic.getUuid());
        Log.d("BLE", "Characteristic222 Properties: " + characteristic.getProperties());

        boolean success = false;

        // 检查权限
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
            return;
        }
        success = bluetoothGatt.writeCharacteristic(characteristic);

        Log.d("BLE", "Characteristic222 Write characteristic success: " + success);

        if (success) {
            // runOnUiThread(() -> Toast.makeText(this, "消息发送成功", Toast.LENGTH_SHORT).show());
        } else {
            // runOnUiThread(() -> Toast.makeText(this, "消息发送失败", Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothGatt != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // 检查是否具有 BLUETOOTH_CONNECT 权限
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    // 有权限，关闭蓝牙连接
                    bluetoothGatt.close();
                } else {
                    // 没有权限，请求权限
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
                }
            } else {
                // 对于较低版本的 Android，不需要额外检查
                bluetoothGatt.close();
            }

            bluetoothGatt = null;
        }
    }
}
