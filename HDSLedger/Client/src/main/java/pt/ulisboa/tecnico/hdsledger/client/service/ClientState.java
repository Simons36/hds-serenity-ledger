package pt.ulisboa.tecnico.hdsledger.client.service;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import pt.ulisboa.tecnico.hdsledger.client.enums.RequestSendingPolicy;
import pt.ulisboa.tecnico.hdsledger.client.exceptions.ErrorCommunicatingWithNode;
import pt.ulisboa.tecnico.hdsledger.client.exceptions.IncorrectSendingPolicyException;
import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerUpdateMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

public class ClientState {

    private final CustomLogger LOGGER;
    //Id of this client
    private final String clientId;
    //Service that will be used for node communication
    private final ClientService clientService;
    //Sending policy to be used
    private final RequestSendingPolicy sendingPolicy;
    //Counter to keep track of sent messages for message IDs
    private int messageCounter = 0;
    // Current ledger
    private ArrayList<String> ledger = new ArrayList<>();
    // Map with received ledger update messages
    private Map<Integer, List<ConsensusMessage>> receivedLedgerUpdates = new HashMap<>();

    public ClientState(String configPath, String ipAddress, int port, String sendingPolicy, String clientId, CustomLogger LOGGER) {
        
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

        this.clientService = new ClientService(configPath, clientId, ipAddress, port, this, LOGGER);
        this.LOGGER = LOGGER;

        startListening();
        
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
        String senderId  = consensusMessage.getSenderId();

        synchronized(receivedLedgerUpdates){
            if (!receivedLedgerUpdates.containsKey(consensusInstance)) {
                receivedLedgerUpdates.put(consensusInstance, new ArrayList<>(List.of(consensusMessage)));
            }else{
                List<ConsensusMessage> instanceReceivedMessages = receivedLedgerUpdates.get(consensusInstance);

                boolean haventReceivedFromThisNode = instanceReceivedMessages.stream()
                                                                            .filter(ledgerUpdate -> {
                                                                                return ledgerUpdate.getSenderId().equals(senderId);
                                                                            } )
                                                                            .findFirst()
                                                                            .isEmpty();

                
                
                if(haventReceivedFromThisNode){
                    instanceReceivedMessages.add(consensusMessage);

                }else{
                    return;
                }
            }

            List<ConsensusMessage> instanceReceivedMessages = receivedLedgerUpdates.get(consensusInstance);
            if(instanceReceivedMessages.size() == clientService.getQuorumSize()){
                //We have a quorum
                //We can now update the ledger
                LedgerUpdateMessage ledgerUpdateMessage = consensusMessage.deserializeLedgerUpdateMessage();
                String newValue = ledgerUpdateMessage.getValue();

                ledger.ensureCapacity(consensusInstance);
                while (ledger.size() < consensusInstance - 1) {
                    ledger.add("");
                }
                
                ledger.add(consensusInstance - 1, newValue);
                
                System.out.println("AAAAAAAAAAAAAAAA");

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


}
