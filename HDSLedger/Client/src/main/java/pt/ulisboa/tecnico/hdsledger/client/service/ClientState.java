package pt.ulisboa.tecnico.hdsledger.client.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.text.MessageFormat;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.PublicKey;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import pt.ulisboa.tecnico.hdsledger.client.enums.RequestSendingPolicy;
import pt.ulisboa.tecnico.hdsledger.client.exceptions.ClientIdDoesntExistException;
import pt.ulisboa.tecnico.hdsledger.client.exceptions.CommandsFilePathNotValidException;
import pt.ulisboa.tecnico.hdsledger.client.exceptions.ErrorCommunicatingWithNode;
import pt.ulisboa.tecnico.hdsledger.client.exceptions.IncorrectSendingPolicyException;
import pt.ulisboa.tecnico.hdsledger.client.models.ClientMessageBucket;
import pt.ulisboa.tecnico.hdsledger.client.models.Command;
import pt.ulisboa.tecnico.hdsledger.common.models.Transaction;
import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.CheckBalanceMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerUpdateMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.TransferMessage;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.cryptolib.CryptoIO;
import pt.ulisboa.tecnico.hdsledger.cryptolib.CryptoUtil;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.enums.TypeOfProcess;

public class ClientState {

    // Id of this client
    private final String clientId;
    // Public key of this client
    private final PublicKey publicKey;
    // Private key of this client
    private final PrivateKey privateKey;
    // Service that will be used for node communication
    private final ClientService clientService;
    // Client configs
    private final ProcessConfig[] clientConfigs;
    // Sending policy to be used
    private final RequestSendingPolicy sendingPolicy;
    // Current ledger
    private ArrayList<String> ledger = new ArrayList<>();
    // Map with received ledger update messages
    private Map<Integer, List<ConsensusMessage>> receivedLedgerUpdates = new HashMap<>();
    // Bucket to store check balance responses
    private final ClientMessageBucket checkBalanceResponseMessageBucket;
    // Bucket to store transfer responses
    private final ClientMessageBucket transferResponseMessageBucket;
    // Number of check balance messages sent
    private long sentCheckBalance = 0;
    // List with check balance request IDs that have already finished
    private List<Long> finishedCheckBalanceRequests = new ArrayList<>();
    // Lock for commandLineInterface (used for displaying information in the command line in the correct order)
    private Object lock;
    // Message counter for nonce generation and for identifying transfer messages
    private long transferMessageCounter = 0;
    // List with transfer request IDs that have already finished
    private List<Long> finishedTransferRequests = new ArrayList<>();
    // List with all outputs from received checkBalance and transfer messages
    private ArrayList<String> allOutputs = new ArrayList<>();

    private static final int MAX_TIME_WAIT = 5; // 10 seconds

    public ClientState(String configPath, String ipAddress, int port, String sendingPolicy, String clientId,
            String commandsFilePath, boolean verboseMode)
            throws IncorrectSendingPolicyException, CommandsFilePathNotValidException, Exception {

        this.clientId = clientId;

        switch (sendingPolicy) {
            case "all":
                this.sendingPolicy = RequestSendingPolicy.ALL;
                break;
            case "majority":
                this.sendingPolicy = RequestSendingPolicy.QUORUM;
                break;
            case "one":
                this.sendingPolicy = RequestSendingPolicy.ONE;
                break;
            default:
                throw new IncorrectSendingPolicyException(sendingPolicy);

        }

        ProcessConfig[] allConfigs = new ProcessConfigBuilder().fromFile(configPath);
        Stream<String> keyPathsStream = Arrays.stream(allConfigs)
                .filter(config -> config.getId().equals(clientId))
                .findAny()
                .map(config -> Stream.of("../" + config.getPublicKeyPath(), "../" + config.getPrivateKeyPath()))
                .orElseThrow(() -> new RuntimeException("Client not found in config file"));

        List<String> keyPaths = keyPathsStream.collect(Collectors.toList());

        try {
            this.publicKey = CryptoIO.readPublicKey(keyPaths.get(0));
            this.privateKey = CryptoIO.readPrivateKey(keyPaths.get(1));
        } catch (Exception e) {
            throw e;
        }

        this.clientConfigs = Arrays.stream(allConfigs)
                .filter(config -> TypeOfProcess.client.equals(config.getType()))
                .toArray(ProcessConfig[]::new);

        this.clientService = new ClientService(allConfigs, clientId, ipAddress, port, this, verboseMode);

        startListening();

        if (commandsFilePath != null) {

            try {
                ExecuteCommands(commandsFilePath);
            } catch (IOException e) {
                throw new CommandsFilePathNotValidException(commandsFilePath);
            }

        }

        int nodeCount = (int) Arrays.stream(allConfigs)
                .filter(config -> TypeOfProcess.node.equals(config.getType()))
                .count();

        
        this.checkBalanceResponseMessageBucket = new ClientMessageBucket(nodeCount);
        this.transferResponseMessageBucket = new ClientMessageBucket(nodeCount);

    }

