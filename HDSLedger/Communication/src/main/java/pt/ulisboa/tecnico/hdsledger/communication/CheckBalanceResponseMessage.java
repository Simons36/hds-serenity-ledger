package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class CheckBalanceResponseMessage {
    
    private final String balance;

    private final long responseToCheckBalanceRequestId;

    public CheckBalanceResponseMessage(String balance, long responseToCheckBalanceRequestId) {
        this.balance = balance;
        this.responseToCheckBalanceRequestId = responseToCheckBalanceRequestId;
    }

    public String getBalance() {
        return balance;
    }

    public long getResponseToCheckBalanceRequestId() {
        return responseToCheckBalanceRequestId;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
