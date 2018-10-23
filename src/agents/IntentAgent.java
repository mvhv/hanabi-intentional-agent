package agents;

import hanabAI.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Stack;
import java.lang.Math;

/**
 * An implementation of an intentional agent for playing Hanabi.
 * The agent implements a modified Van den Bergh ruleset, with the
 * additon of tracking the mind states of all players, and attempting
 * to rate their "intentionality". If an opponent's actions are considered
 * to be "intentional", then hints from that agent are considered
 * more liberally when analysing plays.
 * 
 * @author Jesse Wyatt (20756971)
 **/
public class IntentAgent implements Agent {

  private static final int NUM_COLOURS = Colour.values().length;
  private static final int NUM_RANKS = 5;
  private static final int[] RANK_SPREAD = {3, 2, 2, 2, 1};

  /**The number of cards per hand */
  private static int handSize;
  /**The number of players in the game */
  private static int numPlayers;

  /**Frequencies for cards that haven't been discarded or played */
  private int[][] allUnplayed;
  /**Mind state models for all players */
  private Player[] players;
  /**Mind state model for this agent */
  private Player self;
  /**True if the agent is playing it's first action */
  private boolean firstAction = true;
  /**The current game state */
  private State currentState;
  /**The order number of the current game state */
  private int currentOrder;

  /**
   * A class representing all information a player knows about a
   * single card in it's own hand.
   */
  class MindState {
    /**True if the player knows the rank of this card */
    private boolean knowRank = false;
    /**True if the player knows the colour of this card */
    private boolean knowColour = false;
    /**The zero-based rank of this card if known */
    private int rank = -1;
    /**The colour of this card if known */
    private Colour colour = null;
    /**Known frequencies of all possible values for this card */
    private int[][] state;

    /**
     * Constructs an instance of the MindState model for this card using
     * the existing knowledge of the player holding it.
     * @param potential the frequencies of all possible values avaliable to
     * the player holding this card.
     */
    public MindState(int[][] potential) {
      //init MindState from potential card space
      state = new int[5][];
      for (int i = 0; i < 5; ++i) {
        state[i] = Arrays.copyOf(potential[i], potential[i].length);
      }
    }

    /**
     * Updates model of player's knowledge of this card after receiving
     * a rank hint.
     * @param rank the zero-based rank hinted
     */
    public void rankHint(int rank) {
      knowRank = true;
      this.rank = rank;
      for (int c = 0; c < 5; ++c) {
        for (int r = 0; r < 5; ++r) {
          if (r != rank) {
            state[c][r] = 0;
          }
        }
      }
      updateKnown();
    }

    /**
     * Updates model of player's knowledge of this card after receiving
     * a colour hint.
     * @param colour the colour hinted
     */
    public void colourHint(Colour colour) {
      knowColour = true;
      this.colour = colour;
      for (int c = 0; c < 5; ++c) {
        for (int r = 0; r < 5; ++r) {
          if (c != colour.ordinal()) {
            state[c][r] = 0;
          }
        }
      }
      updateKnown();
    }

