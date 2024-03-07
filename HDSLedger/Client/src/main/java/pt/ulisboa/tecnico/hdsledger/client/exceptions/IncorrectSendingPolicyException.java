package pt.ulisboa.tecnico.hdsledger.client.exceptions;

public class IncorrectSendingPolicyException extends RuntimeException{
    
    public IncorrectSendingPolicyException(String userInput){
        super("The sending policy '" + userInput + "'' is not valid. Please use one of the following: 'ALL', 'QUORUM', 'ONE'");
    }

}
