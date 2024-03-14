package pt.ulisboa.tecnico.hdsledger.communication;

import java.io.Serializable;

public class Message implements Serializable {

    // Sender identifier
    private String senderId;
    // Message identifier
    private int messageId;
    // Message type
    private Type type;

    //authentication bytes
    //TODO: Decide whether to use digital signatures or MACs
    private byte[] signature;

    public enum Type {
        APPEND, PRE_PREPARE, PREPARE, COMMIT, ACK, IGNORE, ROUND_CHANGE;
    }

    public Message(String senderId, Type type) {
        this.senderId = senderId;
        this.type = type;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public int getMessageId() {
        return messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    @Override
    public String toString() {
        return "Message{" +
                "senderId= '" + senderId + '\'' +
                ", messageId= " + messageId +
                ", type= " + type +
                ", signature= " + signature +
                '}';
    }
}
