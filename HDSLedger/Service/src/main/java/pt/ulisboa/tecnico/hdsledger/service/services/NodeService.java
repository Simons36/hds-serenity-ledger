package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.PrePrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.RoundChangeMessage;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.service.models.AccountInfo;
import pt.ulisboa.tecnico.hdsledger.service.models.InstanceInfo;
import pt.ulisboa.tecnico.hdsledger.service.models.MessageBucket;
import pt.ulisboa.tecnico.hdsledger.service.models.RoundTimer;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

public class NodeService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(NodeService.class.getName());

    // Number of seconds to reset timer
    private static final int SECONDS_TO_RESET_TIMER = 5;
    // Nodes configurations
    private final ProcessConfig[] nodesConfig;

    // Current node is leader
    private final ProcessConfig config;
    // Leader configuration
    private final ProcessConfig leaderConfig;

    // Link to communicate with nodes
    private final Link link;

    // Consensus instance -> Round -> List of prepare messages
    private final MessageBucket prepareMessages;
    // Consensus instance -> Round -> List of commit messages
    private final MessageBucket commitMessages;
    // Consensus instance -> Round -> List of round change messages
    private final MessageBucket roundChangeMessages;

    // Store if already received pre-prepare for a given <consensus, round>
    private final Map<Integer, Map<Integer, Boolean>> receivedPrePrepare = new ConcurrentHashMap<>();
    // Consensus instance information per consensus instance
    private final Map<Integer, InstanceInfo> instanceInfo = new ConcurrentHashMap<>();
    // Current consensus instance
    private final AtomicInteger consensusInstance = new AtomicInteger(0);
    // Last decided consensus instance
    private final AtomicInteger lastDecidedConsensusInstance = new AtomicInteger(0);

    // Ledger (for now, just a list of strings)
    private ArrayList<String> ledger = new ArrayList<String>();

    // Client Service
    private ClientService clientService;

    // Client's accounts map <clientId, Account>
    private Map<String, AccountInfo> accountsInfo = new ConcurrentHashMap<>();

    public NodeService(Link link, ProcessConfig config,
            ProcessConfig leaderConfig, ProcessConfig[] nodesConfig) {

        this.link = link;
        this.config = config;
        this.leaderConfig = leaderConfig;
        this.nodesConfig = nodesConfig;

        this.prepareMessages = new MessageBucket(nodesConfig.length);
        this.commitMessages = new MessageBucket(nodesConfig.length);
        this.roundChangeMessages = new MessageBucket(nodesConfig.length);

    }

    public ProcessConfig getConfig() {
        return this.config;
    }

    public int getConsensusInstance() {
        return this.consensusInstance.get();
    }

    public ArrayList<String> getLedger() {
        return this.ledger;
    }

    public void addClientService(ClientService clientService){
        this.clientService = clientService;
    }

    private boolean isLeader(String id, int instance) {
        int initialLeaderOffset = Integer.valueOf(this.leaderConfig.getNodePosition()) - 1;
        int currentRound = this.instanceInfo.get(instance).getCurrentRound();

        int leaderId = (initialLeaderOffset + currentRound) % this.nodesConfig.length;

        return leaderId == Integer.valueOf(id);
    }

    public void timerReset(RoundTimer roundTimer) {
        System.out.println("Resetting round " + roundTimer.getRoundNumber());

        // get instance
        InstanceInfo instance = this.instanceInfo.get(roundTimer.getConsensusInstance());

        int newRound = instance.incrementRound();

        // we now need to broadcast round change message
        RoundChangeMessage roundChangeMessage = new RoundChangeMessage(instance.getPreparedValue(),
                instance.getPreparedRound()); // TODO: ADD JUSTIFICATION

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE)
                .setConsensusInstance(roundTimer.getConsensusInstance())
                .setRound(newRound)
                .setMessage(roundChangeMessage.toJson())
                .build();

        this.link.broadcast(consensusMessage);
    }

    public ConsensusMessage createConsensusMessage(String value, int instance, int round) {
        PrePrepareMessage prePrepareMessage = new PrePrepareMessage(value);

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE)
                .setConsensusInstance(instance)
                .setRound(round)
                .setMessage(prePrepareMessage.toJson())
                .build();

        return consensusMessage;
    }

    /*
     * Start an instance of consensus for a value
     * Only the current leader will start a consensus instance
     * the remaining nodes only update values.
     *
     * @param inputValue Value to value agreed upon
     */
    public void startConsensus(String value) {

        // Set initial consensus values
        int localConsensusInstance = this.consensusInstance.incrementAndGet();
        InstanceInfo existingConsensus = this.instanceInfo.put(localConsensusInstance, new InstanceInfo(value));

        // If startConsensus was already called for a given round
        if (existingConsensus != null) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Node already started consensus for instance {1}",
                    config.getId(), localConsensusInstance));
            return;
        }

        // Only start a consensus instance if the last one was decided
        // We need to be sure that the previous value has been decided
        while (lastDecidedConsensusInstance.get() < localConsensusInstance - 1) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        InstanceInfo instance = this.instanceInfo.get(localConsensusInstance);
        
        // Leader broadcasts PRE-PREPARE message
        if (this.config.isLeader()) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Starting timer for Consensus Instance {1}, Round {2}",
            config.getId(), consensusInstance, 1));
            
            // Start timer
            
            LOGGER.log(Level.INFO,
            MessageFormat.format("{0} - Node is leader, sending PRE-PREPARE message", config.getId()));
            this.link.broadcast(this.createConsensusMessage(value, localConsensusInstance, instance.getCurrentRound()));
        } else {
            LOGGER.log(Level.INFO,
            MessageFormat.format("{0} - Node is not leader, waiting for PRE-PREPARE message", config.getId()));
        }

        // Start timer
        instance.StartTimerForCurrentRound(SECONDS_TO_RESET_TIMER, localConsensusInstance, this);
    }

    /*
     * Handle pre prepare messages and if the message
     * came from leader and is justified them broadcast prepare
     *
     * @param message Message to be handled
     */
    public void uponPrePrepare(ConsensusMessage message) {

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        String senderId = message.getSenderId();
        int senderMessageId = message.getMessageId();

        PrePrepareMessage prePrepareMessage = message.deserializePrePrepareMessage();

        System.out.println(message);

        String value = prePrepareMessage.getValue();

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PRE-PREPARE message from {1} Consensus Instance {2}, Round {3}",
                        config.getId(), senderId, consensusInstance, round));

        // Verify if pre-prepare was sent by leader
        if (!isLeader(senderId, consensusInstance))
            return;

        // verify if the message is justified
        if (!JustifyPrePrepare(message))
            return;

        // Set instance value
        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(value));

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        receivedPrePrepare.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        if (receivedPrePrepare.get(consensusInstance).put(round, true) != null) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received PRE-PREPARE message for Consensus Instance {1}, Round {2}, "
                                    + "replying again to make sure it reaches the initial sender",
                            config.getId(), consensusInstance, round));
        }

        // Start timer if it is not leader and hadnt received origianl message
        if (!this.config.isLeader() && !this.instanceInfo.get(consensusInstance).hasTimerStartedForCurrentRound()) {

            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Starting timer for Consensus Instance {1}, Round {2}",
                    config.getId(), consensusInstance, round));

            InstanceInfo instance = this.instanceInfo.get(consensusInstance);
            instance.StartTimerForCurrentRound(SECONDS_TO_RESET_TIMER, consensusInstance, this);

        }

        PrepareMessage prepareMessage = new PrepareMessage(prePrepareMessage.getValue());

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PREPARE)
                .setConsensusInstance(consensusInstance)
                .setRound(round)
                .setMessage(prepareMessage.toJson())
                .setReplyTo(senderId)
                .setReplyToMessageId(senderMessageId)
                .build();

        this.link.broadcast(consensusMessage);

    }

    /*
     * Handle prepare messages and if there is a valid quorum broadcast commit
     *
     * @param message Message to be handled
     */
    public synchronized void uponPrepare(ConsensusMessage message) {

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        String senderId = message.getSenderId();

        PrepareMessage prepareMessage = message.deserializePrepareMessage();

        String value = prepareMessage.getValue();

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PREPARE message from {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), senderId, consensusInstance, round));

        // Doesn't add duplicate messages
        prepareMessages.addMessage(message);

        // Set instance values
        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(value));
        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        // Late prepare (consensus already ended for other nodes) only reply to him (as
        // an ACK)
        if (instance.getPreparedRound() >= round) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received PREPARE message for Consensus Instance {1}, Round {2}, "
                                    + "replying again to make sure it reaches the initial sender",
                            config.getId(), consensusInstance, round));

            ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
                    .setConsensusInstance(consensusInstance)
                    .setRound(round)
                    .setReplyTo(senderId)
                    .setReplyToMessageId(message.getMessageId())
                    .setMessage(instance.getCommitMessage().toJson())
                    .build();

            link.send(senderId, m);
            return;
        }

        // Find value with valid quorum
        Optional<String> preparedValue = prepareMessages.hasValidPrepareQuorum(config.getId(), consensusInstance,
                round);
        if (preparedValue.isPresent() && instance.getPreparedRound() < round) {
            instance.setPreparedValue(preparedValue.get());
            instance.setPreparedRound(round);

            // Must reply to prepare message senders
            Collection<ConsensusMessage> sendersMessage = prepareMessages.getMessages(consensusInstance, round)
                    .values();

            CommitMessage c = new CommitMessage(preparedValue.get());
            instance.setCommitMessage(c);

            sendersMessage.forEach(senderMessage -> {
                ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
                        .setConsensusInstance(consensusInstance)
                        .setRound(round)
                        .setReplyTo(senderMessage.getSenderId())
                        .setReplyToMessageId(senderMessage.getMessageId())
                        .setMessage(c.toJson())
                        .build();

                link.send(senderMessage.getSenderId(), m);
            });
        }
    }

    /*
     * Handle commit messages and decide if there is a valid quorum
     *
     * @param message Message to be handled
     */
    public synchronized void uponCommit(ConsensusMessage message) {

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Received COMMIT message from {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), message.getSenderId(), consensusInstance, round));

        commitMessages.addMessage(message);

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        if (instance == null) {
            // Should never happen because only receives commit as a response to a prepare
            // message
            MessageFormat.format(
                    "{0} - CRITICAL: Received COMMIT message from {1}: Consensus Instance {2}, Round {3} BUT NO INSTANCE INFO",
                    config.getId(), message.getSenderId(), consensusInstance, round);
            return;
        }

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        if (instance.getCommittedRound() >= round) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received COMMIT message for Consensus Instance {1}, Round {2}, ignoring",
                            config.getId(), consensusInstance, round));
            return;
        }

        Optional<String> commitValue = commitMessages.hasValidCommitQuorum(config.getId(),
                consensusInstance, round);

        
        if (commitValue.isPresent() && instance.getCommittedRound() < round) {

            // we can now stop the timer for this instance and round
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Stopping timer for Consensus Instance {1}, Round {2}",
                    config.getId(), consensusInstance, round));
            instance.cancelTimer();

            instance = this.instanceInfo.get(consensusInstance);
            instance.setCommittedRound(round);

            String value = commitValue.get();

            // Append value to the ledger (must be synchronized to be thread-safe)
            synchronized (ledger) {

                // Increment size of ledger to accommodate current instance
                ledger.ensureCapacity(consensusInstance);
                while (ledger.size() < consensusInstance - 1) {
                    ledger.add("");
                }

                ledger.add(consensusInstance - 1, value);

            
                LOGGER.log(Level.INFO,
                        MessageFormat.format(
                                "{0} - Current Ledger: {1}",
                                config.getId(), String.join("", ledger)));

                // need to inform clients of ledger change
                clientService.broadcastLedgerUpdate(value, consensusInstance);
            }

            lastDecidedConsensusInstance.getAndIncrement();

            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Decided on Consensus Instance {1}, Round {2}, Successful? {3}",
                            config.getId(), consensusInstance, round, true));
        }
    }

    public synchronized void uponCheckBalance(ConsensusMessage message) {
        System.out.println("Checking balance");
    }

    public synchronized void uponRoundChange(ConsensusMessage message) {

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        String senderId = message.getSenderId();

        RoundChangeMessage roundChangeMessage = message.deserializeRoundChangeMessage();

        int roundProcessHasPreparedTo = roundChangeMessage.getPreparedRound();
        String valueProcessHasPreparedTo = roundChangeMessage.getPreparedValue();

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received ROUND-CHANGE message from {1}: Consensus Instance {2}, Round {3} - Process has prepared to round {4} with value {5}",
                        config.getId(), senderId, consensusInstance, round, roundProcessHasPreparedTo,
                        valueProcessHasPreparedTo));

        // Add message to bucket
        roundChangeMessages.addMessage(message);

        // ------------------- CHECK FOR F + 1 ROUND-CHANGE MESSAGES -------------------

        // Get this instance
        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        // Need to check if we have received f + 1 valid round changes
        Optional<Integer> roundToChangeTo = roundChangeMessages.hasFPlusOneValidRoundChange(consensusInstance,
                instance.getCurrentRound());

        if (roundToChangeTo.isPresent()) {

            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Received f + 1 ROUND-CHANGE messages for Consensus Instance {1}, Round {2} - Changing to round {3}",
                            config.getId(), consensusInstance, round, roundToChangeTo.get()));

            // Change the round
            instance.setCurrentRound(roundToChangeTo.get());

            // Reset timer
            instance.resetTimer(SECONDS_TO_RESET_TIMER, consensusInstance, this);

            // Broadcast new round change message
            RoundChangeMessage newRoundChangeMessage = new RoundChangeMessage(instance.getPreparedValue(),
                    instance.getPreparedRound());

            ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE)
                    .setConsensusInstance(consensusInstance)
                    .setRound(roundToChangeTo.get())
                    .setMessage(newRoundChangeMessage.toJson())
                    .build();

            this.link.broadcast(consensusMessage);

            // ------------------- END OF CHECK FOR F + 1 ROUND-CHANGE MESSAGES
            // -------------------

        }

        // ------------------- CHECK FOR QUORUM OF ROUND CHANGE MESSAGES
        // -------------------

        if (JustifyRoundChange(message) && isLeader(this.config.getId(), consensusInstance)) {

            String value = this.instanceInfo.get(consensusInstance).getInputValue();

            // get highest prepared round (pr, pv)
            String[] highestPrepared = roundChangeMessages.HighestPreparedFromRoundChangeMessages(consensusInstance,
                    round);

            if (Integer.valueOf(highestPrepared[0]) != -1) {
                value = highestPrepared[1];
            }

            // Broadcast PRE-PREPARE message
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Starting timer for Consensus Instance {1}, Round {2}",
                    config.getId(), consensusInstance, instance.getCurrentRound()));
            
            // build pre-prepare message
            PrePrepareMessage prePrepareMessage = new PrePrepareMessage(value);

            ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE)
                    .setConsensusInstance(consensusInstance)
                    .setRound(instance.getCurrentRound())
                    .setMessage(prePrepareMessage.toJson())
                    .build();

            this.link.broadcast(consensusMessage);
        }

        // ------------------- END OF CHECK FOR QUORUM OF ROUND CHANGE MESSAGES
        // -------------------
    }

    private boolean JustifyPrePrepare(ConsensusMessage consensusMessage) {

        // If the message is from the first round, it is justified
        if (consensusMessage.getRound() == 1) {
            return true;
        }

        // The following condition will verify if the condition 'J1' is satisfied

        // Condition J1: All of the messages in the quorum of round change messages
        // have prepared round equal to null (haven't prepared for any round, thus
        // prepared round = -1)
        if (this.VerifyConditionJ1(consensusMessage)) {
            return true;
        }

        // If the condition J1 is not satisfied, we need to verify the condition J2

        if (this.VerifyConditionJ2(consensusMessage)) {
            return true;
        }

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - PrePrepare Message from {1} is not justified for Consensus Instance {2}, Round {3}",
                        config.getId(), consensusMessage.getSenderId(), consensusMessage.getConsensusInstance(),
                        consensusMessage.getRound()));

        return false;

    }

    private boolean JustifyRoundChange(ConsensusMessage consensusMessage) {

        // Return true if either condition J1 or J2 are true

        if (this.VerifyConditionJ1(consensusMessage)) {
            return true;
        }

        if (this.VerifyConditionJ2(consensusMessage)) {
            return true;
        }

        return false;

    }

    private boolean VerifyConditionJ1(ConsensusMessage consensusMessage) {

        // we first need to verify if there exists a quorum of round change messages

        Optional<Integer> preparedRoundChange = roundChangeMessages.hasValidRoundChangeQuorum(
                consensusMessage.getConsensusInstance(),
                consensusMessage.getRound());

        // if there is no quorum return false
        if (!preparedRoundChange.isPresent()) {
            return false;
        }

        // if there is in fact a quorum, we need to verify the prepared round of all
        // messages
        // is equal to -1

        return preparedRoundChange.get() == -1;

    }

    private boolean VerifyConditionJ2(ConsensusMessage consensusMessage) {

        Optional<Integer> preparedRoundChange = roundChangeMessages.hasValidRoundChangeQuorum(
                consensusMessage.getConsensusInstance(),
                consensusMessage.getRound());

        // if there is no quorum return false
        if (!preparedRoundChange.isPresent()) {
            return false;
        }

        // we now need to get the justification piggybacked in the round change message
        Optional<List<List<ConsensusMessage>>> justificationList = roundChangeMessages
                .getJustificationQuorumsFromRoundChange(
                        consensusMessage.getConsensusInstance(), consensusMessage.getRound());

        // if there is no justification list, return false
        if (!justificationList.isPresent()) {
            return false;
        }

        for (List<ConsensusMessage> justification : justificationList.get()) {

            // TODO: Verify all messages in a justification have the same prepared rounds
            // and values
            int preparedRound = justification.get(0).getRound();
            String preparedValue = justification.get(0).deserializeRoundChangeMessage().getPreparedValue();

            String[] highestPrepared = roundChangeMessages.HighestPreparedFromRoundChangeMessages(
                    consensusMessage.getConsensusInstance(), consensusMessage.getRound());

            if (Arrays.equals(new String[] { Integer.toString(preparedRound), preparedValue }, highestPrepared)) {
                return true;
            }

        }

        return false;

    }

    @Override
    public void listen() {
        try {
            // Thread to listen on every request
            new Thread(() -> {
                try {
                    while (true) {
                        Message message = link.receive();

                        // Separate thread to handle each message
                        new Thread(() -> {

                            switch (message.getType()) {

                                case PRE_PREPARE ->
                                    uponPrePrepare((ConsensusMessage) message);

                                case PREPARE ->
                                    uponPrepare((ConsensusMessage) message);

                                case COMMIT ->
                                    uponCommit((ConsensusMessage) message);

                                case ACK ->
                                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received ACK message from {1}",
                                            config.getId(), message.getSenderId()));

                                case IGNORE ->
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received IGNORE message from {1}",
                                                    config.getId(), message.getSenderId()));

                                case ROUND_CHANGE ->
                                    uponRoundChange((ConsensusMessage) message);

                                default ->
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received unknown message from {1}",
                                                    config.getId(), message.getSenderId()));

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

}
