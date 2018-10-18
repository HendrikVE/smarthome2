package de.vanappsteer.windowalarmconfig.services;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import de.vanappsteer.windowalarmconfig.util.LoggingUtil;

public class BluetoothDeviceConnectionService extends Service {

    private final UUID BLE_SERVICE_UUID = UUID.fromString("2fa1dab8-3eef-40fc-8540-7fc496a10d75");

    private final IBinder mBinder = new LocalBinder();

    private BluetoothGatt mConnectedBluetoothGatt = null;
    private boolean mDisconnectPending = false;

    private HashMap<UUID, String> mCharacteristicHashMap;
    private final Queue<BluetoothGattCharacteristic> mReadCharacteristicsOperationsQueue = new LinkedList<>();
    private final Queue<BluetoothGattCharacteristic> mWriteCharacteristicsOperationsQueue = new LinkedList<>();

    private ArrayList<DeviceConnectionListener> mDeviceConnectionListenerList = new ArrayList<>();

    public BluetoothDeviceConnectionService() {

    }

    public class LocalBinder extends Binder {

        public BluetoothDeviceConnectionService getService() {
            return BluetoothDeviceConnectionService.this;
        }
    }

    public static abstract class DeviceConnectionListener {

        public static final int DEVICE_CONNECTION_ERROR_GENERIC = 1;
        public static final int DEVICE_CONNECTION_ERROR_UNSUPPORTED = 2;

        public abstract void onCharacteristicsRead(Map<UUID, String> characteristicMap);
        public abstract void onDeviceConnectionError(int errorCode);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void connectDevice(BluetoothDevice device) {

        LoggingUtil.debug("connectDevice()");

        mConnectedBluetoothGatt = device.connectGatt(BluetoothDeviceConnectionService.this, false, mGattCharacteristicCallback);
    }

    public void disconnectDevice() {

        if (mConnectedBluetoothGatt != null) {

            synchronized (mReadCharacteristicsOperationsQueue) {
                synchronized (mWriteCharacteristicsOperationsQueue) {
                    int operationsPending = mReadCharacteristicsOperationsQueue.size() + mWriteCharacteristicsOperationsQueue.size();

                    if (operationsPending == 0) {
                        mConnectedBluetoothGatt.disconnect();
                        mDisconnectPending = false;
                    }
                    else {
                        mDisconnectPending = true;
                    }
                }
            }
        }
    }

    /*
    public void readCharacteristics() {

    }
    */

    public void writeCharacteristics(Map<UUID, String> characteristicMap) {

        LoggingUtil.debug("writeCharacteristics()");

        synchronized (mWriteCharacteristicsOperationsQueue) {

            boolean needInitialCall = mWriteCharacteristicsOperationsQueue.size() == 0;

            BluetoothGattService gattService = mConnectedBluetoothGatt.getService(BLE_SERVICE_UUID);

            for (Map.Entry<UUID, String> entry : characteristicMap.entrySet()) {

                BluetoothGattCharacteristic characteristic = gattService.getCharacteristic(entry.getKey());
                characteristic.setValue(entry.getValue());

                LoggingUtil.debug("entry.getValue() = " + entry.getValue());
                LoggingUtil.debug("characteristic.getStringValue(0) = " +  characteristic.getStringValue(0));

                mWriteCharacteristicsOperationsQueue.add(characteristic);
            }

            if (needInitialCall) {
                // initial call of writeCharacteristic, further calls are done within onCharacteristicWrite afterwards
                boolean success = mConnectedBluetoothGatt.writeCharacteristic(mWriteCharacteristicsOperationsQueue.poll());

                LoggingUtil.debug("initial write. success = " + success);
            }
        }
    }

    public void addDeviceConnectionListener(DeviceConnectionListener listener) {
        mDeviceConnectionListenerList.add(listener);
    }

    public void removeDeviceConnectionListener(DeviceConnectionListener listener) {
        mDeviceConnectionListenerList.remove(listener);
    }

    private BluetoothGattCallback mGattCharacteristicCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            LoggingUtil.debug("onConnectionStateChange");
            LoggingUtil.debug("status: " + status);
            LoggingUtil.debug("newState: " + newState);

            if (status == BluetoothGatt.GATT_SUCCESS) {

                switch (newState) {

                    case BluetoothProfile.STATE_CONNECTED:
                        LoggingUtil.debug("discoverServices");
                        gatt.discoverServices();
                        break;

                    case BluetoothProfile.STATE_DISCONNECTED:
                        gatt.close();
                        break;

                    default:
                        LoggingUtil.debug("unhandled state: " + newState);
                }
            }
            else {
                for (DeviceConnectionListener listener : mDeviceConnectionListenerList) {
                    listener.onDeviceConnectionError(DeviceConnectionListener.DEVICE_CONNECTION_ERROR_GENERIC);
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            LoggingUtil.debug("onServicesDiscovered");

            if (status != BluetoothGatt.GATT_SUCCESS) {
                LoggingUtil.error("status != BluetoothGatt.GATT_SUCCESS");

                for (DeviceConnectionListener listener : mDeviceConnectionListenerList) {
                    listener.onDeviceConnectionError(DeviceConnectionListener.DEVICE_CONNECTION_ERROR_GENERIC);
                }

                return;
            }

            BluetoothGattService gattService = gatt.getService(BLE_SERVICE_UUID);
            if (gattService == null) {

                gatt.disconnect();

                for (DeviceConnectionListener listener : mDeviceConnectionListenerList) {
                    listener.onDeviceConnectionError(DeviceConnectionListener.DEVICE_CONNECTION_ERROR_UNSUPPORTED);
                }

                return;
            }

            List<BluetoothGattCharacteristic> characteristicList = gattService.getCharacteristics();
            mCharacteristicHashMap = new HashMap<>();

            mReadCharacteristicsOperationsQueue.addAll(characteristicList);

            // initial call of readCharacteristic, further calls are done within onCharacteristicRead afterwards
            gatt.readCharacteristic(mReadCharacteristicsOperationsQueue.poll());
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            LoggingUtil.debug("onCharacteristicRead");
            LoggingUtil.debug("uuid: " + characteristic.getUuid());
            LoggingUtil.debug("value: " + characteristic.getStringValue(0));

            mCharacteristicHashMap.put(characteristic.getUuid(), characteristic.getStringValue(0));

            synchronized (mReadCharacteristicsOperationsQueue) {

                if (mReadCharacteristicsOperationsQueue.size() > 0) {
                    gatt.readCharacteristic(mReadCharacteristicsOperationsQueue.poll());
                }
                else {
                    for (DeviceConnectionListener listener : mDeviceConnectionListenerList) {
                        listener.onCharacteristicsRead(mCharacteristicHashMap);
                    }
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            LoggingUtil.debug("onCharacteristicWrite");
            LoggingUtil.debug("uuid: " + characteristic.getUuid());
            LoggingUtil.debug("value: " + characteristic.getStringValue(0));

            synchronized (mWriteCharacteristicsOperationsQueue) {

                if (mWriteCharacteristicsOperationsQueue.size() > 0) {
                    gatt.writeCharacteristic(mWriteCharacteristicsOperationsQueue.poll());
                }
                else if (mDisconnectPending) {
                    disconnectDevice();
                }
            }
        }
    };
}
