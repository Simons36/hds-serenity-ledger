package pt.ulisboa.tecnico.hdsledger.client.exceptions;

public class CommandsFilePathNotValidException  extends RuntimeException{

    public CommandsFilePathNotValidException(String filePath){
        super("The file path '" + filePath + "'' is not valid. Please use a valid file path.");
    }
    
}
