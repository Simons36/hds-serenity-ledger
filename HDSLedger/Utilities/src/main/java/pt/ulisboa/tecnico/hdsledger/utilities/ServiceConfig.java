package pt.ulisboa.tecnico.hdsledger.utilities;

public class ServiceConfig {
    
    private int initial_account_balance;

    private double transaction_fee;

    private int num_transaction_in_single_block;

    public ServiceConfig() {}

    // getters

    public int getInitialAccountBalance() {
        return this.initial_account_balance;
    }

    public double getTransactionFee() {
        return this.transaction_fee;
    }

    public int getNumTransactionsInBlock() {
        return this.num_transaction_in_single_block;
    }
}
