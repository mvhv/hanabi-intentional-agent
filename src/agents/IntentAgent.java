package agents;
import hanabAI.*;

import java.util.Arrays;

/**
 * An interface for representing the strategy of a player in Hanabi
 * */
public class IntentAgent implements Agent {

  class MindState {
    public int certainRank = null;
    public int certainColour = null;
    public int[][] state;

    public MindState(int[] spread) {
      //init MindState from default spread
      state = new int[5][];
      for (int i = 0; i < 5; ++i) {
        state[i] = Arrays.copyOf(spread)
      }
    }

    public MindState(int[][] avaliable) {
      //init MindState from avaliable cards
      state = new int[5][];
      for (int i = 0; i < 5; ++i) {
        state[i] = Arrays.copyOf(avaliable[i]);
      }
    }

    public void rankHint(int rank) {
      for (int i = 0; i < 5; ++i) {
        for (int j = 0; j < 5; ++j) {
          if (j != rank - 1) {
            state[i][j] = 0;
        }
      }
    }

    public void colourHint(Colour colour) {
      for (int i = 0; i < 5; ++i) {
        for (int j = 0; j < 5; ++j) {
          if (i != colour.ordinal()) {
            state[i][j] = 0;
        }
      }
    }

    public void removeFromMind(int rank, Colour colour) {
      state[colour.ordinal()][rank] -= 1;
    }
  }

  class Player {
    public int index;
    public String name;
    public boolean trustworthy = true;
    public Card[] hand;
    public MindState[] mind;

    public Player(int playerIndex, int agentIndex, int[][] cardsAvaliable, State state) {
      index = playerIndex;
      name = state.getPlayers()[index];
      if (index != agentIndex) {
        hand = state.getHand(index);
      }
      // init default mindstates
      mind = new MindState[IntentAgent.handSize];
      for (int i = 0; i < IntentAgent.handSize; i++) {
        mind[i] = new MindState(cardsAvaliable);
      }
    }

    public void update(Action a);
  }

  public static final int NUM_COLOURS = Colour.values().length;
  public static final int NUM_RANKS = 5;
  public static final int[] CARD_SPREAD = {3, 2, 2, 2, 1}

  private static int handSize;
  private static int numPlayers;
  private static int[][] cardsAvaliable;

  private Player[] players;
  private boolean firstAction = true;
  private int currentMove;
  private int self;

  /**
   * Default constructor.
   */
  public IntentAgent() {}
  
  /**
   * Initialises agent on first call.
   */
  private void init(State state) {
    // init players
    numPlayers = state.getPlayers().length;
    if (numPlayers > 3) {
      handSize = 4;
    } else {
      handSize = 5;
    }
    self = state.getNextPlayer();

    // find first state by stepping back to the first player
    State firstState = state;
    for (int i = 0; i < self; ++i) {
      firstState = firstState.getPreviousState();
    }
    
    // init avaliable cards
    cardsAvaliable = new int[NUM_COLOURS][];
    for (int i = 0; i < NUM_RANKS; ++i) {
      cardsAvaliable[i] = Arrays.copyOf(CARD_SPREAD);
    }

    // init player states
    players = new Player[numPlayers];
    for (int i = 0; i < numPlayers; ++i) {
      players[i] = new Player(i, self, cardsAvaliable, firstState);
    }

    // remove visible cards from mindstates.
    for (Player visible : players) {
      for (Player canSee : players) {
        if ((visible.index != self) && (visible != canSee)) {
            for (Card card : visible.getHand()) {
              canSee.mind.removeFromMind(card.getValue(), card.getColour());
            }
          }
        }
      }
    }

    // apply first half-round moves
    for (int i = 0; i < self; ++i) {
      applyAction(getPreviousAction(i));
    }
  }

  private void applyDiscard(Action action) {
    // reduce count
    Card card = action.getCard();
    cardsAvaliable[card.getColour().ordinal()][card.getValue()] -= 1;
    
    
  }

  private void applyAction(Action action) {
    switch (action.getType()) {
      case DISCARD:
        applyDiscard(action);
        break;
      case HINT_COLOUR:
        applyColourHint(action);
        break;
      case HINT_VALUE:
        applyRankHint(action);
        break;
      case PLAY:
        applyPlay(action);
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
  public Action doAction(State s) {
    if (firstAction) {
      init(s);
      firstAction = false;
    }
    
    return null;
  }

}


