package pt.ulisboa.tecnico.hdsledger.client.service;

import pt.ulisboa.tecnico.hdsledger.client.enums.RequestSendingPolicy;
import pt.ulisboa.tecnico.hdsledger.common.models.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.ClientErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.ClientException;

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
                throw new ClientException(ClientErrorMessage.IncorrectSendingPolicy);
                
        }

        this.clientService = new ClientService(configPath, ipAddress, port, this);

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
                clientService.broadcast(new AppendMessage(String.valueOf(this.messageCounter), content, content));
                break;
        
            default:
                throw new RuntimeException("Sending policy not implemented yet.");
        }
    }


}
