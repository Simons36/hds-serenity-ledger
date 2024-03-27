package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.common.models.Transaction;

public class TransferMessage {

    private Transaction transaction;

    public TransferMessage(Transaction transaction) {
        this.transaction = transaction;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    //to json
    public String toJson() {
        return new Gson().toJson(this);
    }
}
