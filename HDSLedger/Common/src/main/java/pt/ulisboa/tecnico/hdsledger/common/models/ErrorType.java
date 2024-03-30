package pt.ulisboa.tecnico.hdsledger.common.models;

public enum ErrorType {
    INVALID_SENDER_ID("Provided sender id does not exist"),
    INVALID_RECEIVER_ID("Provided receiver id does not exist"),
    INVALID_SENDER_PK("Provided sender public key is invalid"),
    INVALID_SIGNATURE("Provided signature of the transaction ID is invalid"),
    INVALID_TRANSACTION_ID("Provided transaction ID is invalid"),
    INVALID_NONCE("The sent nonce has already been used"),
    INSUFFICIENT_BALANCE("The sender does not have enough funds to make the transaction"),
    UNKNOWN_ERROR("Unknown error");

    private String message;

    ErrorType(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
