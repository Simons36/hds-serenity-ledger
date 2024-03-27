package pt.ulisboa.tecnico.hdsledger.client;

import java.util.ArrayList;
import java.util.List;

import pt.ulisboa.tecnico.hdsledger.client.cli.CommandLineInterface;
import pt.ulisboa.tecnico.hdsledger.client.service.ClientState;

public class Client {
    
    private static String configPath = "../Common/src/main/resources/";

    private static String commandsPath  = "src/main/resources/";

    //main
    public static void main(String[] args) {

        if (args.length < 6){
            System.out.println("Usage: mvn compile exec:java -Dexec.args=\"<clientId> <config filename> <ip_address> <port> <sending_policy> <verbose_mode> OPTIONAL[<commands_file_path>]\"\n" + 
                               " <sending_policy> can take the following values: \n" +
                               "  - 'all' to send the transaction to all nodes\n" +
                               "  - 'majority' to send the transaction to the majority of nodes\n" +
                               "  - 'one' to send the transaction to a single node (leader)\n" +
                               " <verbose_mode> can take the following values: \n" +
                               "  - 'true' to enable debug mode\n" +
                               "  - 'false' to disable debug mode\n" +
                               " <commands_file_path> is the path to a json file containing commands to be executed\n" +
                               " If you don't provide <commands_file_path> the client will start a command line interface");
        
        }
        
        final String thisClientId = args[0];
        configPath += args[1];
        final String ipAddress = args[2];
        final int port = Integer.parseInt(args[3]);
        final String sendingPolicy = args[4];
        final boolean verboseMode = Boolean.parseBoolean(args[5]);
        ClientState clientState = null;

        try {

            if(args.length == 7){
                clientState = new ClientState(configPath, ipAddress, port, sendingPolicy, thisClientId, commandsPath + args[6], verboseMode);
            }else{
                clientState = new ClientState(configPath, ipAddress, port, sendingPolicy, thisClientId, verboseMode);
                CommandLineInterface.ParseInput(clientState);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        



    }

}
