package submit11;

import battlecode.common.*;


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
    static void randomMove() throws GameActionException {
        int starting_i = Constants.rng.nextInt(Constants.directions.length);
        for (int i = starting_i; i < starting_i + 8; i++) {
            Direction dir = Constants.directions[i % 8];
            if (rc.canMove(dir) && canPass(dir)) rc.move(dir);
        }
    }

    static void tryMoveDir(Direction dir) throws GameActionException {
        if (rc.isMovementReady()) {
            if (rc.canMove(dir)) {
                rc.move(dir);
            } else if (rc.canMove(dir.rotateRight())) {
                rc.move(dir.rotateRight());
            } else if (rc.canMove(dir.rotateLeft())) {
                rc.move(dir.rotateLeft());
            } else if (rc.canMove(dir.rotateRight().rotateRight())) {
                rc.move(dir.rotateRight().rotateRight());
            } else if (rc.canMove(dir.rotateLeft().rotateLeft())) {
                rc.move(dir.rotateLeft().rotateLeft());
            } else {
                randomMove();
            }
        }
    }
    static void follow(MapLocation location) throws GameActionException {
        tryMoveDir(rc.getLocation().directionTo(location));
    }

    static int getClosestID(MapLocation fromLocation, MapLocation[] locations) {
        int dis = Integer.MAX_VALUE;
        int rv = -1;
        for (int i = 0; i < locations.length; i++) {
            MapLocation location = locations[i];
            if (location != null) {
                int newDis = fromLocation.distanceSquaredTo(location);
                if (newDis < dis) {
                    rv = i;
                    dis = newDis;
                }
            }
        }
        assert dis != Integer.MAX_VALUE;
        return rv;
    }
    static int getClosestID(MapLocation[] locations) {
        return getClosestID(rc.getLocation(), locations);
    }

    static int getClosestDis(MapLocation fromLocation, MapLocation[] locations) {
        int id = getClosestID(fromLocation, locations);
        return fromLocation.distanceSquaredTo(locations[id]);
    }
    static int getClosestDis(MapLocation[] locations) {
        return getClosestDis(rc.getLocation(), locations);
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
        indicator += String.format("cnt%d,", pathingCnt);
        lastPathingTarget = location;
        lastPathingTurn = turnCount;

        if (rc.isMovementReady()) {
            if (pathingCnt == 0) {
                Direction dir = rc.getLocation().directionTo(location);
                while ((!rc.canMove(dir) || !canPass(dir)) && pathingCnt != 8) {
                    MapLocation loc = rc.getLocation().add(dir);
                    if (rc.onTheMap(loc) && rc.senseRobotAtLocation(loc) != null && rc.senseRobotAtLocation(loc).type != RobotType.HEADQUARTERS) {
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
                    return;
                }
            } else {
                while (pathingCnt > 0 && canPass(prv[pathingCnt - 1])) {
                    pathingCnt--;
                }
                while (pathingCnt > 0 && !canPass(prv[pathingCnt - 1].rotateLeft())) {
                    prv[pathingCnt] = prv[pathingCnt - 1].rotateLeft();;
                    pathingCnt++;
                    if (pathingCnt == PRV_LENGTH) {
                        pathingCnt = 0;
                        return;
                    }
                }
                Direction moveDir = pathingCnt == 0? prv[pathingCnt] : prv[pathingCnt - 1].rotateLeft();
                if (rc.canMove(moveDir)) {
                    rc.move(moveDir);
                } else {
                    // a robot blocking us while we are following wall, wait
                    indicator += "blocked";
                    return;
                }
            }
        }
    }

    static boolean canPass(MapLocation loc) throws GameActionException {
        if (!rc.onTheMap(loc) || !rc.senseMapInfo(loc).isPassable())
            return false;
        RobotInfo robot = rc.senseRobotAtLocation(loc);
        if (robot != null && robot.type == RobotType.HEADQUARTERS)
            return false;
        // only allow empty carrier to go onto current for now
        if (rc.senseMapInfo(loc).getCurrentDirection() != Direction.CENTER)
            return rc.getType() == RobotType.CARRIER && rc.getWeight() <= 12;
        return true;
    }

    static boolean canPass(Direction dir) throws GameActionException {
        return canPass(rc.getLocation().add(dir));
    }

    static Direction Dxy2dir(int dx, int dy) {
        if (dx == 0 && dy == 0) return Direction.CENTER;
        if (dx == 0 && dy == 1) return Direction.NORTH;
        if (dx == 0 && dy == -1) return Direction.SOUTH;
        if (dx == 1 && dy == 0) return Direction.EAST;
        if (dx == 1 && dy == 1) return Direction.NORTHEAST;
        if (dx == 1 && dy == -1) return Direction.SOUTHEAST;
        if (dx == -1 && dy == 0) return Direction.WEST;
        if (dx == -1 && dy == 1) return Direction.NORTHWEST;
        if (dx == -1 && dy == -1) return Direction.SOUTHWEST;
        assert false; // shouldn't reach here
        return null;
    }

}
