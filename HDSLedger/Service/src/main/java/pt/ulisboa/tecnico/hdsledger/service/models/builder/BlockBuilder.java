package pt.ulisboa.tecnico.hdsledger.service.models.builder;

import pt.ulisboa.tecnico.hdsledger.common.models.Transaction;
import pt.ulisboa.tecnico.hdsledger.service.models.Block;
import pt.ulisboa.tecnico.hdsledger.service.models.exceptions.BlockIsFullException;
import pt.ulisboa.tecnico.hdsledger.service.models.exceptions.BlockNotYetFullException;

public class BlockBuilder {
    
    private final Block instance;

    public BlockBuilder(int index, int numTransactionInSingleBlock) {
        instance = new Block(index, new Transaction[numTransactionInSingleBlock]);
    }

    /**
     * This function adds a transaction to the block. The return value is one of the following:<p>
     *  - <b>false:</b> the transaction was added successfully, and the block is not full yet <p>
     *  - <b>true:</b> the transaction was added successfully, and the block became full with this addition.<p>
     *  - <b>BlockIsFullException:</b> the block is already full, and the transaction could not be added <p>
     *  - <b>Exception:</b> an error occurred while generating the hash of the block
     * @param transaction
     */
    public boolean addTransactionAndCheckIfFull(Transaction transaction) throws BlockIsFullException, Exception {
        try {
            return instance.addTransactionAndCheckIfFull(transaction);
        } catch (Exception e) {
            throw e;
        }
    }

    public Block build() throws BlockNotYetFullException{
        if(!instance.isFull())
            throw new BlockNotYetFullException();
        return instance;
    }

    public Block getInstance() {
        return instance;
    }


}
