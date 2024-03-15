package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.logging.Level;

import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerUpdateMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

public class ClientService implements UDPService{

    private static final CustomLogger LOGGER = new CustomLogger(ClientService.class.getName());

    // Link for clients
    private final Link linkToClients;

    // This node's configuration
    private final ProcessConfig thisNodeConfig;

    // All nodes' configuration
    private final ProcessConfig[] allNodesConfig;

    // Node service to be able to invoke consensus algorithm
    private final NodeService nodeService;

    public ClientService(Link linkToClients, ProcessConfig thisNodeConfig, ProcessConfig[] allNodesConfig, NodeService nodeService) {

        this.linkToClients = linkToClients;
        this.thisNodeConfig = thisNodeConfig;
        this.allNodesConfig = allNodesConfig;
        this.nodeService = nodeService;

    }

    @Override
    public void listen() {
        try {
            // Thread to listen on every request
            new Thread(() -> {
                try {
                    while (true) {
                        Message message = linkToClients.receive();

                        // Separate thread to handle each message
                        new Thread(() -> {
                            
                            switch (message.getType()) {
                                
                                case APPEND -> {
                                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received APPEND message from {1} with value {2}",
                                            thisNodeConfig.getId(), message.getSenderId(), (((ConsensusMessage) message).deserializeAppendMessage()).getValue()));

                                    //we now need to deserialize the message and check to get the appendMessage

                                    ConsensusMessage consensusMessage = (ConsensusMessage) message;
                                    AppendMessage appendMessage = consensusMessage.deserializeAppendMessage();


                                    // Invoke consensus algorithm
                                    nodeService.startConsensus(appendMessage.getValue());
                                }


                                case ACK ->
                                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received ACK message from {1}",
                                            thisNodeConfig.getId(), message.getSenderId()));

                                case IGNORE ->
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received IGNORE message from {1}",
                                                    thisNodeConfig.getId(), message.getSenderId()));

                                default ->
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received unknown message from {1}",
                                                    thisNodeConfig.getId(), message.getSenderId()));

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

    public void broadcastLedgerUpdate(String value, int consensusInstance){

        LedgerUpdateMessage ledgerUpdateMessage = new LedgerUpdateMessage(value);

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(thisNodeConfig.getId(), Message.Type.LEDGER_UPDATE)
                .setMessage(ledgerUpdateMessage.toJson())
                .setConsensusInstance(consensusInstance)
                .build();

        linkToClients.broadcast(consensusMessage);
    }
    
}
