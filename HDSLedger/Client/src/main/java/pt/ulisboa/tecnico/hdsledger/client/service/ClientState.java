package pt.ulisboa.tecnico.hdsledger.client.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.text.MessageFormat;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import pt.ulisboa.tecnico.hdsledger.client.enums.RequestSendingPolicy;
import pt.ulisboa.tecnico.hdsledger.client.exceptions.CommandsFilePathNotValidException;
import pt.ulisboa.tecnico.hdsledger.client.exceptions.ErrorCommunicatingWithNode;
import pt.ulisboa.tecnico.hdsledger.client.exceptions.IncorrectSendingPolicyException;
import pt.ulisboa.tecnico.hdsledger.client.models.Command;
import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerUpdateMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;

public class ClientState {

    // Id of this client
    private final String clientId;
    // Service that will be used for node communication
    private final ClientService clientService;
    // Sending policy to be used
    private final RequestSendingPolicy sendingPolicy;
    // Counter to keep track of sent messages for message IDs
    private int messageCounter = 0;
    // Current ledger
    private ArrayList<String> ledger = new ArrayList<>();
    // Map with received ledger update messages
    private Map<Integer, List<ConsensusMessage>> receivedLedgerUpdates = new HashMap<>();

    public ClientState(String configPath, String ipAddress, int port, String sendingPolicy, String clientId,
            String commandsFilePath) throws IncorrectSendingPolicyException, CommandsFilePathNotValidException {

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

        this.clientService = new ClientService(configPath, clientId, ipAddress, port, this);

        startListening();

        if (commandsFilePath != null) {

            try {
                ExecuteCommands(commandsFilePath);
            } catch (IOException e) {
                throw new CommandsFilePathNotValidException(commandsFilePath);
            }

        }

    }

    public ClientState(String configPath, String ipAddress, int port, String sendingPolicy, String clientId) {
        this(configPath, ipAddress, port, sendingPolicy, clientId, null);
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

    private void WriteLedgerToFile(String ledgerFilePath) throws IOException{

        String ledgerString = String.join("", ledger);
        BufferedWriter writer = new BufferedWriter(new FileWriter(ledgerFilePath));

        writer.write(ledgerString);

        writer.close();
    }

}
