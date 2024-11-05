package players.MCRave;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;
import core.interfaces.IStateHeuristic;

import java.util.*;



public class MCRavePlayer extends AbstractPlayer {

    Map<AbstractAction, Double> AMAFValue = new HashMap<>(); //This contains the AMAF Values for all states
    Map<AbstractAction, Double> AMAFCount = new HashMap<>(); //This contains the count for appearances for all states
    Map<AbstractAction, Double> RAVECount = new HashMap<>(); //This contains the AMAFValue's that have already been selected as best action
    List<AbstractAction> currentROActions = new ArrayList<>(); //This contains all the actions in the current rollout

    public MCRavePlayer() {
        this(System.currentTimeMillis());
    }

    public MCRavePlayer(long seed) {
        super(new MCRaveParams(), "MCRave");
        // for clarity we create a new set of parameters here, but we could just use the default parameters
        parameters.setRandomSeed(seed);
        rnd = new Random(seed);


        // These parameters can be changed, and will impact the Basic MCTS algorithm
        MCRaveParams params = getParameters();


    }

    public MCRavePlayer(MCRaveParams params) {
        super(params, "MCRave");
        rnd = new Random(params.getRandomSeed());
    }

    @Override
    public AbstractAction _getAction(AbstractGameState gameState, List<AbstractAction> actions) {
        // Search for best action from the root
        RAVETreeNode root = new RAVETreeNode(this, null, gameState, rnd);

        // mctsSearch does all of the hard work
        root.mctsSearch();

        // Return best action
        return root.bestAction();
    }

    @Override
    public MCRaveParams getParameters() {
        return (MCRaveParams) parameters;
    }

    public void setStateHeuristic(IStateHeuristic heuristic) {
        getParameters().heuristic = heuristic;
    }

    //Resets all the AMAF Maps
    public void resetAMAFData() {
        AMAFValue.clear();
        RAVECount.clear();
        AMAFCount.clear();
    }

    @Override
    public String toString() {
        return "MCRave";
    }

    @Override
    public MCRavePlayer copy() {
        return this;
    }
}