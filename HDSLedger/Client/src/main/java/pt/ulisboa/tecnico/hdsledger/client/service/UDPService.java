package pt.ulisboa.tecnico.hdsledger.client.service;

import pt.ulisboa.tecnico.hdsledger.common.models.AppendMessage;

public interface UDPService {
    void listen();
    
    void send(AppendMessage appendMessage, String nodeId);

    void broadcast(AppendMessage appendMessage);
}
