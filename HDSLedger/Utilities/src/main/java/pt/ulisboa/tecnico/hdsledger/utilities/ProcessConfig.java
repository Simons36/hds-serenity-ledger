package pt.ulisboa.tecnico.hdsledger.utilities;

import pt.ulisboa.tecnico.hdsledger.utilities.enums.TypeOfProcess;

public class ProcessConfig {
    public ProcessConfig() {}

    private TypeOfProcess type;

    private boolean isLeader;

    private String hostname;

    private String id;

    private int port;

    private int clientRequestPort;

    private String privateKeyPath;

    private String publicKeyPath;

    // Used for leader election
    private int nodePosition;

    public void setType(TypeOfProcess type) {
        this.type = type;
    }

    //get the type of the process
    public TypeOfProcess getType() {
        return type;
    }

    public boolean isLeader() {
        return isLeader;
    }

    public int getPort() {
        return port;
    }

    public int getClientRequestPort() {
        return clientRequestPort;
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

    public int getNodePosition() {
        return nodePosition;
    }

    public void setNodePosition(int nodePosition) {
        this.nodePosition = nodePosition;
    }



}
