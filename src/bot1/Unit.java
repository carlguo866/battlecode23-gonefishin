package bot1;

import battlecode.common.*;
import static bot1.Constants.*;

/**
 * This class contains logic / variable that is shared between all units
 * pathfinding logics will be here
 */
public class Unit extends RobotPlayer {
    // TODO path finding
    static void randomMove() throws GameActionException {
        int starting_i = rng.nextInt(directions.length);
        for (int i = starting_i; i < starting_i + 8; i++) {
            Direction dir = directions[i % 8];
            if (rc.canMove(dir)) rc.move(dir);
        }
    }

    static void moveToward(MapLocation location) throws GameActionException {
        if (rc.isMovementReady()) {
            Direction dir = rc.getLocation().directionTo(location);
            if (rc.canMove(dir))
                rc.move(dir);
            else {
                randomMove();
            }
        }
    }
}
