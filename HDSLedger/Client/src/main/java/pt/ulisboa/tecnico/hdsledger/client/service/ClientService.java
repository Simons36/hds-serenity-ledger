package pt.ulisboa.tecnico.hdsledger.client.service;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.logging.Level;

import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
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
    // Number of nodes that contitute a quorum
    private final int quorumSize;
    // Verbose mode (if true then print additional information)
    private final boolean verboseMode;
    

    public ClientService(ProcessConfig[] allConfigs, String clientId, String ipAddress, final int port, ClientState clientState, boolean verboseMode){

        // Filter node configs and put them in nodes
        this.nodes = Arrays.stream(allConfigs)
                .filter(processConfig -> TypeOfProcess.node.equals(processConfig.getType()))
                .toArray(ProcessConfig[]::new);

                
        int f = Math.floorDiv(nodes.length - 1, 3);
        quorumSize = Math.floorDiv(nodes.length + f, 2) + 1;

        // Get self config
        this.selfConfig = Arrays.stream(allConfigs)
                .filter(c -> c.getId().equals(clientId))
                .findAny().orElse(null);
            

        this.port = port;
        this.clientState = clientState;

        this.verboseMode = verboseMode;

        this.linkToNodes = new Link(this.selfConfig, port, nodes, ConsensusMessage.class, false, verboseMode);
        
    }

    /**
     * This function will listen in the provided port for responses from the nodes.
     */
    @Override
    public void listen() {
        try {
            // Thread to listen on every request
            new Thread(() -> {
                try {
                    while (true) {
                        Message message = linkToNodes.receive();

                        ProcessConfig sendingNode = Arrays.stream(nodes)
                        .filter(node -> node.getId().equals(message.getSenderId()))
                        .findAny()
                        .orElse(null);

                        if(sendingNode == null){
                            if(verboseMode){
                                System.out.println(MessageFormat.format("Received message from unknown node {0}", message.getSenderId()));
                            }
                            continue;
                        }

                        // Separate thread to handle each message
                        new Thread(() -> {
                            
                            switch (message.getType()) {


                                case ACK:
                                    if(verboseMode)
                                    System.out.println(MessageFormat.format("{0} - Received ACK message from {1}",
                                            this.selfConfig.getId(), message.getSenderId()));
                                    break;

                                case LEDGER_UPDATE:
                                    if(verboseMode)
                                    System.out.println(MessageFormat.format("{0} - Received LEDGER_UPDATE message from {1}",
                                    this.selfConfig.getId(), message.getSenderId()));

                                    this.clientState.ledgerUpdate((ConsensusMessage) message);
                                    break;

                                case CHECK_BALANCE_RESPONSE:
                                    if(verboseMode)
                                        System.out.println(MessageFormat.format("{0} - Received CHECK_BALANCE_RESPONSE message from {1}",
                                                this.selfConfig.getId(), message.getSenderId()));

                                    this.clientState.uponCheckBalanceResponse((ConsensusMessage) message);
                                    break;

                                case TRANSFER_RESPONSE:
                                    if(verboseMode)
                                        System.out.println(MessageFormat.format("{0} - Received TRANSFER_RESPONSE message from {1}",
                                                this.selfConfig.getId(), message.getSenderId()));

                                    this.clientState.uponTransferResponse((ConsensusMessage) message);
                                    break;

                                case IGNORE:
                                    if(verboseMode)
                                    System.out.println(MessageFormat.format("{0} - Received IGNORE message from {1}",
                                                    this.selfConfig.getId(), message.getSenderId()));

                                    break;

                                default:
                                    if(verboseMode)
                                    System.out.println(MessageFormat.format("{0} - Received unknown message from {1}",
                                                    this.selfConfig.getId(), message.getSenderId()));

                                    break;

                            }

                        }).start();
                    }
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
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

        if(verboseMode){
            System.out.println("Broadcasting message.");
        }


        linkToNodes.broadcast(consensusMessage);
    }

    public int getQuorumSize() {
        return quorumSize;
    }
}
