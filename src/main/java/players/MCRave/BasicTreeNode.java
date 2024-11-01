package players.MCRave;

import core.AbstractGameState;
import core.actions.AbstractAction;
import org.apache.spark.sql.execution.columnar.FLOAT;
import players.PlayerConstants;
import players.simple.RandomPlayer;
import scala.Console;
import scala.collection.immutable.Stream;
import utilities.ElapsedCpuTimer;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static players.PlayerConstants.*;
import static utilities.Utils.noise;

class BasicTreeNode {
    // Root node of tree
    BasicTreeNode root;
    // Parent of this node
    BasicTreeNode parent;
    // Children of this node
    Map<AbstractAction, BasicTreeNode> children = new HashMap<>();
    // Depth of this node
    final int depth;

    // Total value of this node
    private double totValue;
    // Number of visits
    private int nVisits;
    // Number of FM calls and State copies up until this node
    private int fmCallsCount;
    // Parameters guiding the search
    private MCRavePlayer player;
    private Random rnd;
    private RandomPlayer randomPlayer = new RandomPlayer();

    // State in this node (closed loop)
    private AbstractGameState state;

    //Dictionary that binds RAVEValue to each GameState
    private Map<AbstractAction, Double> RAVEValue = new HashMap<>();

    //Dictionary that binds RAVECount to each GameState
    private Map<AbstractAction, Double> RAVECount = new HashMap<>();

    private List<AbstractAction> currentROActions = new ArrayList<>();

    private int delayThreshold = 350;

    protected BasicTreeNode(MCRavePlayer player, BasicTreeNode parent, AbstractGameState state, Random rnd) {
        this.player = player;
        this.fmCallsCount = 0;
        this.parent = parent;
        this.root = parent == null ? this : parent.root;
        totValue = 0.0;
        setState(state);
        if (parent != null) {
            depth = parent.depth + 1;
        } else {
            depth = 0;
        }
        this.rnd = rnd;
        randomPlayer.setForwardModel(player.getForwardModel());
    }

    /**
     * Performs full MCTS search, using the defined budget limits.
     */
    void mctsSearch() {

        MCRaveParams params = player.getParameters();

        // Variables for tracking time budget
        double avgTimeTaken;
        double acumTimeTaken = 0;
        long remaining;
        int remainingLimit = params.breakMS;
        ElapsedCpuTimer elapsedTimer = new ElapsedCpuTimer();
        if (params.budgetType == BUDGET_TIME) {
            elapsedTimer.setMaxTimeMillis(params.budget);
        }

        // Tracking number of iterations for iteration budget
        int numIters = 0;

        boolean stop = false;

        while (!stop) {
            // New timer for this iteration
            ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();

            // Selection + expansion: navigate tree until a node not fully expanded is found, add a new node to the tree
            BasicTreeNode selected = treePolicy(); // performs selection (using UCBI) and expansion
            // Monte carlo rollout: return value of MC rollout from the newly added node
            double delta = selected.rollOut(numIters); // performs the Monte Carlo simulation setup
            // Back up the value of the rollout through the tree
            selected.backUp(delta); // performs the backpropagation step
            // Finished iteration
            numIters++;
            Console.print("The current RAVE Size is: "+RAVECount.size()+"\n");

            // Check stopping condition
            PlayerConstants budgetType = params.budgetType;
            if (budgetType == BUDGET_TIME) {
                // Time budget
                acumTimeTaken += (elapsedTimerIteration.elapsedMillis());
                avgTimeTaken = acumTimeTaken / numIters;
                remaining = elapsedTimer.remainingTimeMillis();
                stop = remaining <= 2 * avgTimeTaken || remaining <= remainingLimit;
            } else if (budgetType == BUDGET_ITERATIONS) {
                // Iteration budget
                stop = numIters >= params.budget;
            } else if (budgetType == BUDGET_FM_CALLS) {
                // FM calls budget
                stop = fmCallsCount > params.budget;
            }
        }
    }

    /**
     * Selection + expansion steps.
     * - Tree is traversed until a node not fully expanded is found.
     * - A new child of this node is added to the tree.
     *
     * @return - new node added to the tree.
     */
    private BasicTreeNode treePolicy() {

        BasicTreeNode cur = this;

        // Keep iterating while the state reached is not terminal and the depth of the tree is not exceeded
        while (cur.state.isNotTerminal() && cur.depth < player.getParameters().maxTreeDepth) {
            if (!cur.unexpandedActions().isEmpty()) {
                // We have an unexpanded action
                cur = cur.expand();
                return cur;
            } else {
                // Move to next child given by UCT function
                AbstractAction actionChosen = cur.ucb();
                cur = cur.children.get(actionChosen);
            }
        }

        return cur;
    }


