package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.common.models.ErrorType;

public class TransferResponseMessage {

    private final long responseToMessageId;
    private final boolean success;
    private final ErrorType errorMessage;

    // Constructor for success case
    public TransferResponseMessage(long responseToMessageId, boolean success) {
        this(responseToMessageId, success, null);
    }

    // Constructor for failure case
    public TransferResponseMessage(long responseToMessageId, boolean success, ErrorType errorMessage) {
        this.responseToMessageId = responseToMessageId;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    // getters

    public long getResponseToMessageId() {
        return responseToMessageId;
    }

    public boolean isSuccess() {
        return success;
    }

    public ErrorType getErrorMessage() {
        return errorMessage;
    }
    
    public String toJson() {
        return new Gson().toJson(this);
    }
}
