package pt.ulisboa.tecnico.hdsledger.utilities;

public class ProcessConfig {
    public ProcessConfig() {}

    private boolean isLeader;

    private String hostname;

    private String id;

    private int port;

    private String privateKeyPath;

    private String publicKeyPath;

    public boolean isLeader() {
        return isLeader;
    }

    public int getPort() {
        return port;
    }

    public String getId() {
        return id;
    }

    public String getHostname() {
        return hostname;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }
    
    public String getPublicKeyPath() {
        return publicKeyPath;
    }



}
