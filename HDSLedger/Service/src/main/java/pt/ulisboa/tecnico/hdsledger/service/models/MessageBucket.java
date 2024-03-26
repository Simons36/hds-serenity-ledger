package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.RoundChangeMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

public class MessageBucket {

    private static final CustomLogger LOGGER = new CustomLogger(MessageBucket.class.getName());
    // Quorum size
    private final int quorumSize;
    // F size
    private final int f;
    // Instance -> Round -> Sender ID -> Consensus message
    private final Map<Integer, Map<Integer, Map<String, ConsensusMessage>>> bucket = new ConcurrentHashMap<>();

    public MessageBucket(int nodeCount) {
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
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        bucket.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        bucket.get(consensusInstance).putIfAbsent(round, new ConcurrentHashMap<>());
        bucket.get(consensusInstance).get(round).put(message.getSenderId(), message);
    }

    public Optional<String> hasValidPrepareQuorum(String nodeId, int instance, int round) {
        // Create mapping of value to frequency
        HashMap<String, Integer> frequency = new HashMap<>();
        bucket.get(instance).get(round).values().forEach((message) -> {
            PrepareMessage prepareMessage = message.deserializePrepareMessage();
            String value = prepareMessage.getValue();
            frequency.put(value, frequency.getOrDefault(value, 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<String, Integer> entry) -> {
            return entry.getValue() >= quorumSize;
        }).map((Map.Entry<String, Integer> entry) -> {
            return entry.getKey();
        }).findFirst();
    }

    public Optional<String> hasValidCommitQuorum(String nodeId, int instance, int round) {
        // Create mapping of value to frequency
        HashMap<String, Integer> frequency = new HashMap<>();

        

        bucket.get(instance).get(round).values().forEach((message) -> {
            CommitMessage commitMessage = message.deserializeCommitMessage();
            String value = commitMessage.getValue();
            frequency.put(value, frequency.getOrDefault(value, 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<String, Integer> entry) -> {
            return entry.getValue() >= quorumSize;
        }).map((Map.Entry<String, Integer> entry) -> {
            return entry.getKey();
        }).findFirst();
    }

    /**
     * This function checks if there is a valid round change quorum; returns
     * prepared round of that quorum
     * 
     * @param instance
     * @param round
     * @return
     */
    public Optional<Integer> hasValidRoundChangeQuorum(int instance, int round) {

        // in round change messages we don't have to only consider the value;
        // we have to check for prepared round and prepared value

        // The keys of this hashmap will be string arrays of the format: [preparedValue,
        // preparedRound]
        HashMap<String[], Integer> frequency = new HashMap<>();

        // Check if bucket has the instance and round
        if (bucket.get(instance) == null || bucket.get(instance).get(round) == null) {
            return Optional.empty();
        }

        // Create mapping of [value, round] to frequency
        bucket.get(instance).get(round).values().forEach((message) -> {
            RoundChangeMessage roundChangeMessage = message.deserializeRoundChangeMessage();

            // get the prepared value and round from the message
            String preparedValue = roundChangeMessage.getPreparedValue();
            int preparedRound = roundChangeMessage.getPreparedRound();

            if(preparedValue == null){
                preparedValue = "null";
            }

            String[] key = { preparedValue, Integer.toString(preparedRound) };
            boolean contains = false;
            int count = 0;
            for(String[] frequencyKey : frequency.keySet()){
                if(frequencyKey[0].equals(key[0]) && frequencyKey[1].equals(key[1])){
                    contains = true;
                    count++;
                    break;
                }
            }

            if(!contains){
                frequency.put(key, 1);
            } else {
                frequency.put(key, count + 1);
            }
            frequency.put(key, frequency.getOrDefault(key, 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<String[], Integer> entry) -> {
            return entry.getValue() >= quorumSize;
        }).map((Map.Entry<String[], Integer> entry) -> {
            return Integer.valueOf(entry.getKey()[1]); // we only return the prepared round
        }).findFirst();
    }

    public Optional<List<List<ConsensusMessage>>> getJustificationQuorumsFromRoundChange(int instance, int round) {

        // We will go through every round change message received for this instance and
        // round and return all of the justifications

        List<List<ConsensusMessage>> justificationList = new ArrayList<>();

        // Check if bucket has the instance and round
        if (bucket.get(instance) == null || bucket.get(instance).get(round) == null) {
            return Optional.empty();
        }

        bucket.get(instance).get(round).values().forEach((message) -> {
            RoundChangeMessage roundChangeMessage = message.deserializeRoundChangeMessage();
            List<ConsensusMessage> justification = roundChangeMessage.getJustification();

            if (!justification.isEmpty()) {

                // if justification doesnt contain a quorum of prepare messages, we ignore it
                if (justification.size() >= quorumSize) {
                    justificationList.add(justification);
                }

            }

        });

        if (justificationList.size() == 0) {
            return Optional.empty();
        }

        return Optional.of(justificationList);

    }

    /**
     * This function will check if there are f + 1 valid round change messages for a certain instance
     * @param instance
     * @return Minimum round of the f + 1 valid round change messages; empty if there are not enough valid round change messages
     */
    public Optional<Integer> hasFPlusOneValidRoundChange(int instance, int currentRound){

        // Check if bucket has the instance
        if (bucket.get(instance) == null) {
            return Optional.empty();
        }
        
        List<Integer> roundChangeRounds = new ArrayList<>();

        bucket.get(instance).keySet().forEach(key -> {
            bucket.get(instance).get(key).values().forEach(x -> roundChangeRounds.add(key));
        });


        
        //filter out rounds that are equal or smaller than the current round
        roundChangeRounds.removeIf(round -> round <= currentRound);
        
        //Print roundChangeRounds
        System.out.println("Round change rounds: " + roundChangeRounds);
        
        if (roundChangeRounds.size() < this.f + 1){
            return Optional.empty();
        }
        
        
        int minRound = Integer.MAX_VALUE;

        for (int round : roundChangeRounds){
            if(round < minRound){
                minRound = round;
            }
        }

        return Optional.of(minRound);
    }


    /**
     * Helper function that returns a tuple (pr, pv) where pr and pv are, respectively, the prepared round
     * and the prepared value of the ROUND-CHANGE message in received round change messages with the highest prepared round
     */
    public String[] HighestPreparedFromRoundChangeMessages(int instance, int round) {

        // We will go through every round change message received for this instance and
        // round and return the one with the highest prepared round

        int highestPreparedRound = -1;
        String highestPreparedValue = null;

        for (ConsensusMessage message : bucket.get(instance).get(round).values()) {
            RoundChangeMessage roundChangeMessage = message.deserializeRoundChangeMessage();
            int preparedRound = roundChangeMessage.getPreparedRound();

            if (preparedRound > highestPreparedRound) {
                highestPreparedRound = preparedRound;
                highestPreparedValue = roundChangeMessage.getPreparedValue();
            }
        }

        String[] result = { Integer.toString(highestPreparedRound), highestPreparedValue };
        return result;

    }

    public Map<String, ConsensusMessage> getMessages(int instance, int round) {
        return bucket.get(instance).get(round);
    }
}