package pt.ulisboa.tecnico.hdsledger.utilities;


public enum ClientErrorMessage {

    IncorrectSendingPolicy("The provided sending policy is not valid. Please provide one of the following:\n" +
                            "   'all'\n" +
                            "   'majority'\n" + 
                            "   'one'");

    private final String message;

    ClientErrorMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
