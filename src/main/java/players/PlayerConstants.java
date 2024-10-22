package players;

/**
 * We have five distinct budgets to constrain MCTS:
 * - TIME is measured in milliseconds
 * - ITERATIONS is self-explanatory
 * - FM_CALLS limits the number if times the ForwardModel.next() function can be called
 * - COPY_CALLS limits the number of times a state can be copied (this is frequently quite a bit more time-consuming
 *   then ForwardModel.next(), so for some games FM directly may be an unfair comparator
 * - FMANDCOPY_CALLS limits the sum of the calls to copy() or next()
 */
public enum PlayerConstants {
    BUDGET_TIME, //Sets the limit on the number of milliseconds the agent can use to make a decision
    BUDGET_ITERATIONS, //Sets the limit on the number of iterations of the algorithm
    BUDGET_FM_CALLS, //Sets the limit on the number of times the model can be rolled forward
    BUDGET_FMANDCOPY_CALLS,
    BUDGET_COPY_CALLS;
}
