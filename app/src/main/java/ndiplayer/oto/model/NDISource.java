package ndiplayer.oto.model;

public class NDISource {
    private String name;
    private String ipAddress;
    private int port;
    private String description;
    private boolean isConnected;
    private String streamUrl;

    // Constructor vacío
    public NDISource() {
        this.name = "";
        this.ipAddress = "";
        this.port = 0;
        this.description = "";
        this.isConnected = false;
        this.streamUrl = "";
    }

    public NDISource(String name, String ipAddress, int port) {
        this.name = name;
        this.ipAddress = ipAddress;
        this.port = port;
        this.description = "";
        this.isConnected = false;
    }

    public NDISource(String name, String ipAddress, int port, String description) {
        this.name = name;
        this.ipAddress = ipAddress;
        this.port = port;
        this.description = description;
        this.isConnected = false;
    }

    // Getters
    public String getName() { return name; }
    public String getIpAddress() { return ipAddress; }
    public int getPort() { return port; }
    public String getDescription() { return description; }
    public boolean isConnected() { return isConnected; }

    // Setters
    public void setName(String name) { this.name = name; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public void setPort(int port) { this.port = port; }
    public void setDescription(String description) { this.description = description; }
    public void setConnected(boolean connected) { this.isConnected = connected; }
    public void setStreamUrl(String streamUrl) { this.streamUrl = streamUrl; }

    public String getStreamUrl() {
        if (streamUrl != null && !streamUrl.isEmpty()) {
            return streamUrl;
        }
        return "udp://" + ipAddress + ":" + port;
    }

    @Override
    public String toString() {
        return name + " (" + ipAddress + ":" + port + ")";
    }
}
