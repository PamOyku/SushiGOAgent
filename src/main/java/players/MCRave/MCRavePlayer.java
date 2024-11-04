package players.MCRave;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;
import core.interfaces.IStateHeuristic;

import java.util.*;


/**
 * This is a simple version of MCTS that may be useful for newcomers to TAG and MCTS-like algorithms
 * It strips out some of the additional configuration of MCTSPlayer. It uses BasicTreeNode in place of
 * SingleTreeNode.
 */
public class MCRavePlayer extends AbstractPlayer {

    Map<AbstractAction, Double> RAVEValue = new HashMap<>();
    Map<AbstractAction, Double> RAVECount = new HashMap<>();

    List<AbstractAction> currentROActions = new ArrayList<>();

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
        BasicTreeNode root = new BasicTreeNode(this, null, gameState, rnd);

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

    public void resetRAVEData() {
        RAVEValue.clear();
        RAVECount.clear();
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