package pt.ulisboa.tecnico.hdsledger.communication;

public class ClientErrorMessage extends Message {

    private final ErrorType errorType;

    private final int correspondingMessageId;

    // enum
    public enum ErrorType {
        INVALID_SENDER_ID("Provided sender id does not exist"),
        INVALID_RECEIVER_ID("Provided receiver id does not exist"),
        INVALID_SENDER_PK("Provided sender public key is invalid"),
        UNKNOWN_ERROR("Unknown error");

        private String message;

        ErrorType(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public ClientErrorMessage(String nodeIdOfSender, ErrorType errorType, int correspondingMessageId) {
        super(nodeIdOfSender, Type.ERROR);
        this.errorType = errorType;
        this.correspondingMessageId = correspondingMessageId;
    }
    

    public ErrorType getErrorType() {
        return errorType;
    }

    public int getCorrespondingMessageId() {
        return correspondingMessageId;
    }

    public String getErrorMessage() {
        return errorType.message;
    }
}