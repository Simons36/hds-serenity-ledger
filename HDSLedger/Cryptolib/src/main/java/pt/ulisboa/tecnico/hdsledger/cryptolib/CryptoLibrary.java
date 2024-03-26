package pt.ulisboa.tecnico.hdsledger.cryptolib;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;

import pt.ulisboa.tecnico.hdsledger.cryptolib.structs.SignatureStruct;

public class CryptoLibrary {
    
    //Stores the private key of this node
    private static PrivateKey _thisNodePrivateKey;

    //Stores the public keys of all nodes
    private static HashMap<String, PublicKey> _publicKeys = new HashMap<>();


    public CryptoLibrary(String pathToPrivateKey) throws Exception{

        _thisNodePrivateKey = CryptoIO.readPrivateKey(pathToPrivateKey); 
    }

    public void addPublicKey(String nodeID, String pathToPublicKey) throws Exception{

        PublicKey publicKey = CryptoIO.readPublicKey(pathToPublicKey);
        _publicKeys.put(nodeID, publicKey);
    
    }

    public SignatureStruct sign(byte[] data) throws Exception{
        
        byte[] signature = CryptoUtil.sign(data, _thisNodePrivateKey);
        return new SignatureStruct(signature);

    }

    public boolean verifySignature(byte[] data, SignatureStruct signature, String nodeID) throws Exception{

        PublicKey publicKey = _publicKeys.get(nodeID);
        return CryptoUtil.verifySignature(data, signature.getContent(), publicKey);
    
    }

    public boolean verifyPublicKey(String pathToPublicKey, PublicKey providedPublicKey){
            
        try {
            PublicKey publicKey = CryptoIO.readPublicKey(pathToPublicKey);
            return publicKey.equals(providedPublicKey);
        } catch (Exception e) {
            return false;
        }
    }

}
