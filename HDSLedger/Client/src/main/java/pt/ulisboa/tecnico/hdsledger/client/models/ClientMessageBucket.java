package pt.ulisboa.tecnico.hdsledger.client.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import pt.ulisboa.tecnico.hdsledger.communication.CheckBalanceResponseMessage;
import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.RoundChangeMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

public class ClientMessageBucket {

    private static final CustomLogger LOGGER = new CustomLogger(ClientMessageBucket.class.getName());
    // Quorum size
    private final int quorumSize;
    // F size
    private final int f;
    // ReplyToMessageId -> Sender ID -> Consensus message
    private final Map<Integer, Map<String, ConsensusMessage>> bucket = new ConcurrentHashMap<>();

    public ClientMessageBucket(int nodeCount) {
        this.f = Math.floorDiv(nodeCount - 1, 3);
        quorumSize = Math.floorDiv(nodeCount + f, 2) + 1;
    }

    /*
     * Add a message to the bucket
     * 
     * @param consensusInstance
     * 
     * @param message
     */
    public void addMessage(ConsensusMessage message) {
        int replyToCheckBalanceRequestId = message.deserializeCheckBalanceResponseMessage().getResponseToCheckBalanceRequestId(); //to know to wich check balance request this message is replying to
        String senderId = message.getSenderId();

        bucket.putIfAbsent(replyToCheckBalanceRequestId, new ConcurrentHashMap<>());
        bucket.get(replyToCheckBalanceRequestId).putIfAbsent(senderId, message); //if already present, then is  duplicate message, so skip
    }

    public Optional<Integer> hasValidCheckBalanceResponseQuorum(int replyToCheckBalanceId) {
        // Create mapping of value to frequency
        HashMap<Integer, Integer> frequency = new HashMap<>();
        bucket.get(replyToCheckBalanceId).values().forEach((message) -> {
            CheckBalanceResponseMessage checkBalanceResponseMessage = message.deserializeCheckBalanceResponseMessage();
            int balance = Integer.parseInt(checkBalanceResponseMessage.getBalance());
            frequency.put(balance, frequency.getOrDefault(balance, 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<Integer, Integer> entry) -> {
            return entry.getValue() >= quorumSize;
        }).map((Map.Entry<Integer, Integer> entry) -> {
            return entry.getKey();
        }).findFirst();
    }
}