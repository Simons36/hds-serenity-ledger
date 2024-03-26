package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.logging.Level;

import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.CheckBalanceResponseMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerUpdateMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSSException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

public class ClientService implements UDPService{

    private static final CustomLogger LOGGER = new CustomLogger(ClientService.class.getName());

    // Link for clients
    private final Link linkToClients;

    // This node's configuration
    private final ProcessConfig thisNodeConfig;

    // All nodes' configuration
    private final ProcessConfig[] clientsConfig;

    // Node service to be able to invoke consensus algorithm
    private final NodeService nodeService;

    public ClientService(Link linkToClients, ProcessConfig thisNodeConfig, ProcessConfig[] clientsConfig, NodeService nodeService) {

        this.linkToClients = linkToClients;
        this.thisNodeConfig = thisNodeConfig;
        this.clientsConfig = clientsConfig;
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

                                case CHECK_BALANCE -> {
                                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received CHECK_BALANCE message from {1}",
                                            thisNodeConfig.getId(), message.getSenderId()));

                                    //we now need to deserialize the message and check to get the checkBalanceMessage

                                    ConsensusMessage consensusMessage = (ConsensusMessage) message;

                                    // get public key path of the sender
                                    String senderPublicKeyPath = Arrays.stream(clientsConfig)
                                            .filter(c -> c.getId().equals(consensusMessage.getSenderId()))
                                            .findAny().orElse(null).getPublicKeyPath();

                                    if(senderPublicKeyPath == null){
                                        LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Could not find public key path for sender {1}",
                                                thisNodeConfig.getId(), consensusMessage.getSenderId()));
                                        return;
                                    }

                                    senderPublicKeyPath = "../" + senderPublicKeyPath;

                                    int balance;

                                    // Call for balance check in NodeService
                                    try {
                                        balance = nodeService.uponCheckBalance(consensusMessage);
                                    } catch (HDSSException e) {
                                        return;
                                    }

                                    // Get messageId to set replyToMessageId
                                    int replyToCheckBalanceRequestId = consensusMessage.deserializeCheckBalanceMessage().getCheckRequestBalanceId();

                                    // Send response to client
                                    CheckBalanceResponseMessage checkBalanceResponseMessage = new CheckBalanceResponseMessage(Integer.toString(balance), replyToCheckBalanceRequestId);

                                    ConsensusMessage responseMessage = new ConsensusMessageBuilder(thisNodeConfig.getId(), Message.Type.CHECK_BALANCE_RESPONSE)
                                            .setMessage(checkBalanceResponseMessage.toJson())
                                            .build();

                                    linkToClients.send(consensusMessage.getSenderId(), responseMessage);
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
