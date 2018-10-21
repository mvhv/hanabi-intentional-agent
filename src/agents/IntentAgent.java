package agents;

import hanabAI.*;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/**
 * An interface for representing the strategy of a player in Hanabi
 * */
public class IntentAgent implements Agent {

  private static final int NUM_COLOURS = 5;
  private static final int NUM_RANKS = 5;
  private static final int[] RANK_SPREAD = {3, 2, 2, 2, 1};

  private static int handSize;
  private static int numPlayers;

  private int[][] cardsAvaliable;
  private Player[] players;
  private Player self;
  private boolean firstAction = true;
  private int currentOrder;
  private State currentState;

  class MindState {
    private int knownRank;
    private Colour knownColour;
    private int[][] state;

    public MindState(int[][] potential) {
      //init MindState from potential card space
      state = new int[5][];
      for (int i = 0; i < 5; ++i) {
        state[i] = Arrays.copyOf(potential[i], potential[i].length);
      }
    }

    private void rankHint(int rank) {
      knownRank = rank;
      for (int i = 0; i < 5; ++i) {
        for (int j = 0; j < 5; ++j) {
          if (j != rank - 1) {
            state[i][j] = 0;
          }
        }
      }
    }

    private void colourHint(Colour colour) {
      knownColour = colour;
      for (int i = 0; i < 5; ++i) {
        for (int j = 0; j < 5; ++j) {
          if (i != colour.ordinal()) {
            state[i][j] = 0;
          }
        }
      }
    }

    private void remove(int colour, int rank) {
      if (state[colour][rank] > 0) state[colour][rank]--;
    }
  }

  enum HintType {COLOUR, RANK};

  class Hint {
    private Player hinter;
    private boolean[] targets;
    private HintType type;
    private int rank;
    private Colour colour;

    public Hint(int rank, boolean[] targets, Player hinter) {
      type = HintType.RANK;
      this.rank = rank;
      this.targets = targets;
      this.hinter = hinter;
    }

    public Hint(Colour colour, boolean[] targets, Player hinter) {
      type = HintType.COLOUR;
      this.colour = colour;
      this.targets = targets;
      this.hinter = hinter;
    }
  }

  class Player {
    private int id;
    private String name;
    private boolean trustworthy = true;
    private double intentional = 0.5;
    private Card[] hand;
    private MindState[] mind;
    private int[][] potential;
    private List<Hint> hints;

    public Player(int playerID, int agentID, int handSize, int[][] cardsAvaliable, State state) {
      id = playerID;
      name = state.getPlayers()[id];
      if (id != agentID) {
        hand = state.getHand(id); // cant see own hand
      }

      // init potential
      potential = new int[5][];
      for (int i = 0; i < 5; ++i) {
        potential[i] = Arrays.copyOf(cardsAvaliable[i], NUM_RANKS);
      }

      // init default mindstates
      mind = new MindState[handSize];
      for (int i = 0; i < handSize; i++) {
        mind[i] = new MindState(potential);
      }

      // init hint history
      hints = new ArrayList<Hint>();
    }

    private void removeFromMind(Card card) {
      int colour = card.getColour().ordinal();
      int rank = card.getValue();

      potential[colour][rank]--;
      for (MindState mindState : mind) {
        mindState.remove(colour, rank);
      }
    }

    private void resetCardState(int handPos) {
      mind[handPos] = new MindState(potential);
    }
  }

  /**
   * Default constructor.
   */
  public IntentAgent() {}

  /**
   * Initialises agent on first call.
   */
  private void initSim(State state) {
    Action action;
    Player actor;
    int id;

    // check game size
    numPlayers = currentState.getPlayers().length;
    if (numPlayers > 3) {
      handSize = 4;
    } else {
      handSize = 5;
    }

    // init avaliable cards i.e. any not yet discarded or played
    cardsAvaliable = new int[NUM_COLOURS][];
    for (int i = 0; i < NUM_RANKS; ++i) {
      cardsAvaliable[i] = Arrays.copyOf(RANK_SPREAD, NUM_RANKS);
    }

    // init player models
    id = currentState.getNextPlayer();
    players = new Player[numPlayers];
    for (int i = 0; i < numPlayers; ++i) {
      players[i] = new Player(i, id, handSize, cardsAvaliable, currentState);
    }

    // mark agents internal model
    self = players[id];

    // remove initially visible cards from mindstates
    for (Player visible : players) {
      if (visible == self) continue; // cant tell what others see in our hand
      for (Player viewer : players) {
        if (visible == viewer) continue; // viewer cant see own hand
        for (Card card : state.getHand(visible.id)) {
          viewer.removeFromMind(card);
        }
      }
    }
    
    // apply moves since game start
    for (int move = 0; move < numPlayers; move++) {
      id = (self.id + move) % numPlayers;
      actor = players[id];
      action = currentState.getPreviousAction(actor.id);
      try {
        applyAction(action, actor);
      } catch (IllegalActionException e) {
        System.err.println(e);
      }
    }
  }

