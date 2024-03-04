package pt.ulisboa.tecnico.hdsledger.cryptolib.structs;

public abstract class AuthenticationType {

    protected int size;  // Keep 'static' for shared field among subclasses

    protected String authenticationTypeName;

    private byte[] content;

    public AuthenticationType(byte[] content) {
        this.content = content;
    }

    public byte[] getContent() {
        return content;
    }

    public int getSize() {
        return size;
    }

    public String getAuthenticationTypeName() {
        return authenticationTypeName;
    }
}