    /**
     * Checks to see if the current card is useless based on player knowledge
     * of card frequencies and the state of board. Returns true if the
     * card is useless, false otherwise. 
     * @param board an array describing the values of the top ranking played
     * card for each clour
     * @param allUnplayed the player's knowledge of unplayed card frequencies
     * @return True if the card is useless, false otherwise.
     */
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
          if (allUnplayed[col][rank] < 1) {
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

    /**
     * Checks card knowledge and records rank and colour values if they
     * are certain.
     */
    private void updateKnown() {
      int[] colAggs = new int[NUM_COLOURS];
      int[] rankAggs = new int[NUM_RANKS];
      int count, c;
      int trueRank = -1;
      Colour trueColour = null;

      // skip check if already known
      if (knowRank && knowColour) return;

      // count row and colour aggregates
      for (Colour col : Colour.values()) {
        c = col.ordinal();
        for (int r = 0; r < NUM_RANKS; r++) {
          colAggs[c] += state[c][r];
          rankAggs[r] += state[c][r];
        }
      }

      if (!knowColour) {
        // count nonzero colour aggregates
        count = 0;
        for (Colour col : Colour.values()) {
          c = col.ordinal();
          if (colAggs[c] > 0) {
            count++;
            trueColour = col;
          }
        }
        // if exactly one nonzero agg then we know true colour
        if (count == 1) { 
          this.knowColour = true;
          this.colour = trueColour;
        }
      }
      
      if (!knowRank) {
        // count nonzero rank aggregates
        count = 0;
        for (int r = 0; r < NUM_RANKS; r++) {
          if (rankAggs[r] > 0) {
            count++;
            trueRank = r;
          }
        }
        // if exactly one nonzero agg then we know true rank
        if (count == 1) {
          this.knowRank = true;
          this.rank = trueRank;
        }
      }
    }

    /**
     * Removes a card from the record of potential states.
     * @param colour the integer colour ordinal of the card 
     * @param rank the rank of the card
     */
    public void remove(int colour, int rank) {
      if (state[colour][rank] > 0) {
        state[colour][rank]--;
      }
      updateKnown();
    }

    // unused accessor method to fix vscode debug-engine issues
    public int getRank() {
      return this.rank;
    }

    // unused accessor method to fix vscode debug-engine issues
    public Colour getColour() {
      return this.colour;
    }
  }

  /**Enum for holding hint types */
  enum HintType {COLOUR, RANK};

  /**
   * A class representing all information reguarding a single hint. Packaged
   * for use in a list tracking hint history.
   */
  class Hint {
    /**The player receiving the hint */
    private Player hintee;
    /**The player providing the hint */
    private Player hinter;
    /**The cards included in the hint */
    private boolean[] targets;
    /**The type of hint (COLOUR or RANK) */
    private HintType type;
    /**The rank of the hint */
    private int rank;
    /**The colour of the hint */
    private Colour colour;

    /**
     * Constructs an instance for rank hints.
     * @param rank the rank hinted
     * @param targets the cards hinted
     * @param hinter the player giving the hint
     * @param hintee the player recieving the hint
     */
    public Hint(int rank, boolean[] targets, Player hinter, Player hintee) {
      type = HintType.RANK;
      this.rank = rank;
      this.targets = Arrays.copyOf(targets, targets.length);
      this.hinter = hinter;
      this.hintee = hintee;
    }

    /**
     * Constructs an instance for colour hints.
     * @param colour the colour hinted
     * @param targets the cards hinted
     * @param hinter the player giving the hint
     * @param hintee the player receiving the hint
     */
    public Hint(Colour colour, boolean[] targets, Player hinter, Player hintee) {
      type = HintType.COLOUR;
      this.colour = colour;
      this.targets = Arrays.copyOf(targets, targets.length);
      this.hinter = hinter;
      this.hintee = hintee;
    }

    /**
     * Checks a colour hint for validity. True if valid, false otherwise. 
     * @return true if valid, false otherwise
     */
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

    /**
     * Checks a rank hint for validity. True if valid, false otherwise. 
     * @return true if valid, false otherwise
     */
    private boolean rankValid() {
      for (int i = 0; i < handSize; i++) {
        if (targets[i]) {
          if (getRank(hintee.hand[i]) != rank) {
            return false;
          }
        }
      }
      return true;
    }

    /**
     * Checks a hint for validity. True if valid, false otherwise. 
     * @return true if valid, false otherwise
     */   
    public boolean isValid() {
      if (type == HintType.COLOUR) {
        return this.colourValid();
      } else {
        return this.rankValid();
      }
    }
  }

  /**
   * A class for tracking all information about players. Including a 
   * model of the potential mind state of other players.
   */
  class Player {
    /**The player's order in the game */
    private int id;
    /**The player's name */
    private String name;
    /**True if the player has not made any mistakes */
    private boolean trustworthy = true;
    /**Estimated probability of player's hints being "intentional" */
    private double hintIntentional = 0.5;
    //private double playIntentional = 0.5;
    /**The player's actual hand if visible */
    private Card[] hand;
    /**The player's internal */
    private MindState[] mind;
    /**The player's knowledge of card frequencies */
    private int[][] potential;
    /**A list of all hints given to the player */
    private List<Hint> hints;