  private State getPastStateByOrder(int targetOrder) {
    State target = currentState;

    while (target.getOrder() != targetOrder) {
      target = target.getPreviousState();
    }
    return target;
  }

  private Card getCardPlayed(State result) {
    Card prev, curr;

    // compare firework stacks to confirm successful play
    for (Colour colour : Colour.values()) {
      prev = result.getPreviousState().getFirework(colour).peek();
      curr = result.getFirework(colour).peek();
      if (prev != curr) {
        return curr;
      }
    }

    // else must have been discarded
    return result.getDiscards().peek();
  }

  private void applyDiscard(Action action, Player actor, State result) throws IllegalActionException {
    int handPos = action.getCard();
    Card card = result.getDiscards().peek();

    // pickup if avaliable
    replaceCard(actor, card, handPos, result);
  }

  private void applyPlay(Action action, Player actor, State result) throws IllegalActionException {
    int handPos = action.getCard();
    Card card = getCardPlayed(result);

    // pickup if avaliable
    replaceCard(actor, card, handPos, result);
  }

  private void replaceCard(Player actor, Card card, int handPos, State result) {
    int colour = card.getColour().ordinal();
    int rank = card.getValue();

    // mark card used
    cardsAvaliable[colour][rank]--;
    actor.potential[colour][rank]--;

    // if game is over we can't pick up
    if (result.gameOver()) {
      return;
    }

    // reset mind state for new card
    actor.resetCardState(handPos);

    // cant update our own hand
    if (actor == self) {
      return;
    }

    // actor picks up a new card and others see it
    actor.hand = result.getHand(actor.id);
    card = actor.hand[handPos];
    for (Player player : players) {
      if (player == actor) continue;
      player.removeFromMind(card);
    }
  }

  private void applyColourHint(Action action, Player actor, State result) throws IllegalActionException{
    Hint hint = new Hint(action.getColour(), action.getHintedCards(), actor);
    Player hintee = players[action.getHintReciever()]
    int[] board = checkBoard(result);

    // discard hints from untrustworthy sources
    if (!actor.trustworthy) return;

    // assume hints directed at agent are valid
    if (hintee == self) {
      self.hints.add(hint);
    } else {
      // check validity
      for (int i = 0; i < hint.targets.length; i++) {
        if ((hint.targets[i]) && (hintee.hand[i].getColour() != hint.colour)) {
          // hint invalid, source untrustworthy
          actor.intentional = 0.0;
          actor.trustworthy = false;
          return;
        }
      }
      // rate intentionality of hinter
      for (int i = 0; i < hint.targets.length; i++) {
        if ((hint.targets[i]) && (hintee.hand[i].rank == board[hint.colour.ordinal()] + 1)) {
          actor.intentional += 1.0;
          actor.intentional /= 2.0;
        } else if ()

      }
    }
  }

  private void applyRankHint(Action action, Player actor, State result) throws IllegalActionException {

  }

  private void applyAction(Action action, Player actor) throws IllegalActionException {
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

  private int[] checkBoard(State state) {
    int[] board = new int[NUM_COLOURS];

    for (Colour color : Colours.values()) {
      board[color.ordinal()] = state.getFirework(color).peek();
    }

    return board;
  }

  private void simulateRound(State state) {
    Action action;
    Player actor;
    int id;

    // initialise on first simulation
    if (firstAction) {
      initSim(state);
      firstAction = false;
      return;
    }
    
    // apply moves since last agent action
    for (int move = 0; move < numPlayers; move++) {
      id = (self.id + move) % numPlayers;
      actor = players[id];
      action = currentState.getPreviousAction(actor.id);
      try {
        applyAction(action, actor);
      } catch (IllegalActionException e) {
        System.err.println(e);
      }
    }
  }

  /**
   * Given the state, return the action that the strategy chooses for this state.
   * @return the action the agent chooses to perform
   * */
  public Action doAction(State state) {
    State simStart;

    currentState = state;

    // simulation starts with last of own moves unless in first round
    if (firstAction) {
      simStart = getPastStateByOrder(1);
    } else {
      simStart = getPastStateByOrder(currentOrder);
    }
    currentOrder = currentState.getOrder();
    
    // simulate player mindset evolution
    simulateRound(simStart);



    return null;
  }
}


