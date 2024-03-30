package pt.ulisboa.tecnico.hdsledger.client.models;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import pt.ulisboa.tecnico.hdsledger.common.models.ErrorType;
import pt.ulisboa.tecnico.hdsledger.communication.CheckBalanceResponseMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

public class ClientMessageBucket {

    private static final CustomLogger LOGGER = new CustomLogger(ClientMessageBucket.class.getName());
    // Quorum size
    private final int quorumSize;
    // F size
    private final int f;
    // ReplyToMessageId -> Sender ID -> Consensus message
    private final Map<Long, Map<String, ConsensusMessage>> bucket = new ConcurrentHashMap<>();
    // Map with error for a given transfer message ID
    private Map<Long, ErrorType> transferErrors = new ConcurrentHashMap<>(); // <transferMessageId, error>

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

        if(message.getType().equals(Message.Type.CHECK_BALANCE_RESPONSE)){ // if it is a check balance response message
            long replyToCheckBalanceRequestId = message.deserializeCheckBalanceResponseMessage().getResponseToCheckBalanceRequestId(); //to know to wich check balance request this message is replying to
            String senderId = message.getSenderId();
    
            bucket.putIfAbsent(replyToCheckBalanceRequestId, new ConcurrentHashMap<>());
            bucket.get(replyToCheckBalanceRequestId).putIfAbsent(senderId, message); //if already present, then is  duplicate message, so skip
        }else{ //otherwise, it is a transfer response
            long replyToTransferRequestId = message.deserializeTransferResponseMessage().getResponseToMessageId(); //to know to wich transfer request this message is replying to
            String senderId = message.getSenderId();
    
            bucket.putIfAbsent(replyToTransferRequestId, new ConcurrentHashMap<>());
            bucket.get(replyToTransferRequestId).putIfAbsent(senderId, message); //if already present, then is  duplicate message, so skip
        }
    }

    public Optional<Double> hasValidCheckBalanceResponseQuorum(long replyToCheckBalanceId) {
        // Create mapping of value to frequency
        HashMap<Double, Integer> frequency = new HashMap<>();
        bucket.get(replyToCheckBalanceId).values().forEach((message) -> {
            CheckBalanceResponseMessage checkBalanceResponseMessage = message.deserializeCheckBalanceResponseMessage();
            double balance = Double.parseDouble(checkBalanceResponseMessage.getBalance());
            frequency.put(balance, frequency.getOrDefault(balance, 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<Double, Integer> entry) -> {
            return entry.getValue() >= quorumSize;
        }).map((Map.Entry<Double, Integer> entry) -> {
            return entry.getKey();
        }).findFirst();
    }

    public Optional<Boolean> hasValidTransferResponseQuorum(long replyToTransferId) {
        // Create mapping of value to frequency
        HashMap<Boolean, Integer> frequency = new HashMap<>();
        bucket.get(replyToTransferId).values().forEach((message) -> {
            boolean success = message.deserializeTransferResponseMessage().isSuccess();
            frequency.put(success, frequency.getOrDefault(success, 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<Boolean, Integer> entry) -> {
            return entry.getValue() >= quorumSize;
        }).map((Map.Entry<Boolean, Integer> entry) -> {
            if(entry.getKey() == false){
                Optional<ErrorType> errorType = GetErrorType(replyToTransferId);

                if(errorType.isPresent()){
                    transferErrors.put(replyToTransferId, errorType.get());

                }else{
                    transferErrors.put(replyToTransferId, ErrorType.UNKNOWN_ERROR); //should never happen
                }
            }
            return entry.getKey();
        }).findFirst();
    }

    private Optional<ErrorType> GetErrorType(long replyToTransferId) {
        return bucket.get(replyToTransferId).values().stream()
                .map(ConsensusMessage::deserializeTransferResponseMessage)
                .filter(responseMessage -> !responseMessage.isSuccess())
                .findFirst()
                .flatMap(responseMessage -> Optional.of(responseMessage.getErrorMessage()));
    }

    public ErrorType getTransferError(long replyToTransferId){
        return transferErrors.get(replyToTransferId);
    }
}