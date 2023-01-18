package bot1;

import battlecode.common.*;


/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 *
 * This class is inherited by Unit class
 */
public strictfp class RobotPlayer {
    static RobotController rc;
    static int turnCount;

    static Team myTeam;
    static Team oppTeam;

    public static String indicator;
    public static int startRound;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * It is like the main function for your robot. If this method returns, the robot dies!
     *
     * @param rc  The RobotController object. You use it to perform actions from this robot, and to get
     *            information on its current status. Essentially your portal to interacting with the world.
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        RobotPlayer.rc = rc;
        myTeam = rc.getTeam();
        oppTeam = rc.getTeam().opponent();
        turnCount = 0;

        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode.
            try {
                startRound = rc.getRoundNum();
                indicator = "";
                Comm.turn_starts();
                switch (rc.getType()) {
                    case HEADQUARTERS: Headquarter.run();  break;
                    case CARRIER: Carrier.run();  break;
                    case LAUNCHER: Launcher.run(); break;
                    case BOOSTER: // Examplefuncsplayer doesn't use any of these robot types below.
                    case DESTABILIZER: // You might want to give them a try!
                    case AMPLIFIER:       break;
                }
                Comm.commit_write();
                rc.setIndicatorString(indicator);
            } catch (GameActionException e) {
                // Oh no! It looks like we did something illegal in the Battlecode world. You should
                // handle GameActionExceptions judiciously, in case unexpected events occur in the game
                // world. Remember, uncaught exceptions cause your robot to explode!
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();

            } catch (Exception e) {
                // Oh no! It looks like our code tried to do something bad. This isn't a
                // GameActionException, so it's more likely to be a bug in our code.
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();

            } finally {
                // Signify we've done everything we want to do, thereby ending our turn.
                // This will make our code wait until the next turn, and then perform this loop again.
                turnCount += 1;
                if (startRound != rc.getRoundNum()) {
                    System.out.printf("overran turn from %d to %d", startRound, rc.getRoundNum());
                }
                Clock.yield();
            }
        }

        // Your code should never reach here (unless it's intentional)! Self-destruction imminent...
    }
}
