package pt.ulisboa.tecnico.hdsledger.service.models;

import java.io.Serializable;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.common.models.Transaction;
import pt.ulisboa.tecnico.hdsledger.cryptolib.CryptoUtil;
import pt.ulisboa.tecnico.hdsledger.service.models.exceptions.BlockIsFullException;
import pt.ulisboa.tecnico.hdsledger.utilities.Util;

public class Block implements Serializable{

    private final int index;

    private byte[] hash;

    private final Transaction transactions[];

    private final Block previousBlock;

    private Block nextBlock;

    public Block(int index, Transaction[] transactions) {
        this.index = index;
        this.transactions = transactions;
        this.previousBlock = null;
    }

    public Block(int index, Transaction[] transactions, Block previousBlock) {
        this.index = index;
        this.transactions = transactions;
        this.previousBlock = previousBlock;
    }

    public int getIndex() {
        return index;
    }

    public Transaction[] getTransactions() {
        return transactions;
    }

    public Block getPreviousBlock() {
        return previousBlock;
    }

    public Block getNextBlock() {
        return nextBlock;
    }

    public void addNextBlock(Block nextBlock) {
        this.nextBlock = nextBlock;
    }

    public boolean isGenesisBlock() {
        return previousBlock == null;
    }

    public byte[] getHash() {
        return hash;
    }

    public boolean isFull() {
        for (Transaction transaction : transactions) {
            if (transaction == null) {
                return false;
            }
        }

        return true;
    }

    /**
     * This function adds a transaction to the block. The return value is one of the following:<p>
     *  - <b>false:</b> the transaction was added successfully, and the block is not full yet <p>
     *  - <b>true:</b> the transaction was added successfully, and the block became full with this addition. The return value is the hash of the block <p>
     *  - <b>BlockIsFullException:</b> the block is already full, and the transaction could not be added <p>
     *  - <b>Exception:</b> an error occurred while generating the hash of the block
     * @param transaction
     */
    public boolean addTransactionAndCheckIfFull(Transaction transaction) throws BlockIsFullException, Exception{
        for (int i = 0; i < transactions.length; i++) {
            if (transactions[i] == null) {
                transactions[i] = transaction;

                if(i == transactions.length - 1) {
                    try {
                        generateHash();
                        return true;
                    } catch (Exception e) {
                        throw e;
                    }
                }

                return false;
            }
        }

        throw new BlockIsFullException(index, Util.bytesToHex(hash));
    }

    private void generateHash() throws Exception {
        byte[] thisObject = Util.serialize(this);

        try {
            this.hash = CryptoUtil.hash(thisObject);
        } catch (Exception e) {
            throw e;
        }
    }

    // to json using Gson
    public String toJson(){
        return new Gson().toJson(this);
    }

    @Override
    public String toString() {
        return toJson();
    }

}
