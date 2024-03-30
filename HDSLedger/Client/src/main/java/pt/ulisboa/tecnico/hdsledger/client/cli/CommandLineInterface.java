package pt.ulisboa.tecnico.hdsledger.client.cli;

import java.util.Scanner;

import pt.ulisboa.tecnico.hdsledger.client.enums.CommandType;
import pt.ulisboa.tecnico.hdsledger.client.exceptions.ClientIdDoesntExistException;
import pt.ulisboa.tecnico.hdsledger.client.service.ClientState;


public class CommandLineInterface {

    private static final int MAX_TIME_WAIT = 5; // 10 seconds

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
                    
                        WaitForLock(lock);
                        break;

                    case TRANSFER:
                        System.out.println();
                        System.out.println("Please provide the client ID of the receiver:");
                        String receiver = scanner.nextLine().trim();

                        Object lockTransfer = new Object();
                        for(;;){
                            System.out.println("Please provide the amount of coins to transfer:");
                            String amount = scanner.nextLine().trim();
                            try {
                                double amountDouble = Double.parseDouble(amount);
                                if(amountDouble > 0){
                                    clientState.SendTransferMessage(receiver, amountDouble, lockTransfer);
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

                        WaitForLock(lockTransfer);
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

    private static void WaitForLock(Object lock){
        // Start a separate thread for the timer
        Thread timerThread = new Thread(() -> {
            try {
                // Sleep for the desired duration
                Thread.sleep(MAX_TIME_WAIT * 1000);
                synchronized (lock) {
                    // Interrupt the waiting thread after the timeout
                    System.out.println("Timeout: Could not get response in time.");
                    lock.notify(); // Notify the waiting thread
                    
                }
            } catch (InterruptedException e) {
                // Timer thread interrupted, which means the waiting thread was notified before the timeout
            }
        });
        timerThread.start();
    
        synchronized (lock) {
            try {
                lock.wait(); // Wait for the response or timeout
            } catch (InterruptedException e) {
                System.out.println("Waiting thread interrupted");
            } finally {
                // Regardless of whether the waiting thread was interrupted or not,
                // interrupt the timer thread to stop it
                timerThread.interrupt();
            }
        }
    }
}
