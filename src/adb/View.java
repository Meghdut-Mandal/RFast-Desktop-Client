package adb;

import data.Device;

public interface View {

    void showNoConnectedDevicesNotification();

    void showConnectedDeviceNotification(Device device);

    void showDisconnectedDeviceNotification(Device device);

    void showErrorConnectingDeviceNotification(Device device);

    void showErrorDisconnectingDeviceNotification(Device device);

    void showADBNotInstalledNotification();
}