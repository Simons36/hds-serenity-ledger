package pt.ulisboa.tecnico.hdsledger.client.cli;

import java.util.Scanner;

import pt.ulisboa.tecnico.hdsledger.client.enums.CommandType;
import pt.ulisboa.tecnico.hdsledger.client.service.ClientState;

import static pt.ulisboa.tecnico.hdsledger.client.enums.CommandType.HELP;


public class CommandLineInterface {

    public static void ParseInput(ClientState clientState) {
        System.out.println("Starting command line interface...");
        System.out.println("Welcome to HDSLedger client! Type 'help' for a list of commands.");

        Scanner scanner = new Scanner(System.in);
        boolean condition = true;

        while (condition) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();
            String[] arguments = line.split(" ");
            String commandString = arguments[0];

            try {

                switch (CommandType.fromString(commandString)) {
    
                    case HELP:
                        System.out.println(CommandType.getHelpMessage());
                        break;
    
                    case APPEND:
                        if (arguments.length < 2) {
                            System.out.println(CommandType.APPEND.getCommandUsage());
                            break;
                        }
                        clientState.SendAppendMessage(arguments[1]);
                        break;

                    case EXIT:
                        condition = false;
                        break;
                        
                    default:
                        System.out.println("Unknown command. Type 'help' for list of commands.");
                        break;
    
                }
                
            } catch (IllegalArgumentException e) {}

        }
    
        scanner.close();
        System.exit(0);
        

    }
}
