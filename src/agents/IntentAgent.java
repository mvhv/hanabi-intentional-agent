package agents;

import hanabAI.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Random;
import java.util.List;

/**
 * An interface for representing the strategy of a player in Hanabi
 * */
public class IntentAgent implements Agent {

  private static final int NUM_COLOURS = Colour.values().length;
  private static final int NUM_RANKS = 5;
  private static final int[] RANK_SPREAD = {3, 2, 2, 2, 1};

  private static int handSize;
  private static int numPlayers;

  private int[][] allUnplayed;
  private Player[] players;
  private Player self;
  private boolean firstAction = true;
  private int currentOrder;
  private State currentState;

  class MindState {
    private boolean knowRank = false;
    private boolean knowColour = false;
    private int rank;
    private Colour colour;
    private int[][] state;

    public MindState(int[][] potential) {
      //init MindState from potential card space
      state = new int[5][];
      for (int i = 0; i < 5; ++i) {
        state[i] = Arrays.copyOf(potential[i], potential[i].length);
      }
    }

    public void rankHint(int rank) {
      knowRank = true;
      this.rank = rank;
      for (int i = 0; i < 5; ++i) {
        for (int j = 0; j < 5; ++j) {
          if (j != rank - 1) {
            state[i][j] = 0;
          }
        }
      }
    }

    public void colourHint(Colour colour) {
      knowColour = true;
      this.colour = colour;
      for (int i = 0; i < 5; ++i) {
        for (int j = 0; j < 5; ++j) {
          if (i != colour.ordinal()) {
            state[i][j] = 0;
          }
        }
      }
    }

    public boolean isUseless(int[] board, int[][] allUnplayed) {
      int col, top;
      int count = 0;

      // for each card check mind state against the board
      for (Colour colour : Colour.values()) {
        col = colour.ordinal();
        top = board[col];

        // check for potential of higher rank
        for (int rank = top + 1; rank > NUM_RANKS; rank++) {
          // break early if colour chain dead
          if (allUnplayed[col][rank] < 0) {
            break;
          } else {
            count += state[col][rank];
          }
        }
      }

      // card useless if higher ranks have been ruled out
      if (count > 0) {
        return false;
      } else {
        return true;
      }
    }

    public void remove(int colour, int rank) {
      if (state[colour][rank] > 0) {
        state[colour][rank]--;
      }
    }

  }

  enum HintType {COLOUR, RANK};

  class Hint {
    private Player hintee;
    private Player hinter;
    private boolean[] targets;
    private HintType type;
    private int rank;
    private Colour colour;

    public Hint(int rank, boolean[] targets, Player hinter, Player hintee) {
      type = HintType.RANK;
      this.rank = rank;
      this.targets = targets;
      this.hinter = hinter;
      this.hintee = hintee;
    }

    public Hint(Colour colour, boolean[] targets, Player hinter, Player hintee) {
      type = HintType.COLOUR;
      this.colour = colour;
      this.targets = targets;
      this.hinter = hinter;
      this.hintee = hintee;
    }

    private boolean colourValid() {
      for (int i = 0; i < handSize; i++) {
        if (targets[i]) {
          if (hintee.hand[i].getColour() != colour) {
            return false;
          }
        }
      }
      return true;
    }

    private boolean rankValid() {
      for (int i = 0; i < handSize; i++) {
        if (targets[i]) {
          if (hintee.hand[i].getValue() != rank) {
            return false;
          }
        }
      }
      return true;
    }

    public boolean isValid() {
      if (type == HintType.COLOUR) {
        return this.colourValid();
      } else {
        return this.rankValid();
      }
    }
  }

  class Player {
    private int id;
    private String name;
    private boolean trustworthy = true;
    private double hintIntentional = 0.5;
    private double playIntentional = 0.5;
    private Card[] hand;
    private MindState[] mind;
    private int[][] potential;
    private List<Hint> hints;