    private void setState(AbstractGameState newState) {
        state = newState;
        if (newState.isNotTerminal())
            for (AbstractAction action : player.getForwardModel().computeAvailableActions(state, player.getParameters().actionSpace)) {
                children.put(action, null); // mark a new node to be expanded
            }
    }

    /**
     * @return A list of the unexpanded Actions from this State
     */
    private List<AbstractAction> unexpandedActions() {
        return children.keySet().stream().filter(a -> children.get(a) == null).collect(toList());
    }

    /**
     * Expands the node by creating a new random child node and adding to the tree.
     *
     * @return - new child node.
     */
    private BasicTreeNode expand() {
        // Find random child not already created
        Random r = new Random(player.getParameters().getRandomSeed());
        // pick a random unchosen action
        List<AbstractAction> notChosen = unexpandedActions();
        AbstractAction chosen = notChosen.get(r.nextInt(notChosen.size()));

        // copy the current state and advance it using the chosen action
        // we first copy the action so that the one stored in the node will not have any state changes
        AbstractGameState nextState = state.copy();
        advance(nextState, chosen.copy());

        // then instantiate a new node
        BasicTreeNode tn = new BasicTreeNode(player, this, nextState, rnd);
        children.put(chosen, tn);
        return tn;
    }

    /**
     * Advance the current game state with the given action, count the FM call and compute the next available actions.
     *
     * @param gs  - current game state
     * @param act - action to apply
     */
    private void advance(AbstractGameState gs, AbstractAction act) {
        player.getForwardModel().next(gs, act);
        root.fmCallsCount++;
    }

    private AbstractAction ucb() {
        // Find child with highest UCB value, maximising for ourselves and minimizing for opponent
        AbstractAction bestAction = null;
        double bestValue = -Double.MAX_VALUE;
        MCRaveParams params = player.getParameters();
        double alpha = params.raveWeight;

        for (AbstractAction action : children.keySet()) {
            BasicTreeNode child = children.get(action);
            if (child == null)
                throw new AssertionError("Should not be here");
            else if (bestAction == null)
                bestAction = action;

            // Find child value
            double hvVal = child.totValue;
            double childValue = hvVal / (child.nVisits + params.epsilon);

            //Get RAVE value and count
            double raveValue = RAVEValue.getOrDefault(action, 0.0);
            double raveCount = RAVECount.getOrDefault(action, 0.0);

            //Combines both original value and RAVE value
            double combinedValue = (1 - alpha) * childValue + alpha * (raveValue / (raveCount + params.epsilon));

            // default to standard UCB
            double explorationTerm = params.K * Math.sqrt(Math.log(this.nVisits + 1) / (child.nVisits + params.epsilon));
            // unless we are using a variant

            // Find 'UCB' value
            // If 'we' are taking a turn we use classic UCB
            // If it is an opponent's turn, then we assume they are trying to minimise our score (with exploration)
            boolean iAmMoving = state.getCurrentPlayer() == player.getPlayerID();
            double uctValue = iAmMoving ? combinedValue : -combinedValue;
            uctValue += explorationTerm;

            // Apply small noise to break ties randomly
            uctValue = noise(uctValue, params.epsilon, player.getRnd().nextDouble());

            // Assign value
            if (uctValue > bestValue) {
                bestAction = action;
                bestValue = uctValue;
            }
        }

        if (bestAction == null)
            throw new AssertionError("We have a null value in UCT : shouldn't really happen!");

        root.fmCallsCount++;  // log one iteration complete
        return bestAction;
    }

