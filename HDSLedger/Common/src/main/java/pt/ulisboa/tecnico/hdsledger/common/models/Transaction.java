package pt.ulisboa.tecnico.hdsledger.common.models;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;


import pt.ulisboa.tecnico.hdsledger.cryptolib.CryptoUtil;
import pt.ulisboa.tecnico.hdsledger.utilities.Util;

public class Transaction implements Serializable{

    private final byte[] rawTransactionId;

    private final String senderPublicKeyBase64;

    private final String receiverPublicKeyBase64;

    private double amount;

    private final String nonceInBase64;

    private double fee;

    // Signature of the transaction ID, signed by the sender
    private final String signatureInBase64;

    public Transaction(PrivateKey senderPrivateKey, PublicKey senderPublicKey, PublicKey receiverPublicKey, double amount,
            String nonceInBase64) {
        this.senderPublicKeyBase64 = Base64.getEncoder().encodeToString(senderPublicKey.getEncoded());
        this.receiverPublicKeyBase64 = Base64.getEncoder().encodeToString(receiverPublicKey.getEncoded());
        this.amount = amount;

        this.rawTransactionId = CreateTransactionId(senderPublicKey, receiverPublicKey, amount, nonceInBase64);

        this.nonceInBase64 = nonceInBase64;

        // we now sign the transaction id with the private key

        try {
            this.signatureInBase64 = Base64.getEncoder()
                    .encodeToString(CryptoUtil.sign(rawTransactionId, senderPrivateKey));

        } catch (Exception e) {
            throw new RuntimeException("Error signing transaction");
        }

    }

    public String getTransactionIdInHex() {
        return Util.bytesToHex(rawTransactionId);
    }

    public byte[] getRawTransactionId() {
        return rawTransactionId;
    }

    public String getSenderPublicKeyBase64() {
        return senderPublicKeyBase64;
    }

    public String getReceiverPublicKeyBase64() {
        return receiverPublicKeyBase64;
    }

    public PublicKey getSenderPublicKey() throws Exception {
        return CryptoUtil.convertBase64ToPublicKey(senderPublicKeyBase64);
    }

    public PublicKey getReceiverPublicKey() throws Exception {
        return CryptoUtil.convertBase64ToPublicKey(receiverPublicKeyBase64);
    }

    public double getAmount() {
        return amount;
    }

    public double getFee() {
        return fee;
    }

    public void setTransactionId(byte[] transactionId) {
        System.arraycopy(transactionId, 0, rawTransactionId, 0, transactionId.length);
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getSignatureInBase64() {
        return this.signatureInBase64;
    }

    public byte[] getSignature() {
        return Base64.getDecoder().decode(signatureInBase64);
    }

    public String getNonceInBase64() {
        return nonceInBase64;
    }

    public void setFee(double percentageOfAmount) {
        this.fee = amount * percentageOfAmount;
    }

    public static byte[] CreateTransactionId(PublicKey senderPublicKey, PublicKey receiverPublicKey, double amount,
            String nonceInBase64) {
        byte[] senderPublicKeyBytes = senderPublicKey.getEncoded();
        byte[] receiverPublicKeyBytes = receiverPublicKey.getEncoded();
        long amountLong = Double.doubleToRawLongBits(amount);
        byte[] amountBytes = ByteBuffer.allocate(8).putLong(amountLong).array();
        byte[] nonceBytes = Base64.getDecoder().decode(nonceInBase64);

        byte[] data = new byte[senderPublicKeyBytes.length + receiverPublicKeyBytes.length + amountBytes.length
                + nonceBytes.length];

        System.arraycopy(senderPublicKeyBytes, 0, data, 0, senderPublicKeyBytes.length);
        System.arraycopy(receiverPublicKeyBytes, 0, data, senderPublicKeyBytes.length, receiverPublicKeyBytes.length);
        System.arraycopy(amountBytes, 0, data, senderPublicKeyBytes.length + receiverPublicKeyBytes.length,
                amountBytes.length);
        System.arraycopy(nonceBytes, 0, data,
                senderPublicKeyBytes.length + receiverPublicKeyBytes.length + amountBytes.length, nonceBytes.length);

        try {
            byte[] hash = CryptoUtil.hash(data);
            return hash;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error hashing transaction data.");
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TX-")
                .append(getTransactionIdInHex())
                .append(":\n")
                .append(" From: ")
                .append(CryptoUtil.getAbbreviationOfHash(senderPublicKeyBase64) + "\n")
                .append(" To: ")
                .append(CryptoUtil.getAbbreviationOfHash(receiverPublicKeyBase64) + "\n")
                .append(" Amount: ")
                .append(amount + "\n")
                .append(" Fee: ")
                .append(fee + "\n")
                .append(" Nonce: ")
                .append(nonceInBase64 + "\n")
                .append(" Signature: ")
                .append(signatureInBase64 + "\n");

        return sb.toString();
    }

}
