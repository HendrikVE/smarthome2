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
import java.util.Queue;
import java.util.UUID;

import de.vanappsteer.windowalarmconfig.util.LoggingUtil;

public class BluetoothDeviceConnectionService extends Service {

    private final UUID BLE_SERVICE_UUID = UUID.fromString("2fa1dab8-3eef-40fc-8540-7fc496a10d75");

    private final IBinder mBinder = new LocalBinder();

    private BluetoothGatt mConnectedBluetoothGatt = null;

    private HashMap<UUID, String> mCharacteristicHashMap;
    private Queue<BluetoothGattCharacteristic> mReadCharacteristicsOperationsQueue = new LinkedList<>();

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

        public abstract void onCharacteristicsRead(HashMap<UUID, String> characteristicHashmap);
        public abstract void onDeviceConnectionError(int errorCode);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void connectDevice(BluetoothDevice device) {
        mConnectedBluetoothGatt = device.connectGatt(BluetoothDeviceConnectionService.this, false, mGattCharacteristicReadCallback);
    }

    public void disconnectDevice() {
        if (mConnectedBluetoothGatt != null) {
            mConnectedBluetoothGatt.disconnect();
            mConnectedBluetoothGatt.close();
        }
    }

    /*
    public void readCharacteristics() {

    }
    */

    public void writeCharacteristics() {

    }

    public void addDeviceConnectionErrorListener(DeviceConnectionListener listener) {
        mDeviceConnectionListenerList.add(listener);
    }

    public void removeDeviceConnectionErrorListener(DeviceConnectionListener listener) {
        mDeviceConnectionListenerList.remove(listener);
    }

    private BluetoothGattCallback mGattCharacteristicReadCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            LoggingUtil.debug("onConnectionStateChange");
            LoggingUtil.debug("status: " + status);
            LoggingUtil.debug("newState: " + newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                LoggingUtil.debug("discoverServices");
                gatt.discoverServices();
            }
            else {
                gatt.disconnect();
                gatt.close();

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
                gatt.close();

                for (DeviceConnectionListener listener : mDeviceConnectionListenerList) {
                    listener.onDeviceConnectionError(DeviceConnectionListener.DEVICE_CONNECTION_ERROR_UNSUPPORTED);
                }

                return;
            }

            List<BluetoothGattCharacteristic> characteristicList = gattService.getCharacteristics();
            mCharacteristicHashMap = new HashMap<>();

            mReadCharacteristicsOperationsQueue.addAll(characteristicList);

            // initial call of readCharacteristic, further calls are done within readCharacteristic afterwards
            gatt.readCharacteristic(mReadCharacteristicsOperationsQueue.poll());
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            LoggingUtil.debug("uuid: " + characteristic.getUuid());
            LoggingUtil.debug("value: " + characteristic.getStringValue(0));

            mCharacteristicHashMap.put(characteristic.getUuid(), characteristic.getStringValue(0));

            gatt.readCharacteristic(mReadCharacteristicsOperationsQueue.poll());

            if (mReadCharacteristicsOperationsQueue.size() == 0) {
                for (DeviceConnectionListener listener : mDeviceConnectionListenerList) {
                    listener.onCharacteristicsRead(mCharacteristicHashMap);
                }
            }
        }
    };
}