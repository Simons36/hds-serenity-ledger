package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.logging.Level;

import javax.lang.model.type.ErrorType;

import pt.ulisboa.tecnico.hdsledger.common.models.Transaction;
import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.CheckBalanceResponseMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ClientErrorMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ClientErrorMessage;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerUpdateMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.cryptolib.CryptoIO;
import pt.ulisboa.tecnico.hdsledger.cryptolib.CryptoUtil;
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

    private void uponCheckBalance(Message message){

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


    private void uponTransfer(Message message){

        int messageId = message.getMessageId();
        String originalSenderId = message.getSenderId();

        // Get transaction
        Transaction transaction = ((ConsensusMessage) message).deserializeTransferMessage().getTransaction();

        // We will now proceed to make some verifications of this transfer

        // Get public key path of the sender
        String senderPublicKeyPath = Arrays.stream(clientsConfig)
                .filter(c -> c.getId().equals(message.getSenderId()))
                .findAny().orElse(null).getPublicKeyPath();

        // If senderId doesnt exist -> cant find public key path
        if(senderPublicKeyPath == null){
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Could not find public key path for sender {1}",
                    thisNodeConfig.getId(), message.getSenderId()));
            
            SendErrorMessage(originalSenderId, messageId, ClientErrorMessage.ErrorType.INVALID_SENDER_ID);
            return;
        }

        senderPublicKeyPath = "../" + senderPublicKeyPath;

        // Compare sender's public key with the one in the transaction: must be equal

        try {
            if(!CryptoUtil.verifyPublicKey(senderPublicKeyPath, transaction.getSenderPublicKey())){
                LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Sender public key does not match the one in the transaction",
                        thisNodeConfig.getId()));
                
                SendErrorMessage(originalSenderId, messageId, ClientErrorMessage.ErrorType.INVALID_SENDER_PK);
                return;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Error while verifying sender public key",
                    thisNodeConfig.getId()));

            SendErrorMessage(originalSenderId, messageId, ClientErrorMessage.ErrorType.UNKNOWN_ERROR);
        }


        // Verify if there is any client with the receiver public key

        String receiverId = new String();


        receiverId = Arrays.stream(clientsConfig)
                    .filter(c -> {
                        try {
                            return CryptoUtil.verifyPublicKey("../" + c.getPublicKeyPath(), transaction.getReceiverPublicKey());
                        } catch (Exception e) {
                            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Error while verifying receiver public key",
                                thisNodeConfig.getId()));

                            SendErrorMessage(originalSenderId, messageId, ClientErrorMessage.ErrorType.UNKNOWN_ERROR);
                            return false;
                        }
                    })
                    .map(ProcessConfig::getId)
                    .findAny().orElse(null);

        if(receiverId == null){
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Could not find receiver with public key",
                    thisNodeConfig.getId()));
            
            SendErrorMessage(originalSenderId, messageId, ClientErrorMessage.ErrorType.INVALID_RECEIVER_ID);
            return;
        }
        
        
        this.nodeService.uponTransfer(transaction, originalSenderId, receiverId);
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
                                    uponCheckBalance(message);
                                }

                                case TRANSFER -> {
                                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received TRANSFER message from {1}",
                                            thisNodeConfig.getId(), message.getSenderId()));
                                    uponTransfer(message);
                                    
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

    private void SendErrorMessage(String senderId, int originalMessageId, ClientErrorMessage.ErrorType errorType){
        ClientErrorMessage errorMessage = new ClientErrorMessage(senderId, errorType, originalMessageId);

        this.linkToClients.send(senderId, errorMessage);
    }

    
}
