package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.PublicKey;

import com.google.gson.Gson;

public class CheckBalanceMessage {

    private PublicKey publicKey;

    public CheckBalanceMessage(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
    
}
