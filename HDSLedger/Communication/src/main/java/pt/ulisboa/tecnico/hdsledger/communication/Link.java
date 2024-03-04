package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import pt.ulisboa.tecnico.hdsledger.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.cryptolib.CryptoLibrary;
import pt.ulisboa.tecnico.hdsledger.cryptolib.structs.AuthenticationType;
import pt.ulisboa.tecnico.hdsledger.cryptolib.structs.SignatureStruct;
import pt.ulisboa.tecnico.hdsledger.utilities.*;

import java.io.IOException;
import java.net.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.LogManager;

public class Link {

    private static final CustomLogger LOGGER = new CustomLogger(Link.class.getName());
    // Time to wait for an ACK before resending the message
    private final int BASE_SLEEP_TIME;
    // UDP Socket
    private final DatagramSocket socket;
    // Map of all nodes in the network
    private final Map<String, ProcessConfig> nodes = new ConcurrentHashMap<>();
    // Reference to the node itself
    private final ProcessConfig config;
    // Class to deserialize messages to
    private final Class<? extends Message> messageClass;
    // Set of received messages from specific node (prevent duplicates)
    private final Map<String, CollapsingSet> receivedMessages = new ConcurrentHashMap<>();
    // Set of received ACKs from specific node
    private final CollapsingSet receivedAcks = new CollapsingSet();
    // Message counter
    private final AtomicInteger messageCounter = new AtomicInteger(0);
    // Send messages to self by pushing to queue instead of through the network
    private final Queue<Message> localhostQueue = new ConcurrentLinkedQueue<>();
    // Create library to call for cryptographic operations
    private final CryptoLibrary cryptoLibrary;

    public Link(ProcessConfig self, int port, ProcessConfig[] nodes, Class<? extends Message> messageClass) {
        this(self, port, nodes, messageClass, false, 200);
    }

