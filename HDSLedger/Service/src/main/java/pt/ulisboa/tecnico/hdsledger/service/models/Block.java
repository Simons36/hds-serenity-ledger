package pt.ulisboa.tecnico.hdsledger.service.models;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.security.PrivateKey;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import pt.ulisboa.tecnico.hdsledger.common.models.Transaction;
import pt.ulisboa.tecnico.hdsledger.cryptolib.CryptoUtil;
import pt.ulisboa.tecnico.hdsledger.service.models.exceptions.BlockIsFullException;
import pt.ulisboa.tecnico.hdsledger.service.models.util.ByteArraySerializer;
import pt.ulisboa.tecnico.hdsledger.utilities.Util;

public class Block implements Serializable {

    private final int index;

    private byte[] hash;

    private final Transaction transactions[];

    private double totalFees;

    private String nodeIdOfFeeReceiver;

    private byte[] signature; // Corresponds to signature of the hash of the block

    // private final Block previousBlock;

    // private Block nextBlock;

    public Block(int index, Transaction[] transactions) {
        this.index = index;
        this.transactions = transactions;
    }

    public int getIndex() {
        return index;
    }

    public Transaction[] getTransactions() {
        return transactions;
    }

    public byte[] getHash() {
        return hash;
    }

    public double getTotalFees() {
        return totalFees;
    }

    public boolean isFull() {
        for (Transaction transaction : transactions) {
            if (transaction == null) {
                return false;
            }
        }

        return true;
    }

    public void setNodeIdOfFeeReceiver(String nodeIdOfFeeReceiver) {
        this.nodeIdOfFeeReceiver = nodeIdOfFeeReceiver;
    }

    public String getNodeIdOfFeeReceiver() {
        return nodeIdOfFeeReceiver;
    }

    public void setHash(byte[] hash) {
        this.hash = hash;
    }

    public void setTotalFees(double totalFees) {
        this.totalFees = totalFees;
    }

    public void signBlock(PrivateKey privateKey) {
        try {
            this.signature = CryptoUtil.sign(hash, privateKey);
        
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public byte[] getSignature() {
        return signature;
    }

    /**
     * This function adds a transaction to the block. The return value is one of the
     * following:
     * <p>
     * - <b>false:</b> the transaction was added successfully, and the block is not
     * full yet
     * <p>
     * - <b>true:</b> the transaction was added successfully, and the block became
     * full with this addition. The return value is the hash of the block
     * <p>
     * - <b>BlockIsFullException:</b> the block is already full, and the transaction
     * could not be added
     * <p>
     * - <b>Exception:</b> an error occurred while generating the hash of the block
     * 
     * @param transaction
     */
    public boolean addTransactionAndCheckIfFull(Transaction transaction) throws BlockIsFullException, Exception {
        for (int i = 0; i < transactions.length; i++) {
            if (transactions[i] == null) {
                transactions[i] = transaction;

                totalFees += transaction.getFee();

                if (i == transactions.length - 1) {
                    try {
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

    public void replaceTransaction(int index, Transaction transaction) { // just for testing
        totalFees -= transactions[index].getFee();
        totalFees += transaction.getFee();
        transactions[index] = transaction;
    }

    public byte[] generateHash() {
        int totalLength = 0;

        // For each transaction
        for (Transaction transaction : transactions) {
            if (transaction != null) {
                totalLength += transaction.getRawTransactionId().length;
            }
        }

        // For index
        totalLength += Integer.BYTES;

        // For total fees
        totalLength += Double.BYTES;

        // For node id of fee receiver
        totalLength += nodeIdOfFeeReceiver.length();

        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.putInt(index);
        for (Transaction transaction : transactions) {
            byte[] transactionIdBytes = transaction.getRawTransactionId();
            buffer.put(transactionIdBytes);
        }
        buffer.putDouble(totalFees);
        buffer.put(nodeIdOfFeeReceiver.getBytes());

        byte[] data = buffer.array();

        try {
            return CryptoUtil.hash(data);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    // to json using Gson
    public String toJson() {
        return new Gson().toJson(this);
    }

    @Override
    public String toString() {
        final Gson gson = new GsonBuilder()
                .registerTypeAdapter(byte[].class, new ByteArraySerializer())
                .setPrettyPrinting()
                .create();
        return gson.toJson(this);
    }

}
