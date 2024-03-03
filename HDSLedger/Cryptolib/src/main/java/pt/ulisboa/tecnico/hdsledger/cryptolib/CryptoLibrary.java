package pt.ulisboa.tecnico.hdsledger.cryptolib;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;

public class CryptoLibrary {
    
    //Stores the private key of this node
    private static PrivateKey _thisNodePrivateKey;

    //Stores the public keys of all nodes
    private static HashMap<String, PublicKey> _publicKeys = new HashMap<>();


    public CryptoLibrary(String pathToPrivateKey) throws Exception{

        _thisNodePrivateKey = CryptoIO.readPrivateKey(pathToPrivateKey); 
    }

    public static void addPublicKey(String nodeID, String pathToPublicKey) throws Exception{

        PublicKey publicKey = CryptoIO.readPublicKey(pathToPublicKey);
        _publicKeys.put(nodeID, publicKey);
    
    }

    public static byte[] sign(byte[] data) throws Exception{
        
        return CryptoUtil.sign(data, _thisNodePrivateKey);

    }

    public static boolean verifySignature(byte[] data, byte[] signature, String nodeID) throws Exception{

        PublicKey publicKey = _publicKeys.get(nodeID);
        return CryptoUtil.verifySignature(data, signature, publicKey);
    
    }

}