    /**
     * Perform a Monte Carlo rollout from this node.
     *
     * @return - value of rollout.
     */
    private double rollOut(int numIters) {
        currentROActions.clear();
        int rolloutDepth = 0; // Counting from the end of the tree

        // Copy the current state for rollout
        AbstractGameState rolloutState = state.copy();
        if (player.getParameters().rolloutLength > 0) {
            while (!finishRollout(rolloutState, rolloutDepth)) {
                //Console.print("Current rollout depth:"+rolloutDepth+"\n");
                if (numIters < delayThreshold) {
                    // Perform actions normally without biased rollout
                    AbstractAction next = randomPlayer.getAction(rolloutState, randomPlayer.getForwardModel().computeAvailableActions(rolloutState, randomPlayer.parameters.actionSpace));
                    advance(rolloutState, next);
                    Console.print("Random"+"\n");
                } else {
                    // Perform biased rollout after the delay threshold is reached
                    AbstractAction next = biasedRollout(rolloutState);
                    currentROActions.add(next);
                    advance(rolloutState, next);
                    Console.print("Bias"+"\n");
                }
                rolloutDepth++;
            }
            //Console.print("Current number of iteration:"+numIters+"\n");
        }

        // Evaluate final state and return normalized score
        double value = player.getParameters().getHeuristic().evaluateState(rolloutState, player.getPlayerID());
        if (Double.isNaN(value)) {
            throw new AssertionError("Illegal heuristic value - should be a number");
        }
        return value;
    }

    private AbstractAction biasedRollout(AbstractGameState rolloutState) {
        List<AbstractAction> availableActions = player.getForwardModel().computeAvailableActions(rolloutState, player.getParameters().actionSpace);
        Map<AbstractAction, Double> actionProbability = new HashMap<>();
        double totalBias = 0.0;

        // Calculate biases based on RAVE values
        for (AbstractAction action : availableActions) {
            double raveValue = RAVEValue.getOrDefault(action, 0.0);
            double raveCount = RAVECount.getOrDefault(action, 1.0);
            double bias = raveValue / (raveCount + player.getParameters().epsilon);
            actionProbability.put(action, bias);
            totalBias += bias;
        }

        // Normalize probabilities
        for (AbstractAction action : actionProbability.keySet()) {
            actionProbability.put(action, actionProbability.get(action) / totalBias);
        }

        // Select an action based on the calculated probabilities
        return weightedRandomSelect(availableActions, new ArrayList<>(actionProbability.values()));
    }

    private AbstractAction weightedRandomSelect(List<AbstractAction> actions, List<Double> probabilities) {
        double r = Math.random();
        double cumulativeProbability = 0.0;

        for (int i = 0; i < actions.size(); i++) {
            cumulativeProbability += probabilities.get(i);
            if (r <= cumulativeProbability) {
                return actions.get(i);
            }
        }
        return actions.get(actions.size() - 1); // Fallback to the last action
    }

    /**
     * Checks if rollout is finished. Rollouts end on maximum length, or if game ended.
     *
     * @param rollerState - current state
     * @param depth       - current depth
     * @return - true if rollout finished, false otherwise
     */
    private boolean finishRollout(AbstractGameState rollerState, int depth) {
        if (depth >= player.getParameters().rolloutLength)
            return true;

        // End of game
        return !rollerState.isNotTerminal();
    }

    /**
     * Back up the value of the child through all parents. Increase number of visits and total value.
     *
     * @param result - value of rollout to backup
     */
    private void backUp(double result) {
        BasicTreeNode n = this;
        while (n != null) {
            n.nVisits++;
            n.totValue += result;

            // Check if the currentROActions list is not empty before proceeding
            if (!n.currentROActions.isEmpty()) {
                for (AbstractAction action : n.currentROActions) {
                    RAVECount.put(action, RAVECount.getOrDefault(action, 0.0) + 1);
                    RAVEValue.put(action, calculateRAVEValue(action, result));
                }
            } else {
                // Log or handle the case when there are no actions
                //System.err.println("Warning: No actions recorded for back up!");
            }

            n = n.parent;
        }
    }

    /**
     * Calculates the best action from the root according to the most visited node
     *
     * @return - the best AbstractAction
     */
    AbstractAction bestAction() {

        double bestValue = -Double.MAX_VALUE;
        AbstractAction bestAction = null;

        for (AbstractAction action : children.keySet()) {
            if (children.get(action) != null) {
                BasicTreeNode node = children.get(action);
                double childValue = node.nVisits;

                // Apply small noise to break ties randomly
                childValue = noise(childValue, player.getParameters().epsilon, player.getRnd().nextDouble());

                // Save best value (highest visit count)
                if (childValue > bestValue) {
                    bestValue = childValue;
                    bestAction = action;
                }
            }
        }

        if (bestAction == null) {
            throw new AssertionError("Unexpected - no selection made.");
        }

        return bestAction;
    }

    private double calculateRAVEValue(AbstractAction action, double result) {
        double currentRAVEValue = RAVEValue.getOrDefault(action, 0.0);
        double currentRAVECount = RAVECount.getOrDefault(action, 0.0);
        return (currentRAVEValue * currentRAVECount + result) / (currentRAVECount + 1);
    }
}


