package tools;

import java.util.*;
import java.util.logging.*;
import cartago.Artifact;
import cartago.OPERATION;
import cartago.OpFeedbackParam;

public class QLearner extends Artifact {

    private Lab lab; // the lab environment that will be learnt
    private int stateCount; // the number of possible states in the lab environment
    private int actionCount; // the number of possible actions in the lab environment
    private HashMap<String, double[][]> qTables; // a map for storing the qTables computed for different goals
    private int[] goalState = {2, 3};

    private static final Logger LOGGER = Logger.getLogger(QLearner.class.getName());

    public void init(String environmentURL) {

        // the URL of the W3C Thing Description of the lab Thing
        this.lab = new Lab(environmentURL);

        this.stateCount = this.lab.getStateCount();
        LOGGER.info("Initialized with a state space of n="+ stateCount);

        this.actionCount = this.lab.getActionCount();
        LOGGER.info("Initialized with an action space of m="+ actionCount);

        qTables = new HashMap<>();
    }

/**
* Computes a Q matrix for the state space and action space of the lab, and against
* a goal description. For example, the goal description can be of the form [z1level, z2Level],
* where z1Level is the desired value of the light level in Zone 1 of the lab,
* and z2Level is the desired value of the light level in Zone 2 of the lab.
* For exercise 11, the possible goal descriptions are:
* [0,0], [0,1], [0,2], [0,3], 
* [1,0], [1,1], [1,2], [1,3], 
* [2,0], [2,1], [2,2], [2,3], 
* [3,0], [3,1], [3,2], [3,3].
*
*<p>
* HINT: Use the methods of {@link LearningEnvironment} (implemented in {@link Lab})
* to interact with the learning environment (here, the lab), e.g., to retrieve the
* applicable actions, perform an action at the lab during learning etc.
*</p>
* @param  goalDescription  the desired goal against the which the Q matrix is calculated (e.g., [2,3])
* @param  episodesObj the number of episodes used for calculating the Q matrix
* @param  alphaObj the learning rate with range [0,1].
* @param  gammaObj the discount factor [0,1]
* @param epsilonObj the exploration probability [0,1]
* @param rewardObj the reward assigned when reaching the goal state
**/
    @OPERATION
    public void calculateQ(Object[] goalDescription , Object episodesObj, Object alphaObj, Object gammaObj, Object epsilonObj, Object rewardObj) {
        
        // ensure that the right datatypes are used
        Integer episodes = Integer.valueOf(episodesObj.toString());
        Double alpha = Double.valueOf(alphaObj.toString());
        Double gamma = Double.valueOf(gammaObj.toString());
        Double epsilon = Double.valueOf(epsilonObj.toString());
        Integer reward = Integer.valueOf(rewardObj.toString());

        // initialize table
        double[][] qTable = initializeQTable();
        String goalStateKey = Arrays.toString(goalDescription);
        int[] goalDescriptionInt = Arrays.stream(goalDescription)
                                          .mapToInt(obj -> ((Number) obj).intValue())
                                          .toArray();
        //iterate
        for (int e = 0; e < episodes; e++) {
            int randomActionIndex = (int) (Math.random() * lab.getActionCount());
            lab.performAction(randomActionIndex);
            int currentStateIndex = lab.readCurrentState();

            for (int t = 0; t < stateCount; t++) {
                if (Math.random() < epsilon) {
                    // explore
                    int actionIndex = (int) (Math.random() * actionCount);
                    LOGGER.info("Exploring, random action " + actionIndex);
                    performActionAndUpdateQTable(qTable, currentStateIndex, actionIndex, goalDescriptionInt, alpha, gamma, reward);
                } else {
                    // exploit
                    int actionIndex = getBestActionFromQTable(qTable, currentStateIndex);
                    LOGGER.info("Exploiting, best action");
                    performActionAndUpdateQTable(qTable, currentStateIndex, actionIndex, goalDescriptionInt, alpha, gamma, reward);
                }
                currentStateIndex = lab.readCurrentState();
                int[] currentStateDescription = getCurrentStateDescription(currentStateIndex);
                LOGGER.info("Current State: " + Arrays.toString(currentStateDescription));

                if (isGoalStateAchieved(currentStateDescription)) {
                    LOGGER.info("Goal state achieved at iteration " + t);
                    return; // Exit early if goal achieveed
                }
            }
        }
        //store table
        qTables.put(goalStateKey, qTable);
        printQTable(qTable);
    }

