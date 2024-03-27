package pt.ulisboa.tecnico.hdsledger.client.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.text.MessageFormat;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputFilter.Config;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
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
import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.CheckBalanceMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerUpdateMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.cryptolib.CryptoIO;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.enums.TypeOfProcess;

public class ClientState {

    // Id of this client
    private final String clientId;
    // Self config
    private final PublicKey publicKey;
    // Service that will be used for node communication
    private final ClientService clientService;
    // Sending policy to be used
    private final RequestSendingPolicy sendingPolicy;
    // Current ledger
    private ArrayList<String> ledger = new ArrayList<>();
    // Map with received ledger update messages
    private Map<Integer, List<ConsensusMessage>> receivedLedgerUpdates = new HashMap<>();
    // Bucket to store check balance responses
    private final ClientMessageBucket checkBalanceResponseMessageBucket;
    // Number of check balance messages sent
    private int sentCheckBalance = 0;
    // List with check balance request IDs that have already finished
    private List<Integer> finishedCheckBalanceRequests = new ArrayList<>();
    // Lock for commandLineInterface (used for displaying information in the command line in the correct order)
    private Object lock;

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
        String publicKeyPath = "../" + Arrays.stream(allConfigs)
                .filter(config -> config.getId().equals(clientId))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Client not found in config file"))
                .getPublicKeyPath();

        try {
            this.publicKey = CryptoIO.readPublicKey(publicKeyPath);
        } catch (Exception e) {
            throw e;
        }

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

    public void SendTransferMessage(String receiverId, int amount) throws ClientIdDoesntExistException{
        System.out.println("Transfering " + amount + " coins to " + receiverId);
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

        int replyToCheckBalanceRequestId = consensusMessage.deserializeCheckBalanceResponseMessage().getResponseToCheckBalanceRequestId();

        Optional<Integer> balance = this.checkBalanceResponseMessageBucket.hasValidCheckBalanceResponseQuorum(replyToCheckBalanceRequestId);

        if(balance.isPresent() && !finishedCheckBalanceRequests.contains(replyToCheckBalanceRequestId)){
            System.out.println(MessageFormat.format("{0} - Balance: {1}", clientId, balance.get()));
            finishedCheckBalanceRequests.add(replyToCheckBalanceRequestId);
            synchronized(lock){
                lock.notify();
            }
            lock = null;
        }

    }

    public void ExecuteCommands(String commandsFilePath) throws IOException {

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
                    case "write_ledger":
                        WriteLedgerToFile((String) command.getArguments().get(0));
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

}
