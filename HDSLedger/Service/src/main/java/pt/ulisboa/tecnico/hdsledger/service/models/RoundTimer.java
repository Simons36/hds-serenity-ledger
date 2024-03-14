package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.Timer;
import java.util.TimerTask;

import pt.ulisboa.tecnico.hdsledger.service.services.NodeService;

public class RoundTimer {
    
    private Timer timer = new Timer();

    private int roundNumber;

    private int consensusInstance;

    private NodeService nodeService;

    public RoundTimer(int secondsToReset, int roundNumber, int consensusInstance, NodeService nodeService) {
        this.roundNumber = roundNumber;
        this.nodeService = nodeService;
        this.consensusInstance = consensusInstance;
        configureTimer(secondsToReset);

    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public int getConsensusInstance() {
        return consensusInstance;
    }

    public void configureTimer(int secondsToReset) {
        RoundTimer roundTimer = this;

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                nodeService.timerReset(roundTimer);
            }
        }, secondsToReset * 1000, secondsToReset * 1000);
    }

    public void cancelTimer() {
        timer.cancel();
    }

}
