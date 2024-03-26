package pt.ulisboa.tecnico.hdsledger.cryptolib;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

public class CryptoUtil {
    
    final static String SIGNATURE_ALGO = "SHA256withRSA";   

    public static byte[] sign(byte[] data, PrivateKey privateKey) throws Exception{

        Signature sig = Signature.getInstance(SIGNATURE_ALGO);
        sig.initSign(privateKey);
        sig.update(data);
        return sig.sign();

    }

    public static boolean verifySignature(byte[] data, byte[] signature, PublicKey publicKey) throws Exception{

        Signature sig = Signature.getInstance(SIGNATURE_ALGO);
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signature);
    
    }

    public static boolean verifyPublicKey(String pathToPublicKey, PublicKey providedPublicKey){
            
        try {
            PublicKey publicKey = CryptoIO.readPublicKey(pathToPublicKey);
            return publicKey.equals(providedPublicKey);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