    /**
     * Constructs an instance of the player model.
     * @param playerID the id of the player to be modeled
     * @param agentID the id of the player constructing th model
     * @param handSize the numer of cards per hand
     * @param allUnplayed the initial card distribution
     * @param state the initial game state
     */
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

    /** */
    private void removeFromMind(Card card) {
      int colour = card.getColour().ordinal();
      int rank = getRank(card);

      potential[colour][rank]--;
      for (MindState mindState : mind) {
        mindState.remove(colour, rank);
      }
    }

    public void resetCardState(int handPos) {
      mind[handPos] = new MindState(potential);
      removeHints(handPos);
    }

    /**
     * Updates all hints to remove reference to the supplied card.
     * For invalidating hints to old cards after play/discard.
     * @param handPos the index of the card
     */
    private void removeHints(int handPos) {
      for (Hint hint : hints) {
        hint.targets[handPos] = false;
      }
    }
  }

  /**
   * Default constructor. Agent initialisation is delayed until first action.
   */
  public IntentAgent() {}

  /**
   * Initialises agent on first action.
   */
  private void initSim() {
    Action action;
    Player actor;
    State state;
    int id;

    // start from first round
    state = getPastStateByOrder(0);

    // check game size
    numPlayers = currentState.getPlayers().length;
    if (numPlayers > 3) {
      handSize = 4;
    } else {
      handSize = 5;
    }

    // init avaliable cards i.e. any not yet discarded or played
    allUnplayed = new int[NUM_COLOURS][NUM_RANKS];
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
    int prev, curr;

    // compare firework stacks to confirm successful play
    for (Colour colour : Colour.values()) {
      prev = peekRank(result.getPreviousState().getFirework(colour));
      curr = peekRank(result.getFirework(colour));
      if (prev != curr) {
        return result.getFirework(colour).peek();
      }
    }

    // else the play was unsuccessful and must have been discarded
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
    int rank = getRank(card);

    // mark card used
    allUnplayed[colour][rank]--;
    actor.potential[colour][rank]--;

    // new card has no special information
    actor.resetCardState(handPos);

    // cant update our own hand
    if (actor == self) {
      return;
    }

    // update hand info
    actor.hand = result.getHand(actor.id);

    // if game is over there is nothing to learm
    if (result.getPreviousState().getFinalActionIndex() > -1) {
      return;
    }

    // other players consider hand
    card = actor.hand[handPos];
    for (Player player : players) {
      if (player == actor) continue;
      player.removeFromMind(card);
    }
  }

  /*
  private int highestPotentialRankByColour(Colour colour, Player actor) {
    int highRank = -1;
    for (int i = 0; i < NUM_RANKS; i++) {
      if (actor.potential[colour.ordinal()][i] > 0) highRank = i;
    }

    return highRank;
  }
  */

