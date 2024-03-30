package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.common.models.Transaction;

public class TransferMessage {

    private Transaction transaction;

    private long transferMessageId;

    public TransferMessage(Transaction transaction, long transferMessageId) {
        this.transaction = transaction;
        this.transferMessageId = transferMessageId;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public long getTransferMessageId() {
        return transferMessageId;
    }

    //to json
    public String toJson() {
        return new Gson().toJson(this);
    }
}
