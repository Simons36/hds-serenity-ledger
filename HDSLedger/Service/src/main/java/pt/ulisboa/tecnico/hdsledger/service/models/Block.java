package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.List;

import pt.ulisboa.tecnico.hdsledger.common.models.Transaction;

public class Block {

    private final int index;

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

    @Override
    public String toString() {
        return "Block{" +
                "index=" + index +
                ", transactions=" + transactions +
                '}';
    }
    
}