    public Player(int playerID, int agentID, int handSize, int[][] allUnplayed, State state) {
      id = playerID;
      name = state.getPlayers()[id];
      if (id != agentID) {
        hand = state.getHand(id); // cant see own hand
      }

      // init potential
      potential = new int[5][];
      for (int i = 0; i < 5; ++i) {
        potential[i] = Arrays.copyOf(allUnplayed[i], NUM_RANKS);
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
    allUnplayed = new int[NUM_COLOURS][];
    for (int i = 0; i < NUM_RANKS; ++i) {
      allUnplayed[i] = Arrays.copyOf(RANK_SPREAD, NUM_RANKS);
    }

    // init player models
    id = currentState.getNextPlayer();
    players = new Player[numPlayers];
    for (int i = 0; i < numPlayers; ++i) {
      players[i] = new Player(i, id, handSize, allUnplayed, currentState);
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
    for (int move = 0; move < self.id; move++) {
      actor = players[move];
      action = currentState.getPreviousAction(move);
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
    allUnplayed[colour][rank]--;
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

  private int highestPotentialRankByColour(Colour colour, Player actor) {
    int highRank = -1;
    for (int i = 0; i < NUM_RANKS; i++) {
      if (actor.potential[colour.ordinal()][i] > 0) highRank = i;
    }

    return highRank;
  }

  private boolean colourHintPlayable(Hint hint, int[] board) {
    for (int i = 0; i < handSize; i++) {
      if (hint.targets[i]) {
        if (board[hint.colour.ordinal()] == hint.hintee.hand[i].getValue() + 1) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean colourUseless(Hint hint, int[] board) {
    int col = hint.colour.ordinal();
    int top = board[col];
    MindState mind = hint.hintee.mind[col];
    int remain = 0;

    for (int rank = top; rank < NUM_RANKS; rank++) {
      remain += mind.state[col][rank];
    }

    return (remain == 0);
  }

  private void applyColourHint(Action action, Player actor, State result) throws IllegalActionException{
    Colour colour = action.getColour();
    boolean[] targets = action.getHintedCards();
    Player hintee = players[action.getHintReceiver()];
    Hint hint = new Hint(colour, targets, actor, hintee);

    int[] board = checkBoard(result);

    // discard hints from untrustworthy sources
    if (!actor.trustworthy) {
      return;
    }

    // must otherwise assume hints directed to agent are valid
    if (hintee == self) {
      self.hints.add(hint);
    } else if (hint.isValid()) { // other hints can be evaluated
      hintee.hints.add(hint);
          // rate intentionality
      if (colourHintPlayable(hint, board)) {
        actor.hintIntentional += 1.0; // immediately playable
      } else if (colourUseless(hint, board)) {
        actor.hintIntentional += 1.0; // immediately discardable
      } else {
        actor.hintIntentional += 0.5; // unsure
      }
      actor.hintIntentional /= 2.0;
    } else {
      actor.trustworthy = false; // mark known bad actors
      return;
    }

    // apply hint
    for (int i = 0; i < handSize; i++) {
      if (hint.targets[i]) {
        hintee.mind[i].colourHint(colour);
      }
    }
  }

  private boolean rankHintPlayable(Hint hint, int[] board) {
    int rank = hint.rank;
    int col;
    
    for (int i = 0; i < handSize; i++) {
      if (hint.targets[i]) {
        col = hint.hintee.hand[i].getColour().ordinal();
        if (board[col] != rank - 1) {
          return false;
        }
      }
    }
    return true;
  }

  private boolean rankUseless(Hint hint, int[] board) {
    int rank = hint.rank;
    int col;
    
    for (Colour colour : Colour.values()) {
      col = colour.ordinal();
      if (board[col] >= rank) {
        return true;
      }
    }

    return false;
  }

  private void applyRankHint(Action action, Player actor, State result) throws IllegalActionException {
    Colour colour = action.getColour();
    int rank = action.getValue();
    boolean[] targets = action.getHintedCards();
    Player hintee = players[action.getHintReceiver()];
    Hint hint = new Hint(colour, targets, actor, hintee);

    int[] board = checkBoard(result);

    // discard hints from untrustworthy sources
    if (!actor.trustworthy) {
      return;
    }

    // must assume hints directed to agent are valid
    if (hintee == self) {
      self.hints.add(hint);
    } else if (hint.isValid()) { // other hints can be evaluated
      hintee.hints.add(hint);
      
      // rate intentionality
      if (rankHintPlayable(hint, board)) {
        actor.hintIntentional += 1.0; // immediately playable
      } else if (rankUseless(hint, board)) {
        actor.hintIntentional += 1.0; // immediately discardable
      } else {
        actor.hintIntentional += 0.5; // unsure
      }
      actor.hintIntentional /= 2.0;
    } else {
      actor.trustworthy = false; // mark known bad actors
      return;
    }

    // apply hint
    for (int i = 0; i < handSize; i++) {
      if (hint.targets[i]) {
        hintee.mind[i].rankHint(rank);
      }
    }
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

    for (Colour colour : Colour.values()) {
      board[colour.ordinal()] = state.getFirework(colour).peek().getValue();
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

  private double probSafety(MindState mind) {
    int[] board = checkBoard(currentState);
    int safe = 0;
    int total = 0;
    int possible;
    double prob = 0.0;

    for (int col = 0; col < NUM_COLOURS; col++) {
      for (int rank = 0; rank < NUM_RANKS; rank++) {
        possible = mind.state[col][rank];
        if (possible > 0) {
          total += possible;
          if (board[col] + 1 == rank) {
            safe += possible;
          }
        }
      }
    }

    return ((double) total) / ((double) safe);
  }


  private List<Action> probablySafePlays(double safety) {
    List<Action> result = new ArrayList<Action>();
    Action action;
    MindState mind;
    double prob;

    for (int i = 0; i < handSize; i++) {
      mind = self.mind[i];
      if (probSafety(mind) > safety) {
        try {
          action = new Action(self.id, self.name, ActionType.PLAY, i);
          result.add(action);
        } catch (IllegalActionException e) {
          System.err.println(e);
        }
      }
    }

    return result;
  }

  private List<Player> getCardHinters(int handPos) {
    List<Player> hinters = new ArrayList<Player>();
    
    for (Hint hint : self.hints) {
      hinters.add(hint.hinter);
    }

    return hinters;
  }

  private List<Action> intentionalHintPlays(double safety, double intentionality) {
    List<Action> intentPlays = new ArrayList<Action>();
    List<Action> plays = probablySafePlays(safety);
    List<Player> hinters;

    for (Action play : plays) {
      try {
        hinters = getCardHinters(play.getCard());
        for (Player hinter : hinters) {
          if (hinter.hintIntentional >= intentionality) {
            intentPlays.add(play);
          }
        }
      } catch (IllegalActionException e) {
        System.err.println(e);
      }
    }

    // if no intentional plays found, and fuses remaining then attempt risky move
    if ((intentPlays.isEmpty()) && (currentState.getFuseTokens() > 1)) {
      return plays;
    } else {
      return intentPlays;
    }
  }

  private List<Action> intentionalTells(double intentionality) {
    List<Action> tells = new ArrayList<Action>(); 
    Action tell;
    MindState mind;
    int col, count;
    boolean[] targets;
    Hint hint;
    int[] board = checkBoard(currentState);

    // for each player
    for (Player player : players) {

      // for each colour
      targets = new boolean[NUM_COLOURS];
      for (Colour colour : Colour.values()) {
        col = colour.ordinal();
        Arrays.fill(targets, false);

        // check hand
        count = 0;
        for (int pos = 0; pos < handSize; pos++) {
          if (player.hand[pos].getColour() == colour) {
            targets[pos] = true;
            // only count unknowns or hint is pointless
            if (!player.mind[pos].knowColour) {
              count += 1;
            }
          }
        }

        // dont give negative or useless hints
        if (count == 0) continue;

        // check hint and store if useful
        hint = new Hint(colour, targets, self, player);
        if (colourUseless(hint, board) || colourHintPlayable(hint, board)) {
          try {
            tell = new Action(self.id, self.name, ActionType.HINT_COLOUR, player.id, targets, colour);
            tells.add(tell);
          } catch (IllegalActionException e) {
            System.err.println(e);
          }
        }
      }

      // for each rank
      targets = new boolean[NUM_RANKS];
      for (int rank = 0; rank > NUM_RANKS; rank++) {
        Arrays.fill(targets, false);

        // check hand
        count = 0;
        for (int pos = 0; pos < handSize; pos++) {
          if (player.hand[pos].getValue() == rank) {
            targets[pos] = true;
            // only count unknowns or hint is pointless
            if (!player.mind[pos].knowRank) {
              count += 1;
            }
          }
        }

        // dont give negative or useless hints
        if (count == 0) continue;

        // check hint and store if useful
        hint = new Hint(rank, targets, self, player);
        if (rankUseless(hint, board) || rankHintPlayable(hint, board)) {
          try {
            tell = new Action(self.id, self.name, ActionType.HINT_VALUE, player.id, targets, rank);
            tells.add(tell);
          } catch (IllegalActionException e) {
            System.err.println(e);
          }
        }
      }
    }

    return tells;
  }

  private List<Action> uselessDiscards() {
    List<Action> discards = new ArrayList<Action>();
    Action discard;
    MindState mind;
    int[] board = checkBoard(currentState);

    // for each card check mind state
    for (int pos = 0; pos < handSize; pos++) {
      mind = self.mind[pos];

      if (mind.isUseless(board, allUnplayed)) {
        try {
          discard = new Action(self.id, self.name, ActionType.DISCARD, pos);
          discards.add(discard);
        } catch (IllegalActionException e) {
          System.err.println(e);
        }
      }
    }

    return discards;
  }

  private List<Action> randomDiscards() {
    List<Action> discards = new ArrayList<Action>();
    Action discard;

    for (int pos = 0; pos < handSize; pos++) {
      try {
        discard = new Action(self.id, self.name, ActionType.DISCARD, pos);
        discards.add(discard);
      } catch (IllegalActionException e) {
        System.err.println(e);
      }
    }

    return discards;
  }

  private Action chooseAction() {
    List<Action> valid;
    Action chosen;

    // modified Van den Bergh selection order
    valid = probablySafePlays(0.99);
    if (valid.isEmpty()) valid = intentionalHintPlays(0.6, 0.8);
    if (valid.isEmpty()) valid = uselessDiscards();
    if (valid.isEmpty()) valid = intentionalTells(0.8);
    if (valid.isEmpty()) valid = randomDiscards();

    // pick a random action from 
    Random prng = new Random();
    chosen = valid.get(prng.nextInt(valid.size()));

    return chosen;
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

    // select appropriate action
    return chooseAction();
  }
}


