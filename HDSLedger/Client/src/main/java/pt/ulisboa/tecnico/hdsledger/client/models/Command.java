package pt.ulisboa.tecnico.hdsledger.client.models;

import java.util.List;

public class Command {
    private String command;
    private List<Object> arguments;

    // Getters and setters

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public List<Object> getArguments() {
        return arguments;
    }

    public void setArguments(List<Object> arguments) {
        this.arguments = arguments;
    }
}
