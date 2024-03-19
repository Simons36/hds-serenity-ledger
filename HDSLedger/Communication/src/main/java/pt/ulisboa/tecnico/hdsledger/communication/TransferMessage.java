package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.PublicKey;

import com.google.gson.Gson;

public class TransferMessage {
    private String receiverId;

    private String amount;

    private PublicKey publicKeySender;

    private PublicKey publicKeyReceiver;

    public TransferMessage(String receiverId, String amount, PublicKey publicKeyReceiver, PublicKey publicKeySender) {
        this.receiverId = receiverId;
        this.amount = amount;
        this.publicKeyReceiver = publicKeyReceiver;
        this.publicKeySender = publicKeySender;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public String getAmount() {
        return amount;
    }

    public PublicKey getPublicKeySender() {
        return publicKeySender;
    }

    public PublicKey getPublicKeyReceiver() {
        return publicKeyReceiver;
    }

    //to json
    public String toJson() {
        return new Gson().toJson(this);
    }
}