    public ClientState(String configPath, String ipAddress, int port, String sendingPolicy, String clientId, boolean verboseMode) throws Exception {
        this(configPath, ipAddress, port, sendingPolicy, clientId, null, verboseMode);
    }

    private void startListening() {
        new Thread(() -> {
            clientService.listen();
        }).start();
    }

    public void SendAppendMessage(String content) {

        switch (sendingPolicy) {
            case ALL:

                try {

                    ConsensusMessage appendMessage = new ConsensusMessageBuilder(clientId, Message.Type.APPEND)
                            .setMessage(new AppendMessage(content).toJson())
                            .build();

                    clientService.broadcast(appendMessage);

                } catch (ErrorCommunicatingWithNode e) {
                    System.out.println(e.getMessage());
                }

                break;

            default:
                throw new RuntimeException("Sending policy not implemented yet.");
        }
    }

    public void SendCheckBalanceMessage(Object lock) {

        try {
            this.lock = lock;

            ConsensusMessage checkBalanceMessage = new ConsensusMessageBuilder(clientId, Message.Type.CHECK_BALANCE)
                    .setMessage(new CheckBalanceMessage(publicKey, ++this.sentCheckBalance).toJson())
                    .build();

            clientService.broadcast(checkBalanceMessage);


        } catch (ErrorCommunicatingWithNode e) {
            System.out.println(e.getMessage());
        }
    }

    public void SendTransferMessage(String receiverId, double amount, Object lock) throws ClientIdDoesntExistException{
        this.lock = lock;

        System.out.println("Transfering " + amount + " coins to " + receiverId + "...");

        // Find clientId in clientConfig and get publicKey
        // If not found, throw ClientIdDoesntExistException

        // Generate nonce
        byte[] nonce = CryptoUtil.generateNonce(++transferMessageCounter);
        
        Transaction transaction = new Transaction(this.privateKey, publicKey, getPublicKeyOfClient(receiverId), amount, Base64.getEncoder().encodeToString(nonce));

        try {
            ConsensusMessage transferMessage = new ConsensusMessageBuilder(clientId, Message.Type.TRANSFER)
                    .setMessage(new TransferMessage(transaction, ++this.transferMessageCounter).toJson())
                    .build();

            clientService.broadcast(transferMessage);

        } catch (ErrorCommunicatingWithNode e) {
            System.out.println(e.getMessage());
        }
    }
    
