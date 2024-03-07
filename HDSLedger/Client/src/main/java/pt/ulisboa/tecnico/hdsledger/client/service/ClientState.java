package pt.ulisboa.tecnico.hdsledger.client.service;

import pt.ulisboa.tecnico.hdsledger.client.enums.RequestSendingPolicy;
import pt.ulisboa.tecnico.hdsledger.client.exceptions.ErrorCommunicatingWithNode;
import pt.ulisboa.tecnico.hdsledger.client.exceptions.IncorrectSendingPolicyException;
import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;

public class ClientState {
    
    //Id of this client
    private final String clientId;
    //Service that will be used for node communication
    private final ClientService clientService;
    //Sending policy to be used
    private final RequestSendingPolicy sendingPolicy;
    //Counter to keep track of sent messages for message IDs
    private int messageCounter = 0;

    public ClientState(String configPath, String ipAddress, int port, String sendingPolicy, String clientId) {
        
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


}
