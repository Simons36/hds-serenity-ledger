package pt.ulisboa.tecnico.hdsledger.utilities;

public enum ErrorMessage {
    ConfigFileNotFound("The configuration file is not available at the path supplied"),
    ConfigFileFormat("The configuration file has wrong syntax"),
    NoSuchNode("Can't send a message to a non existing node"),
    SocketSendingError("Error while sending message"),
    CannotOpenSocket("Error while opening socket"),
    ErrorImportingPrivateKey("The private key is not available at the path supplied"),
    InvalidSignature("The signature is invalid"),
    DebugMessage("Debug message"),
    InvalidPublicKey("The provided public key for this client is invalid"),
    AccountNotFound("The account was not found"),
    DuplicateClientInConfig("The configuration file has a duplicate client");

    private final String message;

    ErrorMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