    private PublicKey getPublicKeyOfClient(String clientId) throws ClientIdDoesntExistException{

        return  Arrays.stream(clientConfigs)
                .filter(config -> config.getId().equals(clientId))
                .findAny()
                .map(config -> {
                    try {
                        return CryptoIO.readPublicKey("../" + config.getPublicKeyPath());
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElseThrow(() -> new ClientIdDoesntExistException(clientId));
        
    }

    protected void ledgerUpdate(ConsensusMessage consensusMessage) {
        // TODO
        int consensusInstance = consensusMessage.getConsensusInstance();
        String senderId = consensusMessage.getSenderId();

        synchronized (receivedLedgerUpdates) {
            if (!receivedLedgerUpdates.containsKey(consensusInstance)) {
                receivedLedgerUpdates.put(consensusInstance, new ArrayList<>(List.of(consensusMessage)));
            } else {
                List<ConsensusMessage> instanceReceivedMessages = receivedLedgerUpdates.get(consensusInstance);

                boolean haventReceivedFromThisNode = instanceReceivedMessages.stream()
                        .filter(ledgerUpdate -> {
                            return ledgerUpdate.getSenderId().equals(senderId);
                        })
                        .findFirst()
                        .isEmpty();

                if (haventReceivedFromThisNode) {
                    instanceReceivedMessages.add(consensusMessage);

                } else {
                    return;
                }
            }

            List<ConsensusMessage> instanceReceivedMessages = receivedLedgerUpdates.get(consensusInstance);
            if (instanceReceivedMessages.size() == clientService.getQuorumSize()) {
                // We have a quorum
                // We can now update the ledger
                LedgerUpdateMessage ledgerUpdateMessage = consensusMessage.deserializeLedgerUpdateMessage();
                String newValue = ledgerUpdateMessage.getValue();

                ledger.ensureCapacity(consensusInstance);
                while (ledger.size() < consensusInstance - 1) {
                    ledger.add("");
                }

                ledger.add(consensusInstance - 1, newValue);

                System.out.println(
                        MessageFormat.format(
                                "{0} - Ledger updated with: {1} at instance {2}",
                                clientId, newValue, consensusInstance));

                System.out.println(
                        MessageFormat.format("{0} - New ledger: {1}",
                                clientId, String.join("", ledger)));
            }

        }

    }

    protected void uponCheckBalanceResponse(ConsensusMessage consensusMessage) {
        

        this.checkBalanceResponseMessageBucket.addMessage(consensusMessage);

        long replyToCheckBalanceRequestId = consensusMessage.deserializeCheckBalanceResponseMessage().getResponseToCheckBalanceRequestId();

        Optional<Double> balance = this.checkBalanceResponseMessageBucket.hasValidCheckBalanceResponseQuorum(replyToCheckBalanceRequestId);

        if(balance.isPresent() && !finishedCheckBalanceRequests.contains(replyToCheckBalanceRequestId)){
            System.out.println(MessageFormat.format("{0} - Balance: {1}", clientId, balance.get()));

            // Save the message inside the list
            String output = clientId + " - Balance: " + String.valueOf(balance.get()) + "\n";
            allOutputs.add(output);

            finishedCheckBalanceRequests.add(replyToCheckBalanceRequestId);
            synchronized(lock){
                lock.notify();
            }
            lock = null;
        }

    }

    protected void uponTransferResponse(ConsensusMessage consensusMessage) {
        this.transferResponseMessageBucket.addMessage(consensusMessage);

        long replyToTransferRequestId = consensusMessage.deserializeTransferResponseMessage().getResponseToMessageId();

        Optional<Boolean> success = this.transferResponseMessageBucket.hasValidTransferResponseQuorum(replyToTransferRequestId);

        if(success.isPresent() && !finishedTransferRequests.contains(replyToTransferRequestId)){
            if(success.get()){
                System.out.println("Server replied with success.");

                // add to the list of outputs
                allOutputs.add("Server replied with success.\n");
            }else{
                System.out.println("Server replied with the following error: " + transferResponseMessageBucket.getTransferError(replyToTransferRequestId));

                // add to the list of outputs
                allOutputs.add("Server replied with the following error: " + transferResponseMessageBucket.getTransferError(replyToTransferRequestId) + "\n");
            }
            finishedTransferRequests.add(replyToTransferRequestId);
            synchronized(lock){
                lock.notify();
            }
            lock = null;
        }
    }

    public void ExecuteCommands(String commandsFilePath) throws IOException, InterruptedException {

        try {
            List<Command> commands = ImportCommandsFile(commandsFilePath);

            System.out.println(commands.size() + " commands found.");

            for (Command command : commands) {
                System.out.println(command.getCommand());
                switch (command.getCommand()) {
                    case "append":
                        SendAppendMessage((String) command.getArguments().get(0));
                        break;
                    case "sleep":
                        try {
                            Double sleepTimeDouble = (Double) command.getArguments().get(0);
                            int sleepTime = sleepTimeDouble.intValue() * 1000;
                            System.out.println("Sleeping for " + sleepTime + "ms");
                            Thread.sleep(sleepTime);

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        break;

                    case "check_balance":
                        Object lock_balance = new Object();
                        SendCheckBalanceMessage(lock_balance);
                        WaitForLock(lock_balance);

                        break;

                    case "transfer":
                        Object lock_transfer = new Object();
                        String clientID = (String) command.getArguments().get(0);

                        try {
                            Double amount = (Double) command.getArguments().get(1);
                            if(amount <= 0){
                                allOutputs.add("Amount must be a positive integer.\n");
                            }
                            
                            else{
                                SendTransferMessage(clientID, amount, lock_transfer);
                            }

                        } catch (NumberFormatException e) {
                            allOutputs.add("Amount must be a positive integer.\n");

                        } catch (ClientIdDoesntExistException e){
                            allOutputs.add("Client with id " + clientID + " doesn't exist.\n");
                            break;
                        }

                        WaitForLock(lock_transfer);
                        break;

                    case "write_ledger":
                        WriteLedgerToFile((String) command.getArguments().get(0));
                        break;
                    
                    case "write_output":
                        WriteOutputToFile((String) command.getArguments().get(0));
                        break;
                    
                    default:
                        System.out.println("Unknown command: " + command.getCommand());
                        break;
                }
            }
        } catch (Exception e) {
            throw e;
        }

    }

    private List<Command> ImportCommandsFile(String commandsFilePath) throws IOException {

        // Create a Gson object
        Gson gson = new Gson();

        // Define the type of the list using TypeToken
        Type commandListType = new TypeToken<List<Command>>() {
        }.getType();

        try {
            // Read the json into a string
            String json = Files.readString(Paths.get(commandsFilePath));

            // Convert the JSON string to a list of Command objects
            return gson.fromJson(json, commandListType);

        } catch (IOException e) {
            throw e;
        }

    }

    private void WriteLedgerToFile(String ledgerFilePath) throws IOException {

        String ledgerString = String.join("", ledger);
        BufferedWriter writer = new BufferedWriter(new FileWriter(ledgerFilePath));

        writer.write(ledgerString);

        writer.close();
    }

    // Writes all the saved outputs into the desired file
    private void WriteOutputToFile(String outputFilePath) throws IOException {

        BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath));

        for (String output : allOutputs){
            String outputString = String.join("", output);
            writer.write(outputString);
        }

        writer.close();
    }

    private static void WaitForLock(Object lock){
        // Start a separate thread for the timer
        Thread timerThread = new Thread(() -> {
            try {
                // Sleep for the desired duration
                Thread.sleep(MAX_TIME_WAIT * 1000);
                synchronized (lock) {
                    // Interrupt the waiting thread after the timeout
                    System.out.println("Timeout: Could not get response in time.");
                    lock.notify(); // Notify the waiting thread
                    
                }
            } catch (InterruptedException e) {
                // Timer thread interrupted, which means the waiting thread was notified before the timeout
            }
        });
        timerThread.start();
    
        synchronized (lock) {
            try {
                lock.wait(); // Wait for the response or timeout
            } catch (InterruptedException e) {
                System.out.println("Waiting thread interrupted");
            } finally {
                // Regardless of whether the waiting thread was interrupted or not,
                // interrupt the timer thread to stop it
                timerThread.interrupt();
            }
        }
    }


}
