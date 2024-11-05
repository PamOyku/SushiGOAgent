package players.heuristics;

import core.AbstractGameState;
import core.CoreConstants;
import core.components.Counter;
import core.interfaces.IStateHeuristic;
import games.sushigo.SGGameState;
import games.sushigo.cards.SGCard;
import java.util.Map;
import core.components.Deck;
import java.util.List;

public class AdjustedScoreHeuristic implements IStateHeuristic {

    /**
     * This cares mostly about the raw game score - but will treat winning as a 50% bonus
     * and losing as halving it
     *
     * @param gs       - game state to evaluate and score.
     * @param playerId - player id
     * @return heuristics score
     */


    @Override
    public double evaluateState(AbstractGameState gs, int playerId) {
        double score = gs.getGameScore(playerId);// get current score of player
        double heuristicValue = score;
        if (gs.getPlayerResults()[playerId] == CoreConstants.GameResult.WIN_GAME)
            return score * 1.5;
        else if (gs.getPlayerResults()[playerId] == CoreConstants.GameResult.LOSE_GAME)
            return score * 0.5;

        double playerWeight = 0.8;
        double opponentWeight = 0.2;

        //Card specific heuristics
        if (gs instanceof SGGameState sgState){

            heuristicValue += calculateHistoricalBonuses(sgState, playerId);
            heuristicValue += evaluatePlayerHandContents(sgState, playerId) * playerWeight; // weighted contrubition from players cards.
            heuristicValue += evaluateOpponentHandContents(sgState, playerId) * opponentWeight; // weighted contrubution from opponents cards.
            heuristicValue += evaluatePlayedCards(sgState, playerId) * 0.5; //evaluation of played cards
        }

        return heuristicValue;
    }

    private double calculateHistoricalBonuses(SGGameState sgState, int playerId){

        double bonus= 0.0;

        Map<SGCard.SGCardType, Counter> pointsPerCardType = sgState.getPointsPerCardType()[playerId];

        int makiPoints = pointsPerCardType.getOrDefault(SGCard.SGCardType.Maki, new Counter()).getValue();
        int tempuraPoints = pointsPerCardType.getOrDefault(SGCard.SGCardType.Tempura, new Counter()).getValue();
        int sashimiPoints = pointsPerCardType.getOrDefault(SGCard.SGCardType.Sashimi, new Counter()).getValue();
        int puddingPoints = pointsPerCardType.getOrDefault(SGCard.SGCardType.Pudding, new Counter()).getValue();

        if (makiPoints >= 5) bonus += 3;
        if (tempuraPoints >= 5) bonus += 2;
        if (sashimiPoints >= 10) bonus += 5;
        if (puddingPoints >= 5) bonus += 3;

        return bonus;
    }

    private double evaluatePlayerHandContents(SGGameState sgState, int playerId) {
        double handBonus = 0.0;


        //players hand
        Deck<SGCard> playerHand = sgState.getPlayerHands().get(playerId);
        int makiCount = 0;
        int tempuraCount = 0;
        int sashimiCount = 0;
        int puddingCount = 0;


        for (SGCard card : playerHand.getComponents()) {
            switch (card.type) {
                case Maki -> makiCount++;
                case Tempura -> tempuraCount++;
                case Sashimi -> sashimiCount++;
                case Pudding -> puddingCount++;

            }
        }

        if (makiCount >= 1) handBonus += makiCount * 2; //more maki increases the score
        if (tempuraCount == 1) handBonus += 3;
        if (sashimiCount == 2) handBonus += 6;

        return handBonus;

    }

    private double evaluateOpponentHandContents(SGGameState sgState, int playerId){

        double handBonus = 0.0;

        int makiCount = 0;
        int tempuraCount = 0;
        int sashimiCount = 0;
        int puddingCount = 0;

        for (int opponentId = 0; opponentId < sgState.getNPlayers(); opponentId++){
            if (opponentId != playerId && sgState.hasSeenHand(playerId, opponentId)){

                Deck<SGCard> opponentHand = sgState.getPlayerHands().get(opponentId);
                for (SGCard card : opponentHand.getComponents()){
                    switch (card.type){
                        case Maki -> makiCount++;
                        case Tempura -> tempuraCount++;
                        case Sashimi -> sashimiCount++;
                        case Pudding -> puddingCount++;
                    }
                }
            }
        }
        if (makiCount >= 1) handBonus += makiCount * 2; //more maki increases the score
        if (tempuraCount == 1) handBonus +=3;
        if (sashimiCount == 2) handBonus +=6;

        return handBonus;
    }

    private double evaluatePlayedCards(SGGameState sgState, int playerId){
        double playedCardsBonus = 0.0;
        List<Deck<SGCard>>playedCards = sgState.getPlayedCards();
        Deck<SGCard> playerPlayedCards = playedCards.get(playerId);


        int tempuraPlayed = 0;
        int sashimiPlayed = 0;
        int makiPlayed = 0;


        for (SGCard card : playerPlayedCards.getComponents()){
            switch (card.type){
                case Tempura -> tempuraPlayed++;
                case Sashimi -> sashimiPlayed++;
                case Maki -> makiPlayed++;
            }
        }

        if (tempuraPlayed >= 2) playedCardsBonus += 5;
        if (sashimiPlayed >= 3) playedCardsBonus += 10;
        if (makiPlayed >= 3) playedCardsBonus += makiPlayed * 1.5;

        return playedCardsBonus;
    }


    @Override
    public double minValue() {
        return Double.NEGATIVE_INFINITY;
    }
    @Override
    public double maxValue() {
        return Double.POSITIVE_INFINITY;
    }
}
