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

public class ScoreHeuristic implements IStateHeuristic {

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
            heuristicValue += evaluateStrategy(sgState, playerId);//for collection strategy
        }

        return heuristicValue;
    }

    private double calculateHistoricalBonuses(SGGameState sgState, int playerId){

        double bonus= 0.0;

        Map<SGCard.SGCardType, Counter> pointsPerCardType = sgState.getPointsPerCardType()[playerId];

        int makiPoints = pointsPerCardType.getOrDefault(SGCard.SGCardType.Maki, new Counter()).getValue();
        int tempuraPoints = pointsPerCardType.getOrDefault(SGCard.SGCardType.Tempura, new Counter()).getValue();
        int sashimiPoints = pointsPerCardType.getOrDefault(SGCard.SGCardType.Sashimi, new Counter()).getValue();
        int squidNigiriPoints = pointsPerCardType.getOrDefault(SGCard.SGCardType.SquidNigiri, new Counter()).getValue();
        int salmonNigiriPoints = pointsPerCardType.getOrDefault(SGCard.SGCardType.SalmonNigiri, new Counter()).getValue();
        int eggNigiriPoints = pointsPerCardType.getOrDefault(SGCard.SGCardType.EggNigiri, new Counter()).getValue();
        int wasabiPoints = pointsPerCardType.getOrDefault(SGCard.SGCardType.Wasabi, new Counter()).getValue();
        int chopsticksPoints = pointsPerCardType.getOrDefault(SGCard.SGCardType.Chopsticks, new Counter()).getValue();
        int puddingPoints = pointsPerCardType.getOrDefault(SGCard.SGCardType.Pudding, new Counter()).getValue();

        if (makiPoints >= 5){
            bonus += 3;
        }
        if (tempuraPoints % 2 == 0 && tempuraPoints > 0){
            bonus += (tempuraPoints / 2) * 5; // Pair bonus for Tempura
        }
        if (sashimiPoints % 3 == 0 && sashimiPoints > 0){
            bonus += (sashimiPoints / 3) * 10; // Triplet bonus for Sashimi
        }
        if (salmonNigiriPoints > 0){
            bonus += salmonNigiriPoints * 2;
        }
        if (squidNigiriPoints > 0){
            bonus += squidNigiriPoints * 3;
        }
        if (eggNigiriPoints > 0){
            bonus += eggNigiriPoints;
        }
        if (wasabiPoints > 0) {
            bonus += wasabiPoints * 2;
        }
        if (chopsticksPoints > 0){
            bonus += chopsticksPoints;
        }
        if (puddingPoints >= 5){
            bonus += 3;
        }

        return bonus;
    }

    private double evaluatePlayerHandContents(SGGameState sgState, int playerId) {
        double handBonus = 0.0;


        //players hand
        Deck<SGCard> playerHand = sgState.getPlayerHands().get(playerId);
        int makiCount = 0;
        int tempuraCount = 0;
        int sashimiCount = 0;
        int squidNigiriCount = 0;
        int salmonNigiriCount = 0;
        int eggNigiriCount = 0;
        int wasabiCount = 0;
        int chopsticksCount = 0;
        int puddingCount = 0;


        for (SGCard card : playerHand.getComponents()) {
            switch (card.type) {
                case Maki:
                    makiCount++;
                    break;
                case Tempura:
                    tempuraCount++;
                    break;
                case Sashimi:
                    sashimiCount++;
                    break;
                case EggNigiri:
                    eggNigiriCount++;
                    break;
                case SquidNigiri:
                    squidNigiriCount++;
                    break;
                case SalmonNigiri:
                    salmonNigiriCount++;
                    break;
                case Pudding:
                    puddingCount++;
                    break;


            }
        }

        if (makiCount >= 1) {
            handBonus += makiCount * 2; //more maki increases the score
        }
        if (tempuraCount == 1) {
            handBonus += 3;
        }
        if (sashimiCount == 2){
            handBonus += 6;
        }
        if (squidNigiriCount >= 1){
            handBonus += squidNigiriCount * 3; //squid has higher value
        }
        if (wasabiCount >= 1){
            handBonus += wasabiCount * 2; // wasabi increases nigiri score
        }
        if (chopsticksCount >= 1){
            handBonus += chopsticksCount * 1; // chopsticks provide flexibility
        }

        return handBonus;

    }

    private double evaluateOpponentHandContents(SGGameState sgState, int playerId){

        double handBonus = 0.0;

        int makiCount = 0;
        int tempuraCount = 0;
        int sashimiCount = 0;
        int squidNigiriCount = 0;
        int salmonNigiriCount = 0;
        int eggNigiriCount = 0;
        int wasabiCount = 0;
        int chopsticksCount = 0;
        int puddingCount = 0;

        for (int opponentId = 0; opponentId < sgState.getNPlayers(); opponentId++){
            if (opponentId != playerId && sgState.hasSeenHand(playerId, opponentId)){

                Deck<SGCard> opponentHand = sgState.getPlayerHands().get(opponentId);
                for (SGCard card : opponentHand.getComponents()){
                    switch (card.type){
                        case Maki:
                            makiCount++;
                            break;
                        case Tempura:
                            tempuraCount++;
                            break;
                        case Sashimi:
                            sashimiCount++;
                            break;
                        case SquidNigiri:
                            squidNigiriCount++;
                            break;
                        case SalmonNigiri:
                            salmonNigiriCount++;
                            break;
                        case EggNigiri:
                            eggNigiriCount++;
                            break;
                        case Wasabi:
                            wasabiCount++;
                            break;
                        case Chopsticks:
                            chopsticksCount++;
                            break;
                        case Pudding:
                            puddingCount++;
                            break;
                    }
                }
            }
        }
        if (makiCount >= 1){
            handBonus += makiCount * 2; //more maki increases the score
        }
        if (tempuraCount == 1){
            handBonus +=3;
        }
        if (sashimiCount == 2){
            handBonus +=6;
        }
        if (squidNigiriCount >= 1){
            handBonus += squidNigiriCount * 3; //squid has higher value
        }
        if (wasabiCount >= 1){
            handBonus += wasabiCount * 2; // wasabi increases nigiri score
        }
        if (chopsticksCount >= 1){
            handBonus += chopsticksCount * 1; // chopsticks provide flexibility
        }


        return handBonus;
    }

    private double evaluatePlayedCards(SGGameState sgState, int playerId){
        double playedCardsBonus = 0.0;
        List<Deck<SGCard>>playedCards = sgState.getPlayedCards();
        Deck<SGCard> playerPlayedCards = playedCards.get(playerId);


        int tempuraPlayed = 0;
        int sashimiPlayed = 0;
        int makiPlayed = 0;
        int squidNigiriPlayed = 0;
        int salmonNigiriPlayed = 0;
        int eggNigiriPlayed = 0;
        int wasabiPlayed = 0;
        int chopsticksPlayed = 0;


        for (SGCard card : playerPlayedCards.getComponents()){
            switch (card.type){
                case Tempura:
                    tempuraPlayed++;
                    break;
                case Sashimi:
                    sashimiPlayed++;
                    break;
                case Maki:
                    makiPlayed++;
                    break;
                case SquidNigiri:
                    squidNigiriPlayed++;
                    break;
                case SalmonNigiri:
                    salmonNigiriPlayed++;
                    break;
                case EggNigiri:
                    eggNigiriPlayed++;
                    break;
                case Wasabi:
                    wasabiPlayed++;
                    break;
                case Chopsticks:
                    chopsticksPlayed++;
                    break;
            }
        }

        if (tempuraPlayed >= 2){
            playedCardsBonus += 5;
        }
        if (sashimiPlayed >= 3){
            playedCardsBonus += 10;
        }
        if (makiPlayed >= 3){
            playedCardsBonus += makiPlayed * 1.5;// for multiple
        }
        if (squidNigiriPlayed >= 1){
            playedCardsBonus += squidNigiriPlayed * 3;
        }
        if (salmonNigiriPlayed >= 1){
            playedCardsBonus += salmonNigiriPlayed * 2;
        }

        return playedCardsBonus;
    }

    private double evaluateStrategy(SGGameState sgState, int playerId){
        double collectionBonus = 0.0;

        Map<SGCard.SGCardType, Counter>[] pointsPerCardTypeAll = sgState.getPointsPerCardType();
        Map<SGCard.SGCardType, Counter> pointsPerCardType = pointsPerCardTypeAll[playerId];
        int[] totalCardCounts = new int[SGCard.SGCardType.values().length];

        for ( int i = 0;i < sgState.getNPlayers(); i++){
            if ( i != playerId){
                for (SGCard.SGCardType cardType : SGCard.SGCardType.values()){
                    totalCardCounts[cardType.ordinal()] += pointsPerCardTypeAll[i].getOrDefault(cardType, new Counter()).getValue();

                }
            }
        }

        int playerMakiCount = pointsPerCardType.getOrDefault(SGCard.SGCardType.Maki, new Counter()).getValue();
        if (playerMakiCount > totalCardCounts[SGCard.SGCardType.Maki.ordinal()]) {
            collectionBonus += 5; // Bonus for having more Maki rolls than any other player
        }

        int playerPuddingCount = pointsPerCardType.getOrDefault(SGCard.SGCardType.Pudding, new Counter()).getValue();
        if (playerPuddingCount > totalCardCounts[SGCard.SGCardType.Pudding.ordinal()]) {
            collectionBonus += 3; // Bonus for having more Pudding cards
        }

        int playerTempuraCount = pointsPerCardType.getOrDefault(SGCard.SGCardType.Tempura, new Counter()).getValue();
        if (playerTempuraCount % 2 == 0 && playerTempuraCount > 0) {
            collectionBonus += (playerTempuraCount / 2) * 5; // Pair bonus for Tempura
        }

        int playerSashimiCount = pointsPerCardType.getOrDefault(SGCard.SGCardType.Sashimi, new Counter()).getValue();
        if (playerSashimiCount % 3 == 0 && playerSashimiCount > 0) {
            collectionBonus += (playerSashimiCount / 3) * 10; // Triplet bonus for Sashimi
        }


        int playerWasabiCount = pointsPerCardType.getOrDefault(SGCard.SGCardType.Wasabi, new Counter()).getValue();
        collectionBonus += playerWasabiCount * 2; // Bonus for Wasabi

        int playerChopsticksCount = pointsPerCardType.getOrDefault(SGCard.SGCardType.Chopsticks, new Counter()).getValue();
        collectionBonus += playerChopsticksCount * 1; // Bonus for Chopsticks

        return collectionBonus;

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
