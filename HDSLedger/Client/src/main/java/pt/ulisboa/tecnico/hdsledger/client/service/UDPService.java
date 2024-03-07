package pt.ulisboa.tecnico.hdsledger.client.service;

import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;

public interface UDPService {
    void listen();
    
    void send(AppendMessage appendMessage, String nodeId);

    void broadcast(ConsensusMessage appendMessage);
}
