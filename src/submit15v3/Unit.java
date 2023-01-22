package submit15v3;

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
            if (rc.canMove(dir)) rc.move(dir);
        }
    }

    static void tryMoveDir(Direction dir) throws GameActionException {
        if (rc.isMovementReady()) {
            if (rc.canMove(dir) && canPass(dir)) {
                rc.move(dir);
            } else if (rc.canMove(dir.rotateRight()) && canPass(dir.rotateRight())) {
                rc.move(dir.rotateRight());
            } else if (rc.canMove(dir.rotateLeft()) && canPass(dir.rotateLeft())) {
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
    private static MapLocation lastLocation = null;
    private static int stuckCnt = 0;
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
            if (rc.getLocation().equals(lastLocation)) {
                stuckCnt += 1;
            } else {
                stuckCnt = 0;
            }
            lastLocation = rc.getLocation();
            if (stuckCnt >= 3) {
                indicator += "stuck reset";
                randomMove();
                pathingCnt = 0;
            }

            if (pathingCnt == 0) {
                Direction dir = rc.getLocation().directionTo(location);
                if (canPass(dir) || canPass(dir.rotateRight()) || canPass(dir.rotateLeft())) {
                    tryMoveDir(dir);
                } else {
                    while (!canPass(dir) && pathingCnt != 8) {
                        prv[pathingCnt] = dir;
                        pathingCnt++;
                        dir = dir.rotateLeft();
                    }
                    if (pathingCnt == 8) {
                        indicator += "permblocked";
                        randomMove();
                    } else if (rc.canMove(dir)) {
                        rc.move(dir);
                    }
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
                }
            }
        }
    }

    static boolean canPass(Direction dir) throws GameActionException {
        MapLocation loc = rc.getLocation().add(dir);
        if (!rc.onTheMap(loc))
            return false;
        MapInfo info = rc.senseMapInfo(loc);
        if (!info.isPassable())
            return false;
        RobotInfo robot = rc.senseRobotAtLocation(loc);
        if (robot != null && robot.type == RobotType.HEADQUARTERS)
            return false;
        // only allow empty carrier to go onto current for now
        Direction current = info.getCurrentDirection();
        if (current == Direction.CENTER || current == dir || current == dir.rotateLeft() || current == dir.rotateRight())
            return true;
        return rc.getType() == RobotType.CARRIER && rc.getWeight() <= 12;
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
