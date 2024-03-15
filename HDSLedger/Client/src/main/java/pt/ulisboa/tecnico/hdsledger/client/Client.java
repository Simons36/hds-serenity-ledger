package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.client.cli.CommandLineInterface;
import pt.ulisboa.tecnico.hdsledger.client.service.ClientState;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

public class Client {
    
    private static String configPath = "../Common/src/main/resources/";

    private static final CustomLogger LOGGER = new CustomLogger(Client.class.getName());

    //main
    public static void main(String[] args) {

        if (args.length != 5){
            System.out.println("Usage: mvn compile exec:java -Dexec.args=\"<clientId> <config filename> <ip_address> <port> <sending_policy>\"\n" + 
                               " <sending_policy> can take the following values: \n" +
                               "  - 'all' to send the transaction to all nodes\n" +
                               "  - 'majority' to send the transaction to the majority of nodes\n" +
                               "  - 'one' to send the transaction to a single node (leader)");
            return;
        
        }
        
        final String thisClientId = args[0];
        configPath += args[1];
        final String ipAddress = args[2];
        final int port = Integer.parseInt(args[3]);
        final String sendingPolicy = args[4];

        // Create client service
        ClientState clientState = new ClientState(configPath, ipAddress, port, sendingPolicy, thisClientId, LOGGER);

        // Start command line interface
        CommandLineInterface.ParseInput(clientState);
    }

}
