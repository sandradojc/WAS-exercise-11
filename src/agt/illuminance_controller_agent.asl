//illuminance controller agent

/*
* The URL of the W3C Web of Things Thing Description (WoT TD) of a lab environment
* Simulated lab WoT TD: "https://raw.githubusercontent.com/Interactions-HSG/example-tds/was/tds/interactions-lab.ttl"
* Real lab WoT TD: Get in touch with us by email to acquire access to it!
*/

/* Initial beliefs and rules */

// the agent has a belief about the location of the W3C Web of Thing (WoT) Thing Description (TD)
// that describes a lab environment to be learnt
learning_lab_environment("https://raw.githubusercontent.com/Interactions-HSG/example-tds/was/tds/interactions-lab.ttl").

// the agent believes that the task that takes place in the 1st workstation requires an indoor illuminance
// level of Rank 2, and the task that takes place in the 2nd workstation requires an indoor illumincance 
// level of Rank 3. Modify the belief so that the agent can learn to handle different goals.
task_requirements([2,3]).

/* Initial goals */
!start. // the agent has the goal to start

/* 
 * Plan for reacting to the addition of the goal !start
 * Triggering event: addition of goal !start
 * Context: the agent believes that there is a WoT TD of a lab environment located at Url, and that 
 * the tasks taking place in the workstations require indoor illuminance levels of Rank Z1Level and Z2Level
 * respectively
 * Body: (currently) creates a QLearnerArtifact and a ThingArtifact for learning and acting on the lab environment.
*/
@start
+!start : learning_lab_environment(Url) 
  & task_requirements([Z1Level, Z2Level]) <-

  .print("Hello world");
  .print("I want to achieve Z1Level=", Z1Level, " and Z2Level=",Z2Level);

  // creates a QLearner artifact for learning the lab Thing described by the W3C WoT TD located at URL
  makeArtifact("qlearner", "tools.QLearner", [Url], QLArtId);

  // creates a ThingArtifact artifact for reading and acting on the state of the lab Thing
  makeArtifact("lab", "org.hyperagents.jacamo.artifacts.wot.ThingArtifact", [Url], LabArtId);

  // calculate table
  !calculate_qtable(QLArtId, [Z1Level, Z2Level]).

@calculation
+!calculate_qtable(QLArtId, Requirements) <- 
  calculateQ(Requirements, 1000, 0.1, 0.9, 0.1, 100)[artifact_id(QLArtId)];
  !achieve_goal.

@achieving
+!achieve_goal <- 
  .print("Start achieving goal");
  getCurrentLabState(CurrentState)[artifact_id(LabArtId)];
  .print("Initial lab state: ", CurrentState);
  !reach_state(CurrentState, [2, 3]).

+!reach_state(CurrentState, Requirements) <- 
  .print("Current state: ", CurrentState, ", Requirements: ", Requirements);
  getRelevantElementsFromState(CurrentState, CurrentStateZLevels)[artifact_id(QLArtId)];
  .print("Current state Z-levels: ", CurrentStateZLevels);
  !check_state(CurrentState, Requirements, CurrentStateZLevels).

+!check_state(CurrentState, Requirements, CurrentStateZLevels) : CurrentStateZLevels == Requirements <- 
  .print("I have reached my goal: ", Requirements);
  !stop.

+!check_state(CurrentState, Requirements, CurrentStateZLevels) : CurrentStateZLevels \== Requirements <- 
  .print("Current state doesnt match requirements, still going.");
  getActionFromState(Requirements, CurrentState, ActionTag, PayloadTags, Payload)[artifact_id(QLArtId)];
  .print("Action to perform: ", ActionTag, " with payload ", Payload);
  invokeAction(ActionTag, PayloadTags, Payload)[artifact_id(LabArtId)];
  .wait(1000);
  getCurrentLabState(NewState)[artifact_id(LabArtId)];
  !reach_state(NewState, Requirements).

@setup
+!set_thing(ThingArtifact) <- 
  +thing(ThingArtifact).

@task_requirements
+task_requirements(Requirements) <- 
  .print("Handling these task requirements: ", Requirements).

@termination
+!stop <- 
  .print("GOAL IS REACHED! Agent stopping further actions.").