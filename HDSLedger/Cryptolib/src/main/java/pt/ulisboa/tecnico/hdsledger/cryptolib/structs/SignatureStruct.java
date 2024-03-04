package pt.ulisboa.tecnico.hdsledger.cryptolib.structs;

public class SignatureStruct extends AuthenticationType{

    public SignatureStruct(byte[] content) {
        super(content);
        size = 256;
        authenticationTypeName = "digital_signature";
    }
    
}
