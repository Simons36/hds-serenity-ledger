package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.cryptolib.CryptoUtil;

public class CheckBalanceMessage {

    private final String publicKeyBase64;

    private final long checkBalanceRequestId;

    public CheckBalanceMessage(PublicKey publicKey, long checkBalanceRequestId) {
        this.publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        this.checkBalanceRequestId = checkBalanceRequestId;
    }

    public PublicKey getPublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException, Exception {
        try {
            return CryptoUtil.convertBase64ToPublicKey(publicKeyBase64);
        } catch (Exception e) {
            throw e;
        }
    }

    public long getCheckRequestBalanceId(){
        return this.checkBalanceRequestId;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
    
}
