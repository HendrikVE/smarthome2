#include <stdbool.h>
#include <string>

#include "Arduino.h"
#include "ArduinoLog.h"
#include "WiFiClientSecure.h"
#include "MQTT.h"
#include "BLEDevice.h"
#include "BLEUtils.h"
#include "BLEServer.h"

#include "../storage/FlashStorage.h"

#include "ConnectivityManager.h"

ConnectivityManager::ConnectivityManager() {
    mWifiMutex = xSemaphoreCreateMutex();
    mMqttMutex = xSemaphoreCreateMutex();
}

void ConnectivityManager::begin() {
    logger.begin(LOG_LEVEL_VERBOSE, &Serial);
    logger.setPrefix(printTag);
    logger.setSuffix(printNewline);
}

bool ConnectivityManager::checkWifiConnection() {

    if (xSemaphoreTake(mWifiMutex, (TickType_t) 10 ) == pdTRUE) {

        if (WiFi.status() != WL_CONNECTED) {
            logger.notice("Connect to WiFi...");

            int attempts = 0;
            while (WiFi.status() != WL_CONNECTED) {

                attempts++;
                if (attempts >= 60) {
                    return false;
                }

                delay(500);
            }
            logger.notice("Connected");
        }

        xSemaphoreGive(mWifiMutex);
    }

    return true;
}

bool ConnectivityManager::initWifi(const char* ssid, const char* password) {
    //WiFi.onEvent(wifiEvent);
    WiFi.begin(ssid, password);

    return checkWifiConnection();
}

void ConnectivityManager::turnOnWifi() {
    WiFi.mode(WIFI_STA);
}

void ConnectivityManager::turnOffWifi() {
    WiFi.disconnect();
    WiFi.mode(WIFI_OFF);
}

bool ConnectivityManager::checkMqttConnection() {

    if (xSemaphoreTake(mMqttMutex, (TickType_t) 10 ) == pdTRUE) {

        if (!mMqttClient.connected()) {
            logger.notice("Connect to MQTT broker...");

            int attempts = 0;
            while (!mMqttClient.connect(this->mMqttClientID, this->mMqttUser, this->mMqttPassword)) {
                //connect has timeout set by mMqttClient.setOptions()

                attempts++;
                if (attempts >= 60) {
                    return false;
                }
            }
            logger.notice("Connected");
        }

        xSemaphoreGive(mMqttMutex);
    }

    return true;
}

bool ConnectivityManager::initMqtt(const char* address, int port, const char* user, const char* password, const char* clientID) {

    mWifiClientSecure.setCACert((char*) ca_crt_start);
    mWifiClientSecure.setCertificate((char*) client_crt_start);
    mWifiClientSecure.setPrivateKey((char*) client_key_start);

    this->mMqttUser = user;
    this->mMqttPassword = password;
    this->mMqttClientID = clientID;

    mMqttClient.setOptions(10, true, 500);
    mMqttClient.begin(address, port, mWifiClientSecure);

    return checkMqttConnection();
}

MQTTClient* ConnectivityManager::getMqttClient() {
    return &mMqttClient;
}

void createCharacteristics(BLEService* bleService, BLECharacteristicCallbacks* callbacks, std::map <std::string, std::string>& characteristics) {

    BLECharacteristic* characteristic;

    for (std::map <std::string, std::string>::iterator it=characteristics.begin(); it!=characteristics.end(); ++it) {

        characteristic = bleService->createCharacteristic(
                it->first,
                BLECharacteristic::PROPERTY_READ |
                BLECharacteristic::PROPERTY_WRITE
        );

        characteristic->setCallbacks(callbacks);
        characteristic->setValue(it->second);
    }
}

bool ConnectivityManager::initBluetoothConfig(const char* serviceUuid, BLECharacteristicCallbacks* callbacks, std::map <std::string, std::string>& characteristics) {

    BLEDevice::init("WindowAlertNode");
    BLEServer* bleServer = BLEDevice::createServer();

    BLEService* bleService = bleServer->createService(BLEUUID(serviceUuid), 30, 0);

    createCharacteristics(bleService, callbacks, characteristics);

    bleService->start();

    BLEAdvertising* advertising = bleServer->getAdvertising();
    advertising->start();

    return true;
}

void ConnectivityManager::turnOnBluetooth() {

}

void ConnectivityManager::turnOffBluetooth() {

}
