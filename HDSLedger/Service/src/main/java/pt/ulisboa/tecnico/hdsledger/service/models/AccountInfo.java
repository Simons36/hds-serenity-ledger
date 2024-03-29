package pt.ulisboa.tecnico.hdsledger.service.models;

public class AccountInfo {
    
    private String associatedClientId;

    private double balance;

    private String publicKeyFilename;


    public AccountInfo(String associatedClientId, double balance, String publicKeyFilename) {
        this.associatedClientId = associatedClientId;
        this.balance = balance;
        this.publicKeyFilename = publicKeyFilename;
    }
    

    public String getAssociatedClientId() {
        return associatedClientId;
    }

    public double getBalance() {
        return balance;
    }

    public String getPublicKeyFilename() {
        return publicKeyFilename;
    }

    public boolean isTransactionValid(int amount) {
        return balance - amount >= 0;
    }

    public void increaseBalance(double amount) {
        balance += amount;
    }

    public void decreaseBalance(double amount) {
        balance -= amount;
    }


}
