package bot1;

import battlecode.common.*;

import static bot1.Constants.*;

/**
 * This class contains logic / variable that is shared between all units
 * pathfinding logics will be here
 */
public class Unit extends RobotPlayer {

    public static final int[][] BFS25 = {
            {0, 0},
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
            {2, 0}, {0, 2}, {-2, 0}, {0, -2},
            {2, 1}, {2, -1}, {-2, 1}, {-2, -1}, {1, 2}, {1, -2}, {-1, 2}, {-1, -2},
            {2, 2}, {2, -2}, {-2, 2}, {-2, -2}
    };

    // TODO path finding
    // TODO deal with blow back current
    static void randomMove() throws GameActionException {
        int starting_i = rng.nextInt(directions.length);
        for (int i = starting_i; i < starting_i + 8; i++) {
            Direction dir = directions[i % 8];
            if (rc.canMove(dir)) rc.move(dir);
        }
    }

    static void follow(MapLocation location) throws GameActionException {
        if (rc.isMovementReady()) {
            Direction dir = rc.getLocation().directionTo(location);
            if (rc.canMove(dir)) {
                rc.move(dir);
            } else if (rc.canMove(dir.rotateRight())) {
                rc.move(dir.rotateRight());
            } else if (rc.canMove(dir.rotateLeft())) {
                rc.move(dir.rotateLeft());
            } else {
                randomMove();
            }
        }
    }

    static int getClosestID(MapLocation[] locations) {
        int dis = Integer.MAX_VALUE;
        int rv = 0;
        for (int i = 0; i < locations.length; i++) {
            MapLocation location = locations[i];
            if (location != null) {
                int newDis = rc.getLocation().distanceSquaredTo(location);
                if (newDis < dis) {
                    rv = i;
                    dis = newDis;
                }
            }
        }
        assert dis != Integer.MAX_VALUE;
        return rv;
    }

    // new path finding code from Ray
    private static final int PRV_LENGTH = 60;
    private static Direction[] prv = new Direction[PRV_LENGTH];
    private static int pathingCnt = 0;
    private static MapLocation lastPathingTarget = null;
    private static int lastPathingTurn = 0;

    static void moveToward(MapLocation location) throws GameActionException {
        // reset queue when target location changes or there's gap in between calls
        if (!location.equals(lastPathingTarget) || lastPathingTurn < turnCount - 1) {
            pathingCnt = 0;
        }
        lastPathingTarget = location;
        lastPathingTurn = turnCount;
        indicator += String.format("pfinding_cnt %d,", pathingCnt);

        if (rc.isMovementReady()) {
            if (pathingCnt == 0) {
                Direction dir = rc.getLocation().directionTo(location);
                while (!rc.canMove(dir) && pathingCnt != 8) {
                    MapLocation loc = rc.getLocation().add(dir);
                    if (rc.onTheMap(loc) && rc.senseRobotAtLocation(loc) != null) {
                        // a robot is blocking our way, reset and use follow instead
                        pathingCnt = 0;
                        indicator += "use follow,";
                        follow(location);
                        return;
                    }
                    prv[pathingCnt] = dir;
                    pathingCnt++;
                    dir = dir.rotateLeft();
                }
                if (pathingCnt != 8) {
                    rc.move(dir);
                } else {
                    // we are blocked in all directions, nothing to do
                    indicator += "perma blocked,";
                    pathingCnt = 0;
                }
            }
            else {
                while (pathingCnt > 0
                        && rc.senseMapInfo(rc.getLocation().add(prv[pathingCnt - 1])).isPassable()) {
                    pathingCnt--;
                }
                while (pathingCnt > 0 &&
                        !rc.senseMapInfo(rc.getLocation().add(prv[pathingCnt - 1].rotateLeft())).isPassable()) {
                    prv[pathingCnt] = prv[pathingCnt - 1].rotateLeft();;
                    pathingCnt++;
                }
                Direction moveDir = pathingCnt == 0? prv[pathingCnt] : prv[pathingCnt - 1].rotateLeft();
                if (rc.canMove(moveDir)) {
                    rc.move(moveDir);
                } else {
                    // a robot blocking us while we are following wall, wait
                    indicator += "blocked";
                }
            }
        }
    }
}
