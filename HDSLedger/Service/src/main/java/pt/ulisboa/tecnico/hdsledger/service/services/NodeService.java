package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.common.models.ErrorType;
import pt.ulisboa.tecnico.hdsledger.common.models.Transaction;
import pt.ulisboa.tecnico.hdsledger.communication.CheckBalanceMessage;
import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.PrePrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.RoundChangeMessage;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.cryptolib.CryptoIO;
import pt.ulisboa.tecnico.hdsledger.cryptolib.CryptoUtil;
import pt.ulisboa.tecnico.hdsledger.service.models.AccountInfo;
import pt.ulisboa.tecnico.hdsledger.service.models.Block;
import pt.ulisboa.tecnico.hdsledger.service.models.InstanceInfo;
import pt.ulisboa.tecnico.hdsledger.service.models.MessageBucket;
import pt.ulisboa.tecnico.hdsledger.service.models.RoundTimer;
import pt.ulisboa.tecnico.hdsledger.service.models.builder.BlockBuilder;
import pt.ulisboa.tecnico.hdsledger.service.models.exceptions.BlockIsFullException;
import pt.ulisboa.tecnico.hdsledger.service.models.util.ByzantineUtils;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSSException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ServiceConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.Util;

import java.security.PrivateKey;

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

    // Client Service
    private ClientService clientService;

    // Client's accounts map <clientId, Account>
    private Map<String, AccountInfo> accountsInfo = new ConcurrentHashMap<>();

    // Service Configurations
    private final ServiceConfig serviceConfig;

    // Current block to append incoming transactions
    private BlockBuilder currentBlock;

    // Ledger
    private ArrayList<Block> ledger = new ArrayList<Block>();

    // Store nonces to avoid replay attacks
    private Map<String, List<String>> nonceListOfRequestsSentByClients = new ConcurrentHashMap<>(); // <clientId, nonceInBase64>

    // Behavior type
    private final int behaviourType; // behavior type description in "Report.md"

    public NodeService(Link link, ProcessConfig config,
            ProcessConfig leaderConfig, ProcessConfig[] nodesConfig, ProcessConfig[] clientsConfigs,
            ServiceConfig serviceConfig) {

        this.link = link;
        this.config = config;
        this.leaderConfig = leaderConfig;
        this.nodesConfig = nodesConfig;

        this.prepareMessages = new MessageBucket(nodesConfig.length);
        this.commitMessages = new MessageBucket(nodesConfig.length);
        this.roundChangeMessages = new MessageBucket(nodesConfig.length);
        this.serviceConfig = serviceConfig;
        this.behaviourType = config.getBehaviourType();

        InitializeAccounts(clientsConfigs);

        // Start initialization of block to be built
        ResetCurrentBlockBuilder();

        InitializeNonceArray(clientsConfigs);
    }

    private PrivateKey getPrivateKey() {
        try {
            return CryptoIO.readPrivateKey("../" + this.config.getPrivateKeyPath());
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public ProcessConfig getConfig() {
        return this.config;
    }

    public int getBehaviourType() {
        return this.behaviourType;
    }

    public int getConsensusInstance() {
        return this.consensusInstance.get();
    }

    public void addClientService(ClientService clientService) {
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

    private void InitializeAccounts(ProcessConfig[] clientsConfigs) {

        // First we create the accounts for the clients
        Arrays.stream(clientsConfigs)
                .forEach(clientConfig -> {
                    try {
                        CreateAccount(clientConfig.getId(), this.serviceConfig.getInitialAccountBalance(),
                                "../" + clientConfig.getPublicKeyPath());
                    } catch (HDSSException e) {
                        throw e;
                    }
                });

        // Now we create accounts for each of the nodes (this will be used for
        // transaction's fees)

        Arrays.stream(this.nodesConfig)
                .forEach(nodeConfig -> {
                    try {
                        CreateAccount(nodeConfig.getId(), 0, "../" + nodeConfig.getPublicKeyPath());
                    } catch (HDSSException e) {
                        throw e;
                    }
                });

    }

    private void InitializeNonceArray(ProcessConfig[] clientsConfigs) {
        // Initialize nonce array
        Arrays.stream(clientsConfigs)
                .forEach(clientConfig -> {
                    this.nonceListOfRequestsSentByClients.put(clientConfig.getId(), new ArrayList<String>());
                });
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
                        
        if(!VerifyBlockInPrePrepare(new Gson().fromJson(value, Block.class))){
            return;
        }

        if(this.behaviourType == 2){ // Try to change the amount in the transaction
            
            value = ByzantineUtils.DoubleAmountOfTransaction(value);

        }else if (this.behaviourType == 3) { // Try to change the amount in the transaction
            
            value = ByzantineUtils.ChangeFeeToZero(value);

        }

        // Set instance value
        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(value));

        // Verify if pre-prepare was sent by leader
        if (!isLeader(senderId, consensusInstance))
            return;

        // verify if the message is justified
        if (!JustifyPrePrepare(message))
            return;

        // Do all verifications that correspond to the block information (signatures of transactions, block hash, etc)


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

        PrepareMessage prepareMessage;

        if(this.behaviourType == 2 || this.behaviourType == 3){ // Try to change the amount in the transaction
            
            prepareMessage = new PrepareMessage(value);

            
            
        }else{

            prepareMessage = new PrepareMessage(prePrepareMessage.getValue());

        }


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

                        
        if(this.behaviourType == 2){ // Try to change the amount in the transaction
            
            value = ByzantineUtils.DoubleAmountOfTransaction(value);
            
        }else if (this.behaviourType == 3) { // Try to change the amount in the transaction
            
            value = ByzantineUtils.ChangeFeeToZero(value);

        }else{ // Normal behavior
            
            if (!VerifyBlockInPrepareAndCommit(new Gson().fromJson(value, Block.class))) {
                return;
            }
        }

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

            CommitMessage c;

            if(this.behaviourType == 2 || this.behaviourType == 3){ // Try to change the amount in the transaction
                
                c = new CommitMessage(value);
                instance.setCommitMessage(c);
                
            }else{
                c = new CommitMessage(preparedValue.get());
                instance.setCommitMessage(c);

            }


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

        CommitMessage commitMessage = message.deserializeCommitMessage();
        String value = commitMessage.getValue();

        LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Received COMMIT message from {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), message.getSenderId(), consensusInstance, round));

        if(this.behaviourType == 2){ // Try to change the amount in the transaction
            
            value = ByzantineUtils.DoubleAmountOfTransaction(value);
            System.out.println(value);
        }else{ //normal behavior
            
            // Before adding the message to the bucket, we need to check if the block in the message is valid
            if (!VerifyBlockInPrepareAndCommit(new Gson().fromJson(value, Block.class))) {
                return;
            }
        }


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

            String blockInJsonFomrat = commitValue.get();

            Block block = new Gson().fromJson(blockInJsonFomrat, Block.class);

            // Append block to ledger
            this.HandleBlockAppend(block);

            lastDecidedConsensusInstance.getAndIncrement();

            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Decided on Consensus Instance {1}, Round {2}, Successful? {3}",
                            config.getId(), consensusInstance, round, true));
        }
    }

    // TODO: Remove senderPublicKeyPath
    public synchronized double uponCheckBalance(ConsensusMessage message) throws HDSSException {

        String clientId = message.getSenderId();

        CheckBalanceMessage checkBalanceMessage = message.deserializeCheckBalanceMessage();

        // get public key path of the sender
        if (!this.accountsInfo.containsKey(clientId)) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Could not find account for sender {1}",
                    this.config.getId(), clientId));
            throw new HDSSException(ErrorMessage.AccountNotFound);
        }

        String senderPublicKeyPath = this.accountsInfo.get(clientId).getPublicKeyFilename();

        try {
            if (!CryptoUtil.verifyPublicKey(senderPublicKeyPath, checkBalanceMessage.getPublicKey())) {
                LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Could not verify public key for sender {1}",
                        this.config.getId(), clientId));
                throw new HDSSException(ErrorMessage.InvalidPublicKey);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Could not verify public key for sender {1}",
                    this.config.getId(), clientId));
            throw new HDSSException(ErrorMessage.InvalidPublicKey);
        }

        // get balance
        synchronized (accountsInfo) {
            AccountInfo account = accountsInfo.get(clientId);

            return account.getBalance();

        }

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

        if(instance == null){
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Instance info not found for Consensus Instance {1}; ignoring ROUND-CHANGE message",
                    config.getId(), consensusInstance));
            return;
        }

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

    public void uponTransfer(Transaction transaction, String senderId, String receiverId, long messageId) {
        System.out.println(transaction);

        synchronized (currentBlock) {
            // Check if senderID account has enough balance according to transactions
            // already present in this block
            // (that is, we need to take accountsInfo, create a temporary accountInfo with
            // the transactions present in
            // the current block applied, and check if the sender has enough balance to make
            // the transaction)

            AccountInfo tempAccountOfSender = new AccountInfo(this.accountsInfo.get(senderId).getAssociatedClientId(),
                    this.accountsInfo.get(senderId).getBalance(),
                    this.accountsInfo.get(senderId).getPublicKeyFilename());

            double balanceIfCurrentBlockWasApplied = 0;
            try {
                balanceIfCurrentBlockWasApplied = ApplyChangesToAccountOfCurrentBlock(tempAccountOfSender);
            } catch (Exception e) {
                e.printStackTrace();
                this.clientService.SendTransferErrorMessage(senderId, messageId, ErrorType.UNKNOWN_ERROR);
                return;
            }

            if (balanceIfCurrentBlockWasApplied < transaction.getAmount()) {
                LOGGER.log(Level.SEVERE,
                        MessageFormat.format("{0} - Sender {1} does not have enough balance to transfer {2} to {3}",
                                this.config.getId(), senderId, transaction.getAmount(), receiverId));

                this.clientService.SendTransferErrorMessage(senderId, messageId, ErrorType.INSUFFICIENT_BALANCE);
                return;
            }
            
            // Verify if nonce was used before
            if (this.nonceListOfRequestsSentByClients.get(senderId).contains(transaction.getNonceInBase64())) {
                LOGGER.log(Level.SEVERE,
                        MessageFormat.format("{0} - Nonce {1} was already used by sender {2}",
                                this.config.getId(), transaction.getNonceInBase64(), senderId));

                this.clientService.SendTransferErrorMessage(senderId, messageId, ErrorType.NONCE_ALREADY_USED);
                return;
            }

            // All the checks passed; we now need to add the transaction to the current
            // block
            // Last thing, set fee

            if(this.behaviourType == 4){ // Try to change the fee to 100% of the amount
                
                transaction.setFee(1);

            }else{

                transaction.setFee(this.serviceConfig.getTransactionFee());

            }

            // And add nonce to the list of nonces
            this.nonceListOfRequestsSentByClients.get(senderId).add(transaction.getNonceInBase64());

            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Adding transaction to block: {1}",
                    this.config.getId(), transaction.getTransactionIdInHex()));

            try {

                boolean isFull = currentBlock.addTransactionAndCheckIfFull(transaction);
                System.out.println(currentBlock);

                if (isFull) {
                    // Block is full, we can start consensus on this block
                    Block newBlock = currentBlock.build(this.config.getId(), this.getPrivateKey());
                    
                    //Start consensus on a new thread
                    new Thread(() -> {
                        this.startConsensus(newBlock.toJson());
                    }).start();

                    // Reset current block
                    ResetCurrentBlockBuilder();
                }

            } catch (BlockIsFullException e) {
                // TODO: handle exception
            } catch (Exception e) {
                e.printStackTrace();
                this.clientService.SendTransferErrorMessage(senderId, messageId, ErrorType.UNKNOWN_ERROR);
            }

            // Send success message to client
            this.clientService.SendTransferSuccessMessage(senderId, messageId);



        }
    }

    /**
     * This function should be called whenever an agreement about which block to be
     * appended is reached (DECIDE part of the iBFT protocol).
     */
    private void HandleBlockAppend(Block block) {

        synchronized (ledger) {
            // first we need to process the transactions in the block
            for (Transaction transaction : block.getTransactions()) {
                try {
                    ProcessTransaction(transaction);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // Now need to pay the fee to the node that created the block

            ProcessFeePayment(block);

            this.ledger.add(block);
        }

        LOGGER.log(Level.INFO, MessageFormat.format("{0} - Block appended to ledger: {1}",
                this.config.getId(), block));

    }

    /**
     * This function will apply all transactions in the current block to the
     * accountInfo, and return <b> the new "would be" balance. </b>
     * 
     * @param accountInfo
     * @return The new balance of the account if all transactions in the current
     *         block were applied to the accountInfo
     */
    private double ApplyChangesToAccountOfCurrentBlock(AccountInfo accountInfo) throws Exception {
        // Apply all transactions in the current block to the accountInfo

        Block currentBlockInstance = this.currentBlock.getInstance();
        double balanceOfAccount = accountInfo.getBalance();
        String pathToPublicKey = accountInfo.getPublicKeyFilename();

        try {

            for (Transaction transaction : currentBlockInstance.getTransactions()) {

                if (transaction != null) {

                    if (CryptoUtil.verifyPublicKey(pathToPublicKey, transaction.getSenderPublicKey())) { // this means that the sender of
                                                                                                         // the transaction was the account holder
                        balanceOfAccount -= transaction.getAmount();

                    }

                    if (CryptoUtil.verifyPublicKey(pathToPublicKey, transaction.getReceiverPublicKey())) { // this means that the receiver of this
                                                                                                           // transaction was the account holder
                        balanceOfAccount += (transaction.getAmount() - transaction.getFee());
                    }

                }

            }

        } catch (Exception e) {
            throw e;
        }

        return balanceOfAccount;

    }

    private void ProcessTransaction(Transaction transaction) throws Exception {

        synchronized (accountsInfo) {

            PublicKey publicKeyOfSender;
            PublicKey publicKeyOfReceiver;

            try {
                publicKeyOfSender = transaction.getSenderPublicKey();
                publicKeyOfReceiver = transaction.getReceiverPublicKey();

            } catch (Exception e) {
                throw e;
            }

            // Get sender ID
            String senderId = accountsInfo.entrySet().stream()
                    .filter(entry -> {
                        return CryptoUtil.verifyPublicKey(entry.getValue().getPublicKeyFilename(), publicKeyOfSender);
                    })
                    .map(Map.Entry::getKey) // Extract the key if a matching value is found
                    .findFirst() // Get the first matching key, if any
                    .orElse(null); // Return null if no matching key is found

            // Get receiver ID
            String receiverId = accountsInfo.entrySet().stream()
                    .filter(entry -> {
                        return CryptoUtil.verifyPublicKey(entry.getValue().getPublicKeyFilename(), publicKeyOfReceiver);
                    })
                    .map(Map.Entry::getKey) // Extract the key if a matching value is found
                    .findFirst() // Get the first matching key, if any
                    .orElse(null); // Return null if no matching key is found

            if (senderId == null || receiverId == null) {
                // TODO: idk
            }

            // TODO: implement fee
            accountsInfo.get(senderId).decreaseBalance(transaction.getAmount());
            accountsInfo.get(receiverId).increaseBalance(transaction.getAmount() - transaction.getFee());

        }

    }

    private void ProcessFeePayment(Block block) {
        double totalFees = 0;

        for (Transaction transaction : block.getTransactions()) {
            totalFees += transaction.getFee();
        }

        accountsInfo.get(block.getNodeIdOfFeeReceiver()).increaseBalance(totalFees);
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

    private boolean VerifyBlockInPrePrepare(Block block) {
        try {

            //Create copy of accountInfo
            Map<String, AccountInfo> tempAccountsInfo = new ConcurrentHashMap<>();
            for(Map.Entry<String, AccountInfo> entry : accountsInfo.entrySet()){
                tempAccountsInfo.put(entry.getKey(), new AccountInfo(entry.getValue().getAssociatedClientId(), entry.getValue().getBalance(), entry.getValue().getPublicKeyFilename()));
            }

            //Also see if sum of fees in transactions is equal to total fees
            double sumOfTransactions = 0;
        
            // First we verify the signatures of all transactions

            for (Transaction transaction : block.getTransactions()) {

                //first verify if transactionId = hash(sender_public_key + receiver_public_key + amount + nonce)

                if(!VerifyTransactionHash(transaction)){
                    LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Rejected block {1} because transaction {2} has an invalid transactionId",
                            this.config.getId(), Util.bytesToHex(block.getHash()), transaction.getTransactionIdInHex()));
    
                    return false;
                }

                // Verify if fee is correct
                if(transaction.getFee() != (this.serviceConfig.getTransactionFee() * transaction.getAmount())){
                    LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Rejected block {1} because transaction {2} has an invalid fee",
                        this.config.getId(), Util.bytesToHex(block.getHash()), transaction.getTransactionIdInHex()));
                    return false;
                }

                sumOfTransactions += transaction.getFee();
                
                // Verify if the nonce has already been used

                for(Block b : ledger){
                    for(Transaction t : b.getTransactions()){
                        if(t.getSenderPublicKeyBase64().equals(transaction.getSenderPublicKeyBase64())){
                            if(t.getNonceInBase64().equals(transaction.getNonceInBase64())){
                                LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Rejected block {1} because transaction {2} has a nonce that was already used",
                                    this.config.getId(), Util.bytesToHex(block.getHash()), transaction.getTransactionIdInHex()));
                                return false;
                            }
                        }
                    }
                }

                // Now verify signature
                if (!VerifyTransactionSignature(transaction)) {
                    LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Rejected block {1} because transaction {2} has an invalid signature",
                        this.config.getId(), Util.bytesToHex(block.getHash()), transaction.getTransactionIdInHex()));
                    return false;
                }

                // Now we need to apply the transaction to the temporary accountInfo;
                // In the end we will verify if any account has a negative balance

                tempAccountsInfo.get(GetClientIdCorrespondingToPublicKey(transaction.getSenderPublicKey())).decreaseBalance(transaction.getAmount());
                tempAccountsInfo.get(GetClientIdCorrespondingToPublicKey(transaction.getReceiverPublicKey())).increaseBalance(transaction.getAmount() - transaction.getFee());


            }

            for(Map.Entry<String, AccountInfo> entry : tempAccountsInfo.entrySet()){
                if(entry.getValue().getBalance() < 0){
                    LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Rejected block {1} because account {2} has a negative balance",
                        this.config.getId(), Util.bytesToHex(block.getHash()), entry.getKey()));
                    return false;
                }
            }

            // Now we need to verify if the sum of all transactions is equal to the total fees
            if(sumOfTransactions != block.getTotalFees()){
                LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Rejected block {1} because the sum of all transactions is not equal to the total fees",
                        this.config.getId(), Util.bytesToHex(block.getHash())));
                return false;
            }
                
            // Lastly, verify block hash and signature

            if(!VerifyBlockHash(block)){
                LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Rejected block {1} because it has an invalid hash",
                        this.config.getId(), Util.bytesToHex(block.getHash())));
                return false;
            }

            if(!VerifyBlockSignature(block, getPublicKeyCorrespondingToClientId(block.getNodeIdOfFeeReceiver()))){
                LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Rejected block {1} because it has an invalid signature",
                        this.config.getId(), Util.bytesToHex(block.getHash())));
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private boolean VerifyBlockInPrepareAndCommit(Block block){
        // In prepare and commit, we do not need to make all verifications
        // We only need to:
        //  - For each transaction:
        //      - Verify hash
        //      - Verify signature
        //  - For the block:
        //      - Verify hash
        //      - Verify signature

        for(Transaction transaction : block.getTransactions()){
            try {
                if(!VerifyTransactionHash(transaction)){
                    LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Rejected block {1} because transaction {2} has an invalid transactionId",
                            this.config.getId(), Util.bytesToHex(block.getHash()), transaction.getTransactionIdInHex()));
                    return false;
                }

                if(!VerifyTransactionSignature(transaction)){
                    LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Rejected block {1} because transaction {2} has an invalid signature",
                            this.config.getId(), Util.bytesToHex(block.getHash()), transaction.getTransactionIdInHex()));
                    return false;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        if(!VerifyBlockHash(block)){
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Rejected block {1} because it has an invalid hash",
                    this.config.getId(), Util.bytesToHex(block.getHash())));
            return false;
        }

        if(!VerifyBlockSignature(block, getPublicKeyCorrespondingToClientId(block.getNodeIdOfFeeReceiver()))){
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Rejected block {1} because it has an invalid signature",
                    this.config.getId(), Util.bytesToHex(block.getHash())));
            return false;
        }

        return true;

    }

    private boolean VerifyBlockHash(Block block){
        try {
            byte[] blockHash = block.getHash();
            byte[] calculatedBlockHash = block.generateHash();

            return Arrays.equals(blockHash, calculatedBlockHash);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean VerifyBlockSignature(Block block, PublicKey publicKeyOfSigner){
        try {
            return CryptoUtil.verifySignature(block.getHash(), block.getSignature(), publicKeyOfSigner);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean VerifyTransactionHash(Transaction transaction) throws Exception{
        try {

            byte[] transactionToCompare = Transaction.CreateTransactionId(transaction.getSenderPublicKey(),
                                                                                transaction.getReceiverPublicKey(), 
                                                                                transaction.getAmount(),
                                                                                transaction.getNonceInBase64());
    
            return Arrays.equals(transactionToCompare, transaction.getRawTransactionId());
            
        } catch (Exception e) {
            throw e;
        }
    }

    private boolean VerifyTransactionSignature(Transaction transaction) throws Exception{
        try {
            return CryptoUtil.verifySignature(transaction.getRawTransactionId(), transaction.getSignature(), transaction.getSenderPublicKey());
        } catch (Exception e) {
            throw e;
        }
    }

    private void CreateAccount(String clientId, int initialBalance, String clientPublicKeyPath) {

        // If account already exists throw error
        if (this.accountsInfo.containsKey(clientId)) {
            throw new HDSSException(ErrorMessage.DuplicateClientInConfig);
        }

        this.accountsInfo.put(clientId, new AccountInfo(clientId, initialBalance, clientPublicKeyPath));
    }

    private void ResetCurrentBlockBuilder() {
        this.currentBlock = new BlockBuilder(ledger.size(), serviceConfig.getNumTransactionsInBlock());
    }

    private String GetClientIdCorrespondingToPublicKey(PublicKey publicKey) {
        return this.accountsInfo.entrySet().stream()
                .filter(entry -> CryptoUtil.verifyPublicKey(entry.getValue().getPublicKeyFilename(), publicKey))
                .map(Map.Entry::getKey) // Extract the key if a matching value is found
                .findFirst() // Get the first matching key, if any
                .orElse(null); // Return null if no matching key is found
    }

    private PublicKey getPublicKeyCorrespondingToClientId(String clientId) {
        try {
            return CryptoIO.readPublicKey(this.accountsInfo.get(clientId).getPublicKeyFilename());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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
