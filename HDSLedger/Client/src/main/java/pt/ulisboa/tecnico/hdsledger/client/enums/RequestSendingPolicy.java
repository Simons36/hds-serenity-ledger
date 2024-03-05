package pt.ulisboa.tecnico.hdsledger.client.enums;

public enum RequestSendingPolicy {

    ALL,       // Broadcast to all nodes
    QUORUM,    // Send to a quorum of nodes
    ONE,       // Send to one node (leader)
    
}
