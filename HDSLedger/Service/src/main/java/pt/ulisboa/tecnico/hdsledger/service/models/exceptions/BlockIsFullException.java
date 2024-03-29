package pt.ulisboa.tecnico.hdsledger.service.models.exceptions;

public class BlockIsFullException extends RuntimeException {

    private final int index;

    private final String blockHashInHex;

    public BlockIsFullException(int index, String blockHashInHex) {
        super("The block with index #" + index + " and hash " + blockHashInHex +   "is full");
        this.index = index;
        this.blockHashInHex = blockHashInHex;
    }

    public int getIndex() {
        return index;
    }

    public String getBlockHashInHex() {
        return blockHashInHex;
    }
}
