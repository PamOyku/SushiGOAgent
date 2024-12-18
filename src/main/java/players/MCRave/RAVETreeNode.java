package players.MCRave;

import core.AbstractGameState;
import core.actions.AbstractAction;
import players.PlayerConstants;
import players.simple.RandomPlayer;
import utilities.ElapsedCpuTimer;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static players.PlayerConstants.*;
import static utilities.Utils.noise;

class RAVETreeNode {
    // Root node of tree
    RAVETreeNode root;
    // Parent of this node
    RAVETreeNode parent;
    // Children of this node
    Map<AbstractAction, RAVETreeNode> children = new HashMap<>();
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

    //delay threshold from switching from random rollout to biassed rollout
    private int delayThreshold = 200;

    protected RAVETreeNode(MCRavePlayer player, RAVETreeNode parent, AbstractGameState state, Random rnd) {
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
        player.resetAMAFData();
        MCRaveParams params = player.getParameters();
        //Console.print(params.AMAFWeight);
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
            RAVETreeNode selected = treePolicy(); // performs selection (using UCBI) and expansion
            // Monte carlo rollout: return value of MC rollout from the newly added node
            double delta = selected.rollOut(numIters); // performs the Monte Carlo simulation setup
            // Back up the value of the rollout through the tree
            selected.backUp(delta); // performs the backpropagation step
            // Finished iteration
            numIters++;
            //Console.print("The current AMAF Size is: "+ player.AMAFCount.size()+"\n");

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
     * - Tree is tAMAFrsed until a node not fully expanded is found.
     * - A new child of this node is added to the tree.
     *
     * @return - new node added to the tree.
     */
    private RAVETreeNode treePolicy() {

        RAVETreeNode cur = this;

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
    private RAVETreeNode expand() {
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
        RAVETreeNode tn = new RAVETreeNode(player, this, nextState, rnd);
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
            RAVETreeNode child = children.get(action);
            if (child == null)
                throw new AssertionError("Should not be here");
            else if (bestAction == null)
                bestAction = action;

            // Find child value
            double hvVal = child.totValue;
            double childValue = hvVal / (child.nVisits + params.epsilon);

            //Get AMAF value and count
            double AMAFValue = player.AMAFValue.getOrDefault(action, 0.0);
            double AMAFCount = player.AMAFCount.getOrDefault(action, 0.0);
            //Combines both original value and AMAF value
            double combinedValue = (1 - alpha) * childValue + alpha * (AMAFValue / (AMAFCount + params.epsilon));

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
        if(player.AMAFCount.containsKey(bestAction)){
            player.RAVECount.put(bestAction,player.AMAFCount.get(bestAction));
        }
        return bestAction;
    }

    /**
     * Perform a Monte Carlo rollout from this node.
     *
     * @return - value of rollout.
     */
    private double rollOut(int numIters) {
        player.currentROActions.clear();
        int rolloutDepth = 0; // Counting from the end of the tree

        // Copy the current state for rollout
        AbstractGameState rolloutState = state.copy();
        if (player.getParameters().rolloutLength > 0) {
            while (!finishRollout(rolloutState, rolloutDepth)) {
                //Console.print("Current rollout depth:"+rolloutDepth+"\n");
                if (numIters < delayThreshold) {
                    // Perform actions normally without biased rollout
                    AbstractAction next = randomPlayer.getAction(rolloutState, randomPlayer.getForwardModel().computeAvailableActions(rolloutState, randomPlayer.parameters.actionSpace));
                    player.currentROActions.add(next);
                    advance(rolloutState, next);
                    //Console.print("Random action: " + next + "\n");

                } else {
                    // Perform biased rollout after the delay threshold is reached
                    AbstractAction next = biasedRollout(rolloutState);
                    player.currentROActions.add(next);
                    advance(rolloutState, next);
                    //Console.print("Bias action: " + next + "\n");
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

    /**
     * Creates a normalised probability across the available, the probabilities calculated via the AMAFValue
     *
     * @param rolloutState - list of currently available actions current gameState
     * @return - a call to the weightedRandomSelect function, that returns the best action based on the probabilites.
     */
    private AbstractAction biasedRollout(AbstractGameState rolloutState) {
        List<AbstractAction> availableActions = player.getForwardModel().computeAvailableActions(rolloutState, player.getParameters().actionSpace);
        Map<AbstractAction, Double> actionProbability = new HashMap<>();
        double totalBias = 0.0;

        // Calculate biases based on AMAF values
        for (AbstractAction action : availableActions) {
            double AMAFValue = player.AMAFValue.getOrDefault(action, 1.0);
            double AMAFCount = player.AMAFCount.getOrDefault(action, 1.0);
            double bias = AMAFValue / (AMAFCount + player.getParameters().epsilon);
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

    /**
     * Applies a normalised probability across each of the available actions calculated by the BiasedRollout then generates
     * a random number and an action is selected when the cumulativeProbability hits that value.
     *
     * @param actions - list of currently available actions
     * @param probabilities - list of biases, generated from AMF Values
     * @return - the action defined by the cumulative probability and random value
     */
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
        if (depth >= player.getParameters().rolloutLength){
            //Console.print("Rollout finished due to max depth: " + depth + "\n");
        return true;
    }
        // End of game
        boolean isTerminal = !rollerState.isNotTerminal();
        if (isTerminal) {
            //Console.print("Rollout finished due to terminal state: " + rollerState + "\n");
        }
        return isTerminal;
    }

    /**
     * Back up the value of the child through all parents. Increase number of visits and total value.
     * Also now incriments the AMAF Count for unused actions and calulcates the AMAFValue
     * @param result - value of rollout to backup
     */
    private void backUp(double result) {
        RAVETreeNode n = this;
        while (n != null) {
            n.nVisits++;
            n.totValue += result;

            // Check if the currentROActions list is not empty before proceeding
            if (!player.currentROActions.isEmpty()) {
                for (AbstractAction action : player.currentROActions) {
                    // Check to see if the AMAFValue has been selected as bestaction before
                    if(!player.RAVECount.containsKey(action)) {
                        player.AMAFCount.put(action, player.AMAFCount.getOrDefault(action, 0.0) + 1);
                        player.AMAFValue.put(action, calculateAMAFValue(action, result, n.nVisits));
                        //Console.print(player.AMAFCount);
                    }else{
                        player.AMAFValue.put(action, calculateAMAFValue(action, result, n.nVisits));
                    }
                }
            } else {
                // Log or handle the case when there are no actions
                System.err.println("Warning: No actions recorded for back up!");
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
                RAVETreeNode node = children.get(action);
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

    /**
     * Calculates the AMAFValue for a specific action, it takes the result of the branch, the amount of visits that node has
     * and the action as parameters, it will then check whether that AMAFValue is
     * @param action - current action
     * @param result       - current depth
     * @param nVisits       - current depth
     * @return - the best AbstractAction
     */
    private double calculateAMAFValue(AbstractAction action, double result, double nVisits) {
        double currentAMAFValue = player.AMAFValue.getOrDefault(action, 1.0);
        double currentAMAFCount;
        if(!player.RAVECount.containsKey(action)) {
             currentAMAFCount = player.AMAFCount.getOrDefault(action, 1.0);
        }else{
             currentAMAFCount = player.RAVECount.getOrDefault(action, 1.0);
        }
        double AMAFDecay =Math.max(0,(currentAMAFCount - nVisits) / (currentAMAFCount));
        double calculatedAMAFValue = currentAMAFValue + (result - currentAMAFValue) / currentAMAFCount;
        return calculatedAMAFValue * AMAFDecay;
    }
}


