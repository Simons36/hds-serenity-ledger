package pt.ulisboa.tecnico.hdsledger.client.exceptions;

import java.io.IOException;

import pt.ulisboa.tecnico.hdsledger.client.enums.Commands;

public class ErrorCommunicatingWithNode extends RuntimeException{
    
    public ErrorCommunicatingWithNode(String nodeId, Commands command, IOException causingException){
        super("Error communicating with node " + nodeId + " while trying to execute command " + command.getCommand() + ": " + causingException.getMessage());
    }

}
