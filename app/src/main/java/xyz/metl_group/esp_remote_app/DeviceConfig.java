package xyz.metl_group.esp_remote_app;

public class DeviceConfig {
    private String name;
    private String url;

    public DeviceConfig(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return name;
    }
}
