package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class CheckBalanceResponseMessage {
    
    private final String balance;

    private final int responseToCheckBalanceRequestId;

    public CheckBalanceResponseMessage(String balance, int responseToCheckBalanceRequestId) {
        this.balance = balance;
        this.responseToCheckBalanceRequestId = responseToCheckBalanceRequestId;
    }

    public String getBalance() {
        return balance;
    }

    public int getResponseToCheckBalanceRequestId() {
        return responseToCheckBalanceRequestId;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
