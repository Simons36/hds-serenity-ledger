package pt.ulisboa.tecnico.hdsledger.client.enums;

import java.util.HashMap;
import java.util.Map;

public enum CommandType {

    HELP("help", "Prints this message", ""),
    APPEND("append", "Append a simple string to the blockchain.",  "Usage: append <message>"),
    EXIT("exit", "Exit the application.",  "Usage: exit");

    private final String command;
    private final String commandDescription;
    private final String commandUsage;

    CommandType(String command, String commandDescription, String commandUsage) {
        this.command = command;
        this.commandDescription = commandDescription;
        this.commandUsage = commandUsage;
    }

    public String getCommand() {
        return command;
    }

    public String getCommandDescription() {
        return commandDescription;
    }

    public String getCommandUsage() {
        return commandUsage;
    }

    // Static method to create Commands enum from a string
    public static CommandType fromString(String input) {
        for (CommandType command : CommandType.values()) {
            if (command.getCommand().equalsIgnoreCase(input)) {
                return command;
            }
        }
        // Handle case where the input string doesn't match any enum constant
        throw new IllegalArgumentException("No enum constant with command: " + input);
    }

    public static String getHelpMessage(){
        StringBuilder sb = new StringBuilder();
        for (CommandType command : CommandType.values()) {
            sb.append(command.getCommand()).append(" - ").append(command.getCommandDescription()).append("\n");
        }
        sb.deleteCharAt(sb.length() - 1); // Remove last newline character (to avoid extra newline at the end of the message)
        return sb.toString();
    }
}
