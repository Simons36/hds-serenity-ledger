package pt.ulisboa.tecnico.hdsledger.utilities;

import java.text.MessageFormat;

public class ClientException extends RuntimeException{

    private final ClientErrorMessage errorMessage;

    public ClientException(ClientErrorMessage message) {
        errorMessage = message;
    }
    
    @Override
    public String getMessage() {
        return errorMessage.getMessage();
    }
    
}
