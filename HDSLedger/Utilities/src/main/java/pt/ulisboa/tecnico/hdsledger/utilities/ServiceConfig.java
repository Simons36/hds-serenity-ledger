package pt.ulisboa.tecnico.hdsledger.utilities;

public class ServiceConfig {
    
    private int initialAccountBalance;

    private double transactionFee;

    public ServiceConfig() {}

    // getters

    public int getInitialAccountBalance() {
        return initialAccountBalance;
    }

    public double getTransactionFee() {
        return transactionFee;
    }
}
