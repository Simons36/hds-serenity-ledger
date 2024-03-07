package pt.ulisboa.tecnico.hdsledger.client.service;

import java.util.Arrays;

import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.enums.TypeOfProcess;

/**
 * Service that implements listen from UDPService; it will serve to oth send
 * requests to nodes to
 * append new transactions to the blockchain, and to receive the responses from
 * the nodes.
 */
public class ClientService implements UDPService {

    //self config
    private final ProcessConfig selfConfig;
    // list with information about all nodes
    private final ProcessConfig[] nodes;
    // port this client will use to communicate
    private final int port;
    // Class that handles client business logic
    private final ClientState clientState;
    // Link that will be used to communicate with the nodes
    private final Link linkToNodes;

    public ClientService(String processConfigPath, String clientId, String ipAddress, final int port, ClientState clientState) {

        ProcessConfig[] allConfigs = new ProcessConfigBuilder().fromFile(processConfigPath);

        // Filter node configs and put them in nodes
        this.nodes = Arrays.stream(allConfigs)
                .filter(processConfig -> TypeOfProcess.node.equals(processConfig.getType()))
                .toArray(ProcessConfig[]::new);

        // Get self config
        this.selfConfig = Arrays.stream(allConfigs)
                .filter(c -> c.getId().equals(clientId))
                .findAny().orElse(null);
            

        this.port = port;
        this.clientState = clientState;

        //get self config from 

        this.linkToNodes = new Link(this.selfConfig, port, nodes, null, false);
        
    }

    /**
     * This function will listen in the provided port for responses from the nodes.
     */
    @Override
    public void listen() {
        // TODO
    }

    /**
     * This function will send a request to a single node to append a new
     * transaction to the blockchain.
     * 
     * @param appendMessage
     * @param nodeId
     */
    @Override
    public void send(AppendMessage appendMessage, String nodeId) {
        // TODO
    }

    /**
     * This function will send a request to all nodes to append a new transaction to
     * the blockchain.
     * 
     * @param appendMessage
     */
    @Override
    public void broadcast(ConsensusMessage consensusMessage) {

        System.out.println("Broadcasting message.");

        linkToNodes.broadcast(consensusMessage);
    }
}
