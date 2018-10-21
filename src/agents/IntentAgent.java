package agents;

import hanabAI.*;
import java.util.Arrays;

/**
 * An interface for representing the strategy of a player in Hanabi
 * */
public class IntentAgent implements Agent {

  class MindState {
    public int knownRank;
    public Colour knownColour;
    public int[][] state;

    public MindState(int[] spread) {
      //init MindState from default spread
      state = new int[5][];
      for (int i = 0; i < 5; ++i) {
        state[i] = Arrays.copyOf(spread, spread.length);
      }
    }

    public MindState(int[][] potential) {
      //init MindState from potential card space
      state = new int[5][];
      for (int i = 0; i < 5; ++i) {
        state[i] = Arrays.copyOf(potential[i], potential[i].length);
      }
    }

    public void rankHint(int rank) {
      knownRank = rank;
      for (int i = 0; i < 5; ++i) {
        for (int j = 0; j < 5; ++j) {
          if (j != rank - 1) {
            state[i][j] = 0;
          }
        }
      }
    }

    public void colourHint(Colour colour) {
      knownColour = colour;
      for (int i = 0; i < 5; ++i) {
        for (int j = 0; j < 5; ++j) {
          if (i != colour.ordinal()) {
            state[i][j] = 0;
          }
        }
      }
    }

    public void remove(int colour, int rank) {
      if (state[colour][rank] > 0) state[colour][rank]--;
    }
  }

  class Player {
    public int id;
    public String name;
    public boolean trustworthy = true;
    public boolean intentional = true;
    public Card[] hand;
    public MindState[] mind;
    public int[][] potential;

    public Player(int playerID, int agentID, int handSize, int[][] cardsAvaliable, State state) {
      id = playerID;
      name = state.getPlayers()[id];
      if (id != agentID) {
        hand = state.getHand(id); // cant see own hand
      }

      // record potential
      potential = new int[5][];
      for (int i = 0; i < 5; ++i) {
        potential[i] = Arrays.copyOf(cardsAvaliable[i], NUM_RANKS);
      }

      // init default mindstates
      mind = new MindState[handSize];
      for (int i = 0; i < handSize; i++) {
        mind[i] = new MindState(potential);
      }
    }

    public void removeFromMind(Card card) {
      int colour = card.getColour().ordinal();
      int rank = card.getValue();

      potential[colour][rank]--;
      for (MindState mindState : mind) {
        mindState.remove(colour, rank);
      }
    }

    public void resetCardState(int handPos) {
      mind[handPos] = new MindState(potential);
    }
  }

  private static final int NUM_COLOURS = Colour.values().length;
  private static final int NUM_RANKS = 5;
  private static final int[] CARD_SPREAD = {3, 2, 2, 2, 1};

  private static int handSize;
  private static int numPlayers;
  private static int board;

  private int[][] cardsAvaliable;
  private Player[] players;
  private Player self;
  private boolean firstAction = true;
  private int currentOrder;
  private State currentState;


  /**
   * Default constructor.
   */
  public IntentAgent() {}

  /**
   * Initialises agent on first call.
   */
  private void init() {
    Action prevAction;
    State firstState;
    int selfID;

    // check game size
    numPlayers = currentState.getPlayers().length;
    if (numPlayers > 3) {
      handSize = 4;
    } else {
      handSize = 5;
    }

    selfID = currentState.getNextPlayer();
    currentOrder = currentState.getOrder();

    // init avaliable cards i.e. any not yet discarded or played
    cardsAvaliable = new int[NUM_COLOURS][];
    for (int i = 0; i < NUM_RANKS; ++i) {
      cardsAvaliable[i] = Arrays.copyOf(CARD_SPREAD, NUM_RANKS);
    }

    // init a player records
    players = new Player[numPlayers];
    for (int i = 0; i < numPlayers; ++i) {
      players[i] = new Player(i, selfID, handSize, cardsAvaliable, currentState);
    }

    // find first state by stepping back to the first players turn
    firstState = currentState;
    for (int i = 0; i < self.id; ++i) {
      firstState = firstState.getPreviousState();
    }    

    // remove initially visible cards from mindstates
    for (Player visible : players) {
      if (visible == self) continue; // cant tell what others see in our hand
      for (Player viewer : players) {
        if (visible == viewer) continue; // viewer cant see own hand
        for (Card card : firstState.getHand(visible.id)) {
          viewer.removeFromMind(card);
        }
      }
    }
    
    // apply first partial-round moves
    for (Player actor : players) {
      prevAction = currentState.getPreviousAction(actor.id);
      applyAction(prevAction, actor);
    }
  }

  private State getPastStateByOrder(int targetOrder) {
    State target = currentState;

    while (target.getOrder() != targetOrder) {
      target = target.getPreviousState();
    }
    return target;
  }

  private void applyDiscard(Action action, Player actor, State result) {
    int handPos = action.getCard();
    Card card = actor.hand[handPos];
    
    // decrease avaliablity counts
    cardsAvaliable[card.getColour().ordinal()][card.getValue()] -= 1;

    // pickup if avaliable
    pickupCard(actor, handPos, result);
  }

  private void applyPlay(Action action, Player actor, State result) {
    
  }

  private void pickupCard(Player actor, int handPos, State result) {
    Card card;
    
    // if game is over we can't pick up
    if (result.gameOver()) {
      return;
    }

    // cant manage our own hand
    if (actor == self) {
      return;
    }

    // otherwise actor picks up a new card
    actor.hand = result.getHand(actor.id);
    actor.resetMind(handPos);

    // other players see the new card
    card = actor.hand[handPos];
    for (Player player : players) {
      if (player == actor) continue;
      player.removeFromMind(card);
    }
  }

  private void applyAction(Action action, Player actor) {
    int actionOrder = currentOrder - numPlayers + actor.id;
    State result = getPastStateByOrder(actionOrder);

    //switch on type of action
    switch (action.getType()) {
      case DISCARD:
        applyDiscard(action, actor, result);
        break;
      case HINT_COLOUR:
        applyColourHint(action, actor, result);
        break;
      case HINT_VALUE:
        applyRankHint(action, actor, result);
        break;
      case PLAY:
        applyPlay(action, actor, result);
        break;
    }
  }

  /**
   * Reports the agents name
   * */
  public String toString() {
    return "IntentAgent";
  }

  /**
   * Given the state, return the action that the strategy chooses for this state.
   * @return the action the agent chooses to perform
   * */
  public Action doAction(State state) {
    currentState = state;

    // if agents first action it must initialise itself
    if (firstAction) {
      init();
      firstAction = false;
    }
    
    return null;
  }

}


