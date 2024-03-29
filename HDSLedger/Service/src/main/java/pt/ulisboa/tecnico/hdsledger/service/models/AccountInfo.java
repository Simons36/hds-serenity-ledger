package pt.ulisboa.tecnico.hdsledger.service.models;

import java.security.PublicKey;

public class AccountInfo {
    
    private String associatedClientId;

    private int balance;

    private String publicKeyFilename;


    public AccountInfo(String associatedClientId, int balance, String publicKeyFilename) {
        this.associatedClientId = associatedClientId;
        this.balance = balance;
        this.publicKeyFilename = publicKeyFilename;
    }
    

    public String getAssociatedClientId() {
        return associatedClientId;
    }

    public int getBalance() {
        return balance;
    }

    public String getPublicKeyFilename() {
        return publicKeyFilename;
    }

    public boolean isTransactionValid(int amount) {
        return balance - amount >= 0;
    }

    public void increaseBalance(int amount) {
        balance += amount;
    }

    public void decreaseBalance(int amount) {
        balance -= amount;
    }


}