    public Link(ProcessConfig self, int port, ProcessConfig[] nodes, Class<? extends Message> messageClass,
            boolean activateLogs, int baseSleepTime) {
        

        this.config = self;
        this.messageClass = messageClass;
        this.BASE_SLEEP_TIME = baseSleepTime;

        Arrays.stream(nodes).forEach(node -> {
            String id = node.getId();
            this.nodes.put(id, node);
            receivedMessages.put(id, new CollapsingSet());
        });

        try {
            this.socket = new DatagramSocket(port, InetAddress.getByName(config.getHostname()));
        } catch (UnknownHostException | SocketException e) {
            throw new HDSSException(ErrorMessage.CannotOpenSocket);
        }
        if (!activateLogs) {
            LogManager.getLogManager().reset();
        }

        //add private key and public keys to the crypto library
        try {

            this.cryptoLibrary = new CryptoLibrary("../" + config.getPrivateKeyPath());

            for (ProcessConfig node : nodes) {
                this.cryptoLibrary.addPublicKey(node.getId(), "../" + node.getPublicKeyPath());
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new HDSSException(ErrorMessage.ErrorImportingPrivateKey);
        }
    }

    public void ackAll(List<Integer> messageIds) {
        receivedAcks.addAll(messageIds);
    }

    /*
     * Broadcasts a message to all nodes in the network
     *
     * @param data The message to be broadcasted
     */
    public void broadcast(Message data) {
        Gson gson = new Gson();
        nodes.forEach((destId, dest) -> send(destId, gson.fromJson(gson.toJson(data), data.getClass())));
    }

    /*
     * Sends a message to a specific node with guarantee of delivery
     *
     * @param nodeId The node identifier
     *
     * @param data The message to be sent
     */
    public void send(String nodeId, Message data) {

        // Spawn a new thread to send the message
        // To avoid blocking while waiting for ACK
        new Thread(() -> {
            try {
                ProcessConfig node = nodes.get(nodeId);
                if (node == null)
                    throw new HDSSException(ErrorMessage.NoSuchNode);

                data.setMessageId(messageCounter.getAndIncrement());

                // If the message is not ACK, it will be resent
                InetAddress destAddress = InetAddress.getByName(node.getHostname());
                int destPort = node.getPort();
                int count = 1;
                int messageId = data.getMessageId();
                int sleepTime = BASE_SLEEP_TIME;

                SignatureStruct signature = cryptoLibrary.sign(data.toString().getBytes());

                // Send message to local queue instead of using network if destination in self
                if (nodeId.equals(this.config.getId())) {
                    this.localhostQueue.add(data);

                    LOGGER.log(Level.INFO,
                            MessageFormat.format("{0} - Message {1} (locally) sent to {2}:{3} successfully",
                                    config.getId(), data.getType(), destAddress, destPort));

                    return;
                }

                for (;;) {
                    LOGGER.log(Level.INFO, MessageFormat.format(
                            "{0} - Sending {1} message to {2}:{3} with message ID {4} - Attempt #{5}", config.getId(),
                            data.getType(), destAddress, destPort, messageId, count++));
                    
                    //print base 64 bytes of date
                    unreliableSend(destAddress, destPort, data, signature);

                    // Wait (using exponential back-off), then look for ACK
                    Thread.sleep(sleepTime);

                    // Receive method will set receivedAcks when sees corresponding ACK
                    if (receivedAcks.contains(messageId))
                        break;

                    sleepTime <<= 1;
                }

                LOGGER.log(Level.INFO, MessageFormat.format("{0} - Message {1} sent to {2}:{3} successfully",
                        config.getId(), data.getType(), destAddress, destPort));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /*
     * Sends a message to a specific node without guarantee of delivery
     * Mainly used to send ACKs, if they are lost, the original message will be
     * resent
     *
     * @param address The address of the destination node
     *
     * @param port The port of the destination node
     *
     * @param data The message to be sent
     */
    public void unreliableSend(InetAddress hostname, int port, Message data, AuthenticationType authentication) {
        new Thread(() -> {
            try {

                //we will take the authentication used (e.g., MAC or digital signature) and insert it into the message 
                
                //first create json of the message
                JsonObject messageJson = new Gson().toJsonTree(data).getAsJsonObject();

                //then add field for authentication, stored as base64 string
                String signatureBase64 = Base64.getEncoder().encodeToString(authentication.getContent());
                messageJson.addProperty(authentication.getAuthenticationTypeName(), signatureBase64);

                //finally, convert the json to bytes and send it
                byte[] buf = messageJson.toString().getBytes();
                DatagramPacket packet = new DatagramPacket(buf, buf.length, hostname, port);
                socket.send(packet);

            } catch (IOException e) {
                e.printStackTrace();
                throw new HDSSException(ErrorMessage.SocketSendingError);
            }
        }).start();
    }

    /*
     * Receives a message from any node in the network (blocking)
     */
    public Message receive() throws IOException, ClassNotFoundException, HDSSException, Exception {

        Message message = null;
        String serialized = "";
        Boolean local = false;
        DatagramPacket response = null;
        JsonObject messageJson = null;
        
        if (this.localhostQueue.size() > 0) {
            message = this.localhostQueue.poll();
            local = true; 
            this.receivedAcks.add(message.getMessageId());
        } else {
            byte[] buf = new byte[65535];
            response = new DatagramPacket(buf, buf.length);
            
            socket.receive(response);
            
            byte[] buffer = Arrays.copyOfRange(response.getData(), 0, response.getLength());
            serialized = new String(buffer);
            
            messageJson = new Gson().fromJson(serialized, JsonObject.class);
            
            //we will now check if the message has a field for authentication
            //if it does, we will remove it and verify the authentication
            //if it doesn't, we will assume the message is from the local network and not verify the authentication
            
            //print messageJson
            
            if (messageJson.has("digital_signature")) {
                //we will now remove the authentication field and verify the signature
                String signature = messageJson.get("digital_signature").getAsString();
                messageJson.remove("digital_signature");
                
                message = new Gson().fromJson(messageJson, Message.class);

                byte[] signatureBytes = Base64.getDecoder().decode(signature);

                
                //we will now convert the json back to bytes and verify the signature
                byte[] messageBytes = message.toString().getBytes();
                
                try {
                    
                    if (!cryptoLibrary.verifySignature(messageBytes, new SignatureStruct(signatureBytes), nodes.get(messageJson.get("senderId").getAsString()).getId())) {
                        LOGGER.log(Level.INFO, "Invalid signature! Ignoring message with message ID " + messageJson.get("messageId").getAsInt());
                        message.setType(Message.Type.IGNORE);

                        
                    }

                } catch (Exception e) {
                    throw e;
                }

            }else{
                message = new Gson().fromJson(messageJson, Message.class);
                message.setType(Message.Type.IGNORE);
            }

        }

        
        String senderId = message.getSenderId();
        int messageId = message.getMessageId();
        
        if (!nodes.containsKey(senderId))
        throw new HDSSException(ErrorMessage.NoSuchNode);

        // Handle ACKS, since it's possible to receive multiple acks from the same
        // message
        if (message.getType().equals(Message.Type.ACK)) {
            receivedAcks.add(messageId);
            return message;
        }

        // It's not an ACK -> Deserialize for the correct type
        if (!local && !message.getType().equals(Message.Type.IGNORE)){}
        message = new Gson().fromJson(messageJson, this.messageClass);

        boolean isRepeated = !receivedMessages.get(message.getSenderId()).add(messageId);
        Type originalType = message.getType();
        // Message already received (add returns false if already exists) => Discard
        if (isRepeated) {
            message.setType(Message.Type.IGNORE);
        }
        System.out.println(message.getType());

        switch (message.getType()) {
            case PRE_PREPARE -> {
                return message;
            }
            case IGNORE -> {
                if (!originalType.equals(Type.COMMIT))
                    return message;
            }
            case PREPARE -> {
                ConsensusMessage consensusMessage = (ConsensusMessage) message;
                if (consensusMessage.getReplyTo() != null && consensusMessage.getReplyTo().equals(config.getId()))
                    receivedAcks.add(consensusMessage.getReplyToMessageId());

                return message;
            }
            case COMMIT -> {
                ConsensusMessage consensusMessage = (ConsensusMessage) message;
                if (consensusMessage.getReplyTo() != null && consensusMessage.getReplyTo().equals(config.getId()))
                    receivedAcks.add(consensusMessage.getReplyToMessageId());
            }
            default -> {}
        }

        if (!local) {
            InetAddress address = InetAddress.getByName(response.getAddress().getHostAddress());
            int port = response.getPort();

            Message responseMessage = new Message(this.config.getId(), Message.Type.ACK);
            responseMessage.setMessageId(messageId);

            // ACK is sent without needing for another ACK because
            // we're assuming an eventually synchronous network
            // Even if a node receives the message multiple times,
            // it will discard duplicates
            unreliableSend(address, port, responseMessage, new SignatureStruct(responseMessage.toString().getBytes()));
        }
        
        return message;
    }
}
