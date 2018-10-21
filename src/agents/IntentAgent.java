package agents;
import hanabAI.*;

import java.util.Arrays;

/**
 * An interface for representing the strategy of a player in Hanabi
 * */
public class IntentAgent implements Agent {

  class MindState {
    public int certainRank;
    public Colour certainColour;
    public int[][] state;

    public MindState(int[] spread) {
      //init MindState from default spread
      state = new int[5][];
      for (int i = 0; i < 5; ++i) {
        state[i] = Arrays.copyOf(spread, spread.length);
      }
    }

    public MindState(int[][] avaliable) {
      //init MindState from avaliable cards
      state = new int[5][];
      for (int i = 0; i < 5; ++i) {
        state[i] = Arrays.copyOf(avaliable[i], avaliable[i].length);
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
    }

    public void colourHint(Colour colour) {
      for (int i = 0; i < 5; ++i) {
        for (int j = 0; j < 5; ++j) {
          if (i != colour.ordinal()) {
            state[i][j] = 0;
          }
        }
      }
    }

    public void remove(Card card) {
      state[card.getColour().ordinal()][card.getValue()] -= 1;
    }
  }

  class Player {
    public int id;
    public String name;
    public boolean trustworthy = true;
    public Card[] hand;
    public MindState[] mind;

    public Player(int playerID, int agentID, int[][] cardsAvaliable, State state) {
      this.id = playerID;
      this.name = state.getPlayers()[id];
      if (id != agentID) {
        this.hand = state.getHand(id); // cant see own hand
      }

      // init default mindstates
      mind = new MindState[IntentAgent.handSize];
      for (int i = 0; i < IntentAgent.handSize; i++) {
        mind[i] = new MindState(cardsAvaliable);
      }
    }
    
    public void removeFromMind(Card card) {
      for (MindState mindState : mind) {
        mindState.remove(card);
      }
    }

    public void update(Action action) {

    }
  }

  private static final int NUM_COLOURS = Colour.values().length;
  private static final int NUM_RANKS = 5;
  private static final int[] CARD_SPREAD = {3, 2, 2, 2, 1};

  private static int handSize;
  private static int numPlayers;
  private static int[][] cardsAvaliable;

  private Player[] players;
  private boolean firstAction = true;
  private int currentOrder;
  private State currentState;
  private Player self;

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
    
    // init avaliable cards i.e. any not discarded or played
    cardsAvaliable = new int[NUM_COLOURS][];
    for (int i = 0; i < NUM_RANKS; ++i) {
      cardsAvaliable[i] = Arrays.copyOf(CARD_SPREAD, NUM_RANKS);
    }

    // init players visible/mental states
    players = new Player[numPlayers];
    for (int i = 0; i < numPlayers; ++i) {
      players[i] = new Player(i, selfID, cardsAvaliable, currentState);
    }

    // find first state by stepping back to the first player
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

  private void applyDiscard(Action action, State result) {
    // decrease card counts
    Player actor = players[action.getPlayer()];
    Card card = actor.hand[action.getCard()];
    cardsAvaliable[card.getColour().ordinal()][card.getValue()] -= 1;

    // replace if avaliable
    pickupCard(action, result);
  }

  private void pickupCard(Action action, State result) {
    // actor picks up a new card
    Player actor = players[action.getPlayer()];
    actor.hand = result.getHand(actor.id);
    // new card is unknown

  }

  private void applyAction(Action action, Player actor) {
    int actionOrder = currentOrder - numPlayers + actor.id;
    State result = getPastStateByOrder(actionOrder);

    //switch on type of action
    switch (action.getType()) {
      case DISCARD:
        applyDiscard(action, result);
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


