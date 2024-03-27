package pt.ulisboa.tecnico.hdsledger.common.models;

import java.security.PublicKey;
import java.util.Base64;

public class Transaction {

    private final String transactionId;
    
    private final PublicKey senderPublicKey;

    private final PublicKey receiverPublicKey;

    private final int amount;

    private final int fee;

    // Signature of the transaction, signed by the sender
    private final String signatureInBase64;

    public Transaction(String transactionId, PublicKey senderPublicKey, PublicKey receiverPublicKey, 
                                                                    int amount, int fee, String signatureInBase64){
        this.transactionId = transactionId;
        this.senderPublicKey = senderPublicKey;
        this.receiverPublicKey = receiverPublicKey;
        this.amount = amount;
        this.fee = fee;
        this.signatureInBase64 = signatureInBase64;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public PublicKey getSenderPublicKey() {
        return senderPublicKey;
    }

    public PublicKey getReceiverPublicKey() {
        return receiverPublicKey;
    }

    public int getAmount() {
        return amount;
    }

    public int getFee() {
        return fee;
    }

    @Override
    public String toString() {
        String senderPublicKeyBase64 = Base64.getEncoder().encodeToString(senderPublicKey.getEncoded());
        String receiverPublicKeyBase64 = Base64.getEncoder().encodeToString(receiverPublicKey.getEncoded());
        StringBuilder sb = new StringBuilder();
        sb.append("TX-")
          .append(transactionId)
          .append(": ")
          .append("From: ")
          .append(senderPublicKeyBase64)
          .append(" To: ")
          .append(receiverPublicKeyBase64)
          .append(" Amount: ")
          .append(amount)
          .append(" Fee: ")
          .append(fee);
          
        return sb.toString();
    }



}
