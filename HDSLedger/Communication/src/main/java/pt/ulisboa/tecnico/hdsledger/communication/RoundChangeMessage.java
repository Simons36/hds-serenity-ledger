package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.Gson;

public class RoundChangeMessage {

    // Value
    private String preparedValue;

    // Round
    private int preparedRound;

    // Justification (a set of PREPARE messages)
    private List<ConsensusMessage> justification;

    public RoundChangeMessage(String preparedValue, int preparedRound) {
        this.preparedValue = preparedValue;
        this.preparedRound = preparedRound;
        this.justification = new ArrayList<>();
    }

    public RoundChangeMessage(String preparedValue, int preparedRound, List<ConsensusMessage> justification) {
        this.preparedValue = preparedValue;
        this.preparedRound = preparedRound;
        this.justification = justification;
    }

    // getters
    public String getPreparedValue() {
        return preparedValue;
    }

    public int getPreparedRound() {
        return preparedRound;
    }

    public List<ConsensusMessage> getJustification() {
        return justification;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

}
