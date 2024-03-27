package pt.ulisboa.tecnico.hdsledger.client.exceptions;

public class ClientIdDoesntExistException extends RuntimeException {

    public ClientIdDoesntExistException(String clientId){
        super("Client with id " + clientId + " doesn't exist.");
    }
    
}
