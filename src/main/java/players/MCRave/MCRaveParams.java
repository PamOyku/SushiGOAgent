package players.MCRave;

import core.AbstractGameState;
import core.interfaces.IStateHeuristic;
import players.PlayerParameters;

import java.util.Arrays;


public class MCRaveParams extends PlayerParameters {

    public double K = Math.sqrt(2); // UCBI Exploration constant
    public int rolloutLength = 200; // assuming we have a good heuristic (Max length of the rollouts)
    public int maxTreeDepth = 100; // effectively no limit (Max length the tree can grow)
    public double raveWeight = 0.7;
    public double raveDecay = 0.95;
    public double epsilon = 1e-6;
    public IStateHeuristic heuristic = AbstractGameState::getHeuristicScore;

    public MCRaveParams() {
        addTunableParameter("K", Math.sqrt(2), Arrays.asList(0.0, 0.1, 1.0, Math.sqrt(2), 3.0, 10.0));
        addTunableParameter("rolloutLength", 10, Arrays.asList(0, 3, 10, 30, 100));
        addTunableParameter("maxTreeDepth", 100, Arrays.asList(1, 3, 10, 30, 100));
        addTunableParameter("epsilon", 1e-6);
        addTunableParameter("heuristic", (IStateHeuristic) AbstractGameState::getHeuristicScore);
        addTunableParameter("raveWeight",0.5,Arrays.asList(0.0,0.1,0.3,0.5,1.0));
    }

    @Override
    public void _reset() {
        super._reset();
        K = (double) getParameterValue("K");
        rolloutLength = (int) getParameterValue("rolloutLength");
        maxTreeDepth = (int) getParameterValue("maxTreeDepth");
        epsilon = (double) getParameterValue("epsilon");
        heuristic = (IStateHeuristic) getParameterValue("heuristic");
    }

    @Override
    protected MCRaveParams _copy() {
        // All the copying is done in TunableParameters.copy()
        // Note that any *local* changes of parameters will not be copied
        // unless they have been 'registered' with setParameterValue("name", value)
        return new MCRaveParams();
    }

    public IStateHeuristic getHeuristic() {
        return heuristic;
    }

    @Override
    public MCRavePlayer instantiate() {
        return new MCRavePlayer((MCRaveParams) this.copy());
    }

}
