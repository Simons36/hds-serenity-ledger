package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import com.google.gson.Gson;

public class CheckBalanceMessage {

    private String publicKeyBase64;

    public CheckBalanceMessage(PublicKey publicKey) {
        this.publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    public PublicKey getPublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA"); // Adjust algorithm as per your key type
        return keyFactory.generatePublic(keySpec);
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
    
}