  private boolean colourHintPlayable(Hint hint, int[] board) {
    for (int i = 0; i < handSize; i++) {
      if (hint.targets[i]) {
        if (board[hint.colour.ordinal()] == getRank(hint.hintee.hand[i]) + 1) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean colourUseless(Hint hint, int[] board) {
    int col = hint.colour.ordinal();
    int top = board[col];
    int possible = 0;

    for (int rank = top + 1; rank < NUM_RANKS; rank++) {
      // colour chain dead
      if (allUnplayed[col][rank] < 1) {
        return true;
      }

      // read mind state to see if any higher cards are still considered
      for (MindState mind : hint.hintee.mind) {
        possible += mind.state[col][rank];
      }
    }

    return (possible == 0);
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
    int rank = getRank(action);
    boolean[] targets = action.getHintedCards();
    Player hintee = players[action.getHintReceiver()];
    Hint hint = new Hint(rank, targets, actor, hintee);

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
    int playOrder;
    State state;

    // find State.order for the actors last turn relative to current order
    playOrder = currentOrder - numPlayers + Math.floorMod(actor.id - self.id, numPlayers);

    //switch on type of action
    switch (action.getType()) {
      case DISCARD:
        playOrder++; // discard verification needs next state
        state = getPastStateByOrder(playOrder);
        applyDiscard(action, actor, state);
        break;
      case HINT_COLOUR:
        state = getPastStateByOrder(playOrder);
        applyColourHint(action, actor, state);
        break;
      case HINT_VALUE:
        state = getPastStateByOrder(playOrder);
        applyRankHint(action, actor, state);
        break;
      case PLAY:
        playOrder++; // play verification needs next state
        state = getPastStateByOrder(playOrder);
        applyPlay(action, actor, state);
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
   * Returns a representation of the played firework stacks as an
   * integer array.
   * 
   * The entry at board[i] is the zero-based rank of the highest
   * played card with Colour.ordinal() == i. Or -1 if no cards with
   * this colour have been played.
   * 
   * @param state the state to check for board
   * @return an array of highest ranked cards per colour, with -1
   * indicating an empty stack.
   */
  private int[] checkBoard(State state) {
    int[] board = new int[NUM_COLOURS];
    int col;

    for (Colour colour : Colour.values()) {
      col = colour.ordinal();
      if (state.getFirework(colour).isEmpty()) {
        board[col] = -1;
      } else {
        board[col] = peekRank(state.getFirework(colour));
      }
    }

    return board;
  }

  private void simulateRound() {
    Action action;
    Player actor;
    int id;

    // initialise on first simulation
    if (firstAction) {
      initSim();
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
    int col;

    for (Colour colour : Colour.values()) {
      col = colour.ordinal();
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

    return ((double) safe) / ((double) total);
  }

  private List<Action> safestPlay() {
    List<Action> result = new ArrayList<Action>();
    Action safest = null;
    double safety, highest = -1.0;
    MindState mind;

    for (int i = 0; i < handSize; i++) {
      mind = self.mind[i];
      safety = probSafety(mind);
      if (safety > highest) {
        try {
          safest = new Action(self.id, self.name, ActionType.PLAY, i);
          highest = safety;
        } catch (IllegalActionException e) {
          System.err.println(e);
        }
      }
    }

    if (safest != null) {
      result.add(safest);
    }

    return result;
  }

  private List<Action> probablySafePlays(double safety) {
    List<Action> result = new ArrayList<Action>();
    Action action;
    MindState mind;

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
    int count;
    boolean[] targets;
    Hint hint;
    int[] board = checkBoard(currentState);

    // for each player
    for (Player player : players) {

      // cant hint self
      if (player == self) {
        continue;
      }

      // for each colour
      for (Colour colour : Colour.values()) {
        targets = new boolean[handSize];

        // check hand
        count = 0;
        for (int pos = 0; pos < handSize; pos++) {
          if ((player.hand[pos] != null) && (player.hand[pos].getColour() == colour)) {
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
      for (int rank = 0; rank > NUM_RANKS; rank++) {
        targets = new boolean[handSize];

        // check hand
        count = 0;
        for (int pos = 0; pos < handSize; pos++) {
          if ((player.hand[pos] != null) && (getRank(player.hand[pos]) == rank)) {
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
            tell = new Action(self.id, self.name, ActionType.HINT_VALUE, player.id, targets, rank + 1);
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

    // discards can't be made with full hint tokens
    if (currentState.getHintTokens() > 7) {
      return discards;
    }

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
    List<Action> actions = new ArrayList<Action>();
    Action action;

    // add whole hand to potential discards
    for (int pos = 0; pos < handSize; pos++) {
      try {
        action = new Action(self.id, self.name, ActionType.DISCARD, pos);
        actions.add(action);
      } catch (IllegalActionException e) {
        System.err.println(e);
      }
    }

    return actions;
  }

  private List<Action> randomTells() {
    List<Action> actions = new ArrayList<Action>();
    Action action;
    Random prng;
    Player player;
    int id, card, rank;
    Colour colour;
    boolean[] targets;


    // random tell
    prng = new Random();

    // search until first non useless hint found
    while (actions.isEmpty()) {
      // pick a random player that is not self
      id = self.id;
      while (id == self.id) {
        id = prng.nextInt(numPlayers);
      }
      player = players[id];

      // pick card randomly and hint rank or colour if not already known
      card = prng.nextInt(handSize);

      // tell colour
      if (!player.mind[card].knowColour) {
        colour = player.hand[card].getColour();
        targets = new boolean[handSize];
        for (int pos = 0; pos < handSize; pos++) {
          if ((player.hand[pos] != null) && (player.hand[pos].getColour() == colour)) {
            targets[pos] = true;
          }
        }
        try {
          action = new Action(self.id, self.name, ActionType.HINT_COLOUR, player.id, targets, colour);
          actions.add(action);
        } catch (IllegalActionException e) {
          System.err.println(e);
        }
      }

      // tell rank
      if (!player.mind[card].knowRank) {
        rank = player.hand[card].getValue();
        targets = new boolean[handSize];
        for (int pos = 0; pos < handSize; pos++) {
          if ((player.hand[pos] != null) && (player.hand[pos].getValue() == rank)) {
            targets[pos] = true;
          }
        }
        try {
          action = new Action(self.id, self.name, ActionType.HINT_VALUE, player.id, targets, rank);
          actions.add(action);
        } catch (IllegalActionException e) {
          System.err.println(e);
        }
      }
    }

    return actions;
  }

  private Action chooseAction() {
    List<Action> valid;
    Action chosen;
    int tokens;
    Random prng;

    // modified Van den Bergh selection order
    valid = probablySafePlays(0.99);

    // if final round try anything if fuses remain
    if ((valid.isEmpty()) && (currentState.getFinalActionIndex() > -1) && (currentState.getFuseTokens() > 1)) {
      valid = safestPlay(); 
    }

    // if no guaranteed safe plays, consider plays with bias towards
    // players with a good record
    if (valid.isEmpty()) valid = intentionalHintPlays(0.6, 0.8);

    // prioritise tells higher if many hint tokens remain
    tokens = currentState.getHintTokens();
    if (tokens > 4) {
      if (valid.isEmpty()) valid = intentionalTells(0.8);
      if (valid.isEmpty()) valid = randomTells();
      if (valid.isEmpty()) valid = uselessDiscards();
    } else if (tokens > 0) {
      if (valid.isEmpty()) valid = uselessDiscards();
      if (valid.isEmpty()) valid = intentionalTells(0.8);
      if (valid.isEmpty()) valid = randomTells();
    } else {
      if (valid.isEmpty()) valid = uselessDiscards();
    }

    // finally discard randomly
    if (valid.isEmpty()) valid = randomDiscards();

    // pick a random action from 
    prng = new Random();
    chosen = valid.get(prng.nextInt(valid.size()));

    return chosen;
  }

  /**
   * Helper function to return a zero indexed rank from a card.
   * @param card the card
   * @return the rank of the card
   */
  private int getRank(Card card) {
    return (card.getValue() - 1);
  }

  /**
   * Helper function to return a zero indexed rank from an action.
   * @param Action the action
   * @return the rank of the card referenced in the action
   */
  private int getRank(Action action) {
    int rank = -1;
    
    try {
      rank = action.getValue() - 1;
    } catch (IllegalActionException e) {
      e.printStackTrace();
    }
    
    return rank;
  }

  /**
   * Helper function to safely get a zero indexed rank fromm a card stack
   * @param fireworks the stack of cards
   * @return the rank of the top card, or -1 if the stack is empty
   */
  private int peekRank(Stack<Card> fireworks) {
    if (fireworks.isEmpty()) {
      return -1;
    } else {
      return getRank(fireworks.peek());
    }
  }

  /**
   * Given the state, return the action that the strategy chooses for this state.
   * @return the action the agent chooses to perform
   * */
  public Action doAction(State state) {
    currentState = state;
    currentOrder = currentState.getOrder();
    
    // simulate player mindset evolution
    simulateRound();

    // select appropriate action
    return chooseAction();
  }
}


