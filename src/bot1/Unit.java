package bot1;

import battlecode.common.*;
import static bot1.Constants.*;

/**
 * This class contains logic / variable that is shared between all units
 * pathfinding logics will be here
 */
public class Unit extends RobotPlayer {
    // TODO path finding
    static void moveToward(MapLocation location) throws GameActionException {
        if (rc.isMovementReady()) {
            Direction dir = rc.getLocation().directionTo(location);
            if (rc.canMove(dir))
                rc.move(dir);
            else {
                dir = directions[rng.nextInt(directions.length)];
                if (rc.canMove(dir))
                    rc.move(dir);
            }
        }
    }
}
