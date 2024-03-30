package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import pt.ulisboa.tecnico.hdsledger.common.models.ErrorType;
import pt.ulisboa.tecnico.hdsledger.common.models.Transaction;
import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.CheckBalanceResponseMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerUpdateMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.TransferMessage;
import pt.ulisboa.tecnico.hdsledger.communication.TransferResponseMessage;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.cryptolib.CryptoUtil;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSSException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

public class ClientService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(ClientService.class.getName());

    // Link for clients
    private final Link linkToClients;

    // This node's configuration
    private final ProcessConfig thisNodeConfig;

    // All nodes' configuration
    private final ProcessConfig[] clientsConfig;

    // Node service to be able to invoke consensus algorithm
    private final NodeService nodeService;

    // Map that maps clients to sent nonces (in base 64)
    private final Map<String, List<String>> sentNonces = new HashMap<>(); // <clientId, List<nonceInBase64>>

    public ClientService(Link linkToClients, ProcessConfig thisNodeConfig, ProcessConfig[] clientsConfig,
            NodeService nodeService) {

        this.linkToClients = linkToClients;
        this.thisNodeConfig = thisNodeConfig;
        this.clientsConfig = clientsConfig;
        this.nodeService = nodeService;

    }

    private void uponCheckBalance(Message message) {

        // we now need to deserialize the message and check to get the
        // checkBalanceMessage

        ConsensusMessage consensusMessage = (ConsensusMessage) message;

        // get public key path of the sender
        String senderPublicKeyPath = Arrays.stream(clientsConfig)
                .filter(c -> c.getId().equals(consensusMessage.getSenderId()))
                .findAny().orElse(null).getPublicKeyPath();

        if (senderPublicKeyPath == null) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Could not find public key path for sender {1}",
                    thisNodeConfig.getId(), consensusMessage.getSenderId()));
            return;
        }

        senderPublicKeyPath = "../" + senderPublicKeyPath;

        double balance;

        // Call for balance check in NodeService
        try {
            balance = nodeService.uponCheckBalance(consensusMessage);
        } catch (HDSSException e) {
            return;
        }

        // Get messageId to set replyToMessageId
        long replyToCheckBalanceRequestId = consensusMessage.deserializeCheckBalanceMessage().getCheckRequestBalanceId();

        // Send response to client
        CheckBalanceResponseMessage checkBalanceResponseMessage = new CheckBalanceResponseMessage(
                Double.toString(balance), replyToCheckBalanceRequestId);

        ConsensusMessage responseMessage = new ConsensusMessageBuilder(thisNodeConfig.getId(),
                Message.Type.CHECK_BALANCE_RESPONSE)
                .setMessage(checkBalanceResponseMessage.toJson())
                .build();

        linkToClients.send(consensusMessage.getSenderId(), responseMessage);
    }

    private void uponTransfer(Message message) {

        // Get transfer message
        TransferMessage transferMessage = ((ConsensusMessage) message).deserializeTransferMessage();

        long messageId = transferMessage.getTransferMessageId();
        String originalSenderId = message.getSenderId();

        // Get transaction
        Transaction transaction = transferMessage.getTransaction();

        // We will now proceed to make some verifications of this transfer

        // Get public key path of the sender
        String senderPublicKeyPath = Arrays.stream(clientsConfig)
                .filter(c -> c.getId().equals(message.getSenderId()))
                .findAny().orElse(null).getPublicKeyPath();

        // If senderId doesnt exist -> cant find public key path
        if (senderPublicKeyPath == null) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Could not find public key path for sender {1}",
                    thisNodeConfig.getId(), message.getSenderId()));

            SendTransferErrorMessage(originalSenderId, messageId, ErrorType.INVALID_SENDER_ID);
            return;
        }

        senderPublicKeyPath = "../" + senderPublicKeyPath;

        // Compare sender's public key with the one in the transaction: must be equal

        try {
            if (!CryptoUtil.verifyPublicKey(senderPublicKeyPath, transaction.getSenderPublicKey())) {
                LOGGER.log(Level.SEVERE,
                        MessageFormat.format("{0} - Sender public key does not match the one in the transaction",
                                thisNodeConfig.getId()));

                SendTransferErrorMessage(originalSenderId, messageId, ErrorType.INVALID_SENDER_PK);
                return;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Error while verifying sender public key",
                    thisNodeConfig.getId()));

            SendTransferErrorMessage(originalSenderId, messageId, ErrorType.UNKNOWN_ERROR);
        }

        // Verify if there is any client with the receiver public key

        String receiverId = new String();

        receiverId = Arrays.stream(clientsConfig)
                .filter(c -> {
                    try {
                        return CryptoUtil.verifyPublicKey("../" + c.getPublicKeyPath(),
                                transaction.getReceiverPublicKey());
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Error while verifying receiver public key",
                                thisNodeConfig.getId()));

                        SendTransferErrorMessage(originalSenderId, messageId, ErrorType.UNKNOWN_ERROR);
                        return false;
                    }
                })
                .map(ProcessConfig::getId)
                .findAny().orElse(null);

        if (receiverId == null) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Could not find receiver with public key",
                    thisNodeConfig.getId()));

            SendTransferErrorMessage(originalSenderId, messageId, ErrorType.INVALID_RECEIVER_ID);
            return;
        }

        // We now verify the if the message was actually sent by the sender
        // We do this by verifying that the signature of the transaction is valid
        // AsymmetricDecrypt(signature, senderPublicKey) == transactionId

        try {
            if (!CryptoUtil.verifySignature(transaction.getRawTransactionId(), transaction.getSignature(),
                    transaction.getSenderPublicKey())) {
                LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Invalid signature for transaction",
                        thisNodeConfig.getId()));

                SendTransferErrorMessage(originalSenderId, messageId, ErrorType.INVALID_SIGNATURE);
                return;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Error while verifying signature",
                    thisNodeConfig.getId()));

            SendTransferErrorMessage(originalSenderId, messageId, ErrorType.UNKNOWN_ERROR);
            return;
        }

        // Now we will verify if transactionID = hash(senderPublicKey ||
        // receiverPublicKey || amount || nonce)

        try {

            byte[] newTransactionID = Transaction.CreateTransactionId(transaction.getSenderPublicKey(),
                    transaction.getReceiverPublicKey(),
                    transaction.getAmount(),
                    transaction.getNonceInBase64());

            // Compare the two transaction IDs

            if (!Arrays.equals(newTransactionID, transaction.getRawTransactionId())) {
                LOGGER.log(Level.SEVERE,
                        MessageFormat.format("{0} - Transaction ID does not match the hash of the transaction",
                                thisNodeConfig.getId()));

                SendTransferErrorMessage(originalSenderId, messageId, ErrorType.INVALID_TRANSACTION_ID);
                return;
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Error while verifying transaction ID",
                    thisNodeConfig.getId()));

            SendTransferErrorMessage(originalSenderId, messageId, ErrorType.UNKNOWN_ERROR);
            return;
        }

        // Lastly, we verify if the nonce is valid (it has never been sent before by
        // this client)
        if (!sentNonces.containsKey(originalSenderId)) {
            sentNonces.put(originalSenderId, List.of(transaction.getNonceInBase64()));
        } else {
            if (sentNonces.get(originalSenderId).contains(transaction.getNonceInBase64())) {
                LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Nonce has already been used",
                        thisNodeConfig.getId()));

                SendTransferErrorMessage(originalSenderId, messageId, ErrorType.INVALID_NONCE);
                return;
            }
        }

        // We have now most of the verifications. The missing verifications (amount is
        // correct) will be done in the nodeService

        this.nodeService.uponTransfer(transaction, originalSenderId, receiverId, messageId);
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
                                    LOGGER.log(Level.INFO, MessageFormat.format(
                                            "{0} - Received APPEND message from {1} with value {2}",
                                            thisNodeConfig.getId(), message.getSenderId(),
                                            (((ConsensusMessage) message).deserializeAppendMessage()).getValue()));

                                    // we now need to deserialize the message and check to get the appendMessage

                                    ConsensusMessage consensusMessage = (ConsensusMessage) message;
                                    AppendMessage appendMessage = consensusMessage.deserializeAppendMessage();

                                    // Invoke consensus algorithm
                                    nodeService.startConsensus(appendMessage.getValue());
                                }

                                case CHECK_BALANCE -> {
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received CHECK_BALANCE message from {1}",
                                                    thisNodeConfig.getId(), message.getSenderId()));
                                    uponCheckBalance(message);
                                }

                                case TRANSFER -> {
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received TRANSFER message from {1}",
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

    public void SendTransferErrorMessage(String senderId, long originalMessageId, ErrorType errorType) {
        ConsensusMessage errorMessage = new ConsensusMessageBuilder(thisNodeConfig.getId(),
                Message.Type.TRANSFER_RESPONSE)
                .setMessage(new TransferResponseMessage(originalMessageId, false, errorType).toJson())
                .build();

        this.linkToClients.send(senderId, errorMessage);
    }

    public void SendTransferSuccessMessage(String senderId, long originalMessageId) {
        ConsensusMessage successMessage = new ConsensusMessageBuilder(thisNodeConfig.getId(),
                Message.Type.TRANSFER_RESPONSE)
                .setMessage(new TransferResponseMessage(originalMessageId, true).toJson())
                .build();

        this.linkToClients.send(senderId, successMessage);
    }

}
