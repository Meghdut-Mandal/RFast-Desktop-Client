package ui;


import data.Device;

public interface DeviceAction {
    void connectDevice(Device device);

    void disconnectDevice(Device device);
}