package xyz.metl_group.esp_remote_app;

import java.util.ArrayList;
import java.util.List;

public class DeviceManager {
    private List<DeviceConfig> devices;
    private DeviceConfig selectedDevice;

    public DeviceManager() {
        devices = new ArrayList<>();
    }

    public void addDevice(DeviceConfig device) {
        devices.add(device);
    }

    public void selectDevice(int index) {
        if (index >= 0 && index < devices.size()) {
            selectedDevice = devices.get(index);
        }
    }

    public DeviceConfig getSelectedDevice() {
        return selectedDevice;
    }

    public List<DeviceConfig> getDevices() {
        return devices;
    }
}
