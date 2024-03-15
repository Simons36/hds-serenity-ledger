package pt.ulisboa.tecnico.hdsledger.service.models;


import java.util.ArrayList;
import java.util.List;

import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.service.services.NodeService;

public class InstanceInfo {

    private int currentRound = 1;
    private int preparedRound = -1;
    private String preparedValue;
    private CommitMessage commitMessage;
    private String inputValue;
    private int committedRound = -1;

    //Timer with time left for current round to expire
    private RoundTimer roundTimer;

    public InstanceInfo(String inputValue) {
        this.inputValue = inputValue;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }

    public int getPreparedRound() {
        return preparedRound;
    }

    public void setPreparedRound(int preparedRound) {
        this.preparedRound = preparedRound;
    }

    public String getPreparedValue() {
        return preparedValue;
    }

    public void setPreparedValue(String preparedValue) {
        this.preparedValue = preparedValue;
    }

    public String getInputValue() {
        return inputValue;
    }

    public void setInputValue(String inputValue) {
        this.inputValue = inputValue;
    }

    public int getCommittedRound() {
        return committedRound;
    }

    public void setCommittedRound(int committedRound) {
        this.committedRound = committedRound;
    }

    public CommitMessage getCommitMessage() {
        return commitMessage;
    }

    public void setCommitMessage(CommitMessage commitMessage) {
        this.commitMessage = commitMessage;
    }

    public void StartTimerForCurrentRound(int secondsToReset, int consensusInstance, NodeService nodeService){
        roundTimer = new RoundTimer(secondsToReset, currentRound, consensusInstance,  nodeService);
    }

    public void cancelTimer(){
        roundTimer.cancelTimer();
    }

    public void resetTimer(int secondsToReset, int consensusInstance, NodeService nodeService){
        roundTimer.cancelTimer();
        roundTimer = new RoundTimer(secondsToReset, currentRound, consensusInstance,  nodeService);
    }

    public int incrementRound(){
        return ++currentRound;
    }

}
