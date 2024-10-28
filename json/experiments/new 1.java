  private double rollOut() {
        currentROActions.clear();
        int rolloutDepth = 0; // counting from end of tree

        // If rollouts are enabled, select actions for the rollout in line with the rollout policy
        AbstractGameState rolloutState = state.copy();
        if (player.getParameters().rolloutLength > 0) {
            while (!finishRollout(rolloutState, rolloutDepth)) {
              List<AbstractAction> availableActions = player.getForwardModel().computeAvailableActions(rolloutState,player.getParameters().actionSpace);
              Map<AbstractAction,Double> actionProbablilty = new HashMap<>();
                double totalBias = 0.0;
                for (AbstractAction action: availableActions) {
                    double raveValue = RAVEValue.getOrDefault(action ,0.0);
                    double raveCount = RAVECount.getOrDefault(action, 1.0);
                    double bias = raveValue/(raveCount+ player.getParameters().epsilon);
                    actionProbablilty.put(action,bias);
                    totalBias += bias;
                }
                for(AbstractAction action: actionProbablilty.keySet()){
                    actionProbablilty.put(action,(actionProbablilty.get(action)/totalBias));
                }

                double actionBound = Math.random();
                double currentProb = 0.0;
                for(AbstractAction action: actionProbablilty.keySet()){
                    currentProb += actionProbablilty.get(action);
                    if(currentProb>= actionBound){
                        currentROActions.add(action);
                        advance(rolloutState, action);
                        rolloutDepth++;
                        break;
                    }
                }
                
                /*
                AbstractAction next = randomPlayer.getAction(rolloutState, randomPlayer.getForwardModel().computeAvailableActions(rolloutState, randomPlayer.parameters.actionSpace));
                currentROActions.add(next);
                advance(rolloutState, next); */


            }
        }
        // Evaluate final state and return normalised score
        double value = player.getParameters().getHeuristic().evaluateState(rolloutState, player.getPlayerID());
        if (Double.isNaN(value))
            throw new AssertionError("Illegal heuristic value - should be a number");
        return value;
    }

