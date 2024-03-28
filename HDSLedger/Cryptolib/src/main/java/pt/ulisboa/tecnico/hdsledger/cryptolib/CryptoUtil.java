package pt.ulisboa.tecnico.hdsledger.cryptolib;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class CryptoUtil {

    final static String SIGNATURE_ALGO = "SHA256withRSA";

    public static byte[] sign(byte[] data, PrivateKey privateKey) throws Exception {

        Signature sig = Signature.getInstance(SIGNATURE_ALGO);
        sig.initSign(privateKey);
        sig.update(data);
        return sig.sign();

    }

    public static boolean verifySignature(byte[] data, byte[] signature, PublicKey publicKey) throws Exception {

        Signature sig = Signature.getInstance(SIGNATURE_ALGO);
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signature);

    }

    public static boolean verifyPublicKey(String pathToPublicKey, PublicKey providedPublicKey) {

        try {
            PublicKey publicKey = CryptoIO.readPublicKey(pathToPublicKey);
            return publicKey.equals(providedPublicKey);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Hash SHA-256
    public static byte[] hash(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(data);
    }


    public static String getAbbreviationOfHash(String hash){
        String hashFirst4Chars = hash.substring(0, 4);
        String hashLast4Chars = hash.substring(hash.length() - 4);
        return hashFirst4Chars + "-" + hashLast4Chars;
    }

    public static String convertPublicKeyToBase64(PublicKey publicKey){
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }


    public static PublicKey convertBase64ToPublicKey(String publicKeyBase64) throws Exception{
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA"); // Adjust algorithm as per your key type
        return keyFactory.generatePublic(keySpec);
    }

}