    private void performActionAndUpdateQTable(double[][] qTable, int currentStateIndex, int actionIndex, int[] goalDescription, double alpha, double gamma, int reward) {
        lab.performAction(actionIndex);
        //get new state
        int newStateIndex = lab.readCurrentState();
        double qValue = qTable[currentStateIndex][actionIndex];

        double maxQValueForNewState = Arrays.stream(qTable[newStateIndex]).max().orElse(0.0);

        double rewardValue = isGoalStateAchieved(getCurrentStateDescription(newStateIndex)) ? reward : 0;
        //update
        qTable[currentStateIndex][actionIndex] = qValue + alpha * (rewardValue + gamma * maxQValueForNewState - qValue);
    }

    private int getBestActionFromQTable(double[][] qTable, int stateIndex) {
        double[] actions = qTable[stateIndex];
        int bestActionIndex = 0;
        double maxQValue = actions[0];

        for (int i = 1; i < actions.length; i++) {
            if (actions[i] > maxQValue) {
                maxQValue = actions[i];
                bestActionIndex = i;
            }
        }
        return bestActionIndex;
    }

    @OPERATION
    public void getActionFromState(Object[] goalDescription, Object[] currentStateDescription,
                                   OpFeedbackParam<String> nextBestActionTag, OpFeedbackParam<Object[]> nextBestActionPayloadTags,
                                   OpFeedbackParam<Object[]> nextBestActionPayload) {

        String goalStateKey = Arrays.toString(goalDescription);

        double[][] qTable = qTables.get(goalStateKey);

        int currentStateIndex = getCurrentStateIndex(currentStateDescription);
        int bestActionIndex = getBestActionFromQTable(qTable, currentStateIndex);
        Action bestAction = lab.getAction(bestActionIndex);

        nextBestActionTag.set(bestAction.getActionTag());
        nextBestActionPayloadTags.set(bestAction.getPayloadTags());
        nextBestActionPayload.set(bestAction.getPayload());
    }

    @OPERATION
    public void getCurrentLabState(OpFeedbackParam<Object[]> currentState) {
        int currentStateIndex = lab.readCurrentState();
        int[] stateDescription = getCurrentStateDescription(currentStateIndex);
        Object[] stateDescriptionObj = Arrays.stream(stateDescription).boxed().toArray(Object[]::new);
        currentState.set(stateDescriptionObj);
    }

    /**
    * Print the Q matrix
    *
    * @param qTable the Q matrix
    */
    void printQTable(double[][] qTable) {
        System.out.println("Q matrix");
        for (int i = 0; i < qTable.length; i++) {
            System.out.print("From state " + i + ":  ");
            for (int j = 0; j < qTable[i].length; j++) {
                System.out.printf("%6.2f ", (qTable[i][j]));
            }
            System.out.println();
          }
        }

  /**
  * Initialize a Q matrix
  *
  * @return the Q matrix
  */
    private double[][] initializeQTable() {
        double[][] qTable = new double[this.stateCount][this.actionCount];
        for (int i = 0; i < stateCount; i++){
            for(int j = 0; j < actionCount; j++){
                qTable[i][j] = 0.0;
            }
        }
        return qTable;
    }
    //get state
    private int[] getCurrentStateDescription(int stateIndex) {
        List<List<Integer>> stateSpace = new ArrayList<>(lab.stateSpace);
        List<Integer> state = stateSpace.get(stateIndex);
        int[] stateDescription = new int[state.size()];
        for (int i = 0; i < state.size(); i++) {
            stateDescription[i] = state.get(i);
        }
        return stateDescription;
    }
    //find index
    private int getCurrentStateIndex(Object[] stateDescription) {
        List<Integer> stateList = new ArrayList<>();
        for (Object obj : stateDescription) {
            stateList.add(((Number) obj).intValue());
        }
        List<List<Integer>> stateSpace = new ArrayList<>(lab.stateSpace);
        return stateSpace.indexOf(stateList);
    }

    // extract the relevant parts aka z levels
    @OPERATION
    public void getRelevantElementsFromState(Object[] state, OpFeedbackParam<int[]> relevantElements) {
        // Convert object to int otherwise error
        int[] intState = Arrays.stream(state).mapToInt(obj -> ((Number) obj).intValue()).toArray();
        relevantElements.set(new int[]{intState[0], intState[1]});
    }

    // check if goal state achieved aka right z levels
    @OPERATION
    public boolean isGoalStateAchieved(int[] state) {
        int[] relevantElements = getRelevantElements(state);
        return Arrays.equals(relevantElements, goalState);
    }

    private int[] getRelevantElements(int[] state) {
        return new int[]{state[0], state[1]};
    }
}

