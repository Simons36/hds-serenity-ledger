package pt.ulisboa.tecnico.hdsledger.client.service;

import java.util.HashMap;
import java.util.Map;

import pt.ulisboa.tecnico.hdsledger.common.models.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.common.services.UDPService;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;

/**
 * Service that implements listen from UDPService; it will serve to oth send requests to nodes to
 * append new transactions to the blockchain, and to receive the responses from the nodes.
 */
public class ClientService implements UDPService{
    
    //list with information about all nodes
    private final ProcessConfig[] nodes;
    //port this client will use to communicate
    private final int port;
    //Class that handles client business logic
    private final ClientState clientState;

    public ClientService(String processConfigPath, final int port, ClientState clientState) {

        this.nodes = new ProcessConfigBuilder().fromFile(processConfigPath);
        this.port = port;
        this.clientState = clientState;
    }

    /**
     * This function will listen in the provided port for responses from the nodes.
     */
    @Override
    public void listen() {
        //TODO
    }

    /**
     * This function will send a request to a single node to append a new transaction to the blockchain.
     * 
     * @param appendMessage
     * @param nodeId
     */
    public void send(AppendMessage appendMessage, String nodeId) {
        //TODO
    }


    /**
     * This function will send a request to all nodes to append a new transaction to the blockchain.
     * 
     * @param appendMessage
     */
    public void broadcast(AppendMessage appendMessage) {
        //TODO
    }
}
