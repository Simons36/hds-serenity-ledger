package pt.ulisboa.tecnico.hdsledger.client.cli;

import java.util.Scanner;

import javax.management.monitor.Monitor;

import pt.ulisboa.tecnico.hdsledger.client.enums.CommandType;
import pt.ulisboa.tecnico.hdsledger.client.exceptions.ClientIdDoesntExistException;
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

                    case CHECK_BALANCE:
                        Object lock = new Object();
                        clientState.SendCheckBalanceMessage(lock);
                        synchronized (lock) {
                            try {
                                lock.wait();
                            } catch (Exception e) {
                                System.out.println("Error with lock");
                                break;
                            }
                        }
                        break;

                    case TRANSFER:
                        System.out.println();
                        System.out.println("Please provide the client ID of the receiver:");
                        String receiver = scanner.nextLine().trim();
                        for(;;){
                            System.out.println("Please provide the amount of coins to transfer:");
                            String amount = scanner.nextLine().trim();
                            try {
                                int amountInt = Integer.parseInt(amount);
                                if(amountInt > 0){
                                    clientState.SendTransferMessage(receiver, amountInt);
                                    break;
                                }else{
                                    System.out.println("Amount must be a positive integer.");
                                }
                            } catch (NumberFormatException e) {
                                System.out.println("Amount must be a positive integer.");
                            } catch (ClientIdDoesntExistException e){
                                System.out.println(e.getMessage());
                                break;
                            }
                        }
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
