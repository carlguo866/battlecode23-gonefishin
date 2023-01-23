package bot1;

import battlecode.common.*;

import java.util.Random;


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
        if (rc.isMovementReady() && dir != Direction.CENTER) {
            if (rc.canMove(dir) && canPass(dir)) {
                rc.move(dir);
            } else if (rc.canMove(dir.rotateRight()) && canPass(dir.rotateRight(), dir)) {
                rc.move(dir.rotateRight());
            } else if (rc.canMove(dir.rotateLeft()) && canPass(dir.rotateLeft(), dir)) {
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
        for (int i = locations.length; --i >= 0;) {
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

    static MapLocation getClosestLoc(MapLocation fromLocation, MapLocation[] locations) {
        return locations[getClosestID(fromLocation, locations)];
    }

    static MapLocation getClosestLoc(MapLocation[] locations) {
        return getClosestLoc(rc.getLocation(), locations);
    }

    // new path finding code from Ray
    private static final int PRV_LENGTH = 60;
    private static final int TURNS_BEFORE_SWITCH = 15;
    private static Direction[] prv = new Direction[PRV_LENGTH];
    private static int pathingCnt = 0;
    private static MapLocation lastPathingTarget = null;
    private static MapLocation lastLocation = null;
    private static int stuckCnt = 0;
    private static int lastPathingTurn = 0;
    private static int currentTurnDir = 0;
    private static int currentTurnLen = 0;
    private static int currentMaxTurnLen = TURNS_BEFORE_SWITCH;
    private static int lastmovecount = 0;
    static void moveToward(MapLocation location) throws GameActionException {
        // reset queue when target location changes or there's gap in between calls
        if (!location.equals(lastPathingTarget) || lastPathingTurn < turnCount - 2) {
            pathingCnt = 0;
        }
        indicator += String.format("2%sc%ds%d,", location, pathingCnt, stuckCnt);

        if (rc.isMovementReady()) {
            // we increase stuck count only if it's a new turn (optim for empty carriers)
            if (rc.getLocation().equals(lastLocation) && turnCount != lastPathingTurn) {
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
            if (stuckCnt >= 10) {
                // make sure if it's a carrier on a well, wait 40 turns
                do {
                    if (rc.getType() == RobotType.CARRIER && rc.getWeight() == GameConstants.CARRIER_CAPACITY) {
                        if (rc.senseWell(rc.getLocation()) != null || stuckCnt < 20) {
                            break; // a carrier on a well should never disintegrate, a carrier with max resource gets extra time
                        }
                        if (rc.getNumAnchors(Anchor.STANDARD) == 1 && stuckCnt < 40) {
                            break; // a carrier trying having an anchor gets extra time
                        }
                    }
                    System.out.printf("loc %s disintegrate due to stuck\n", rc.getLocation());
                    rc.disintegrate();
                } while (false);
            }

            if (currentTurnLen >= currentMaxTurnLen) {
                currentTurnLen = 0;
                pathingCnt = 0;
                currentTurnDir = (currentTurnDir + 1) % 2;
                currentMaxTurnLen = currentMaxTurnLen * 3;
                lastmovecount = 0;
            }

            if (pathingCnt == 0) {
                currentTurnLen = 0;
                if (currentMaxTurnLen > TURNS_BEFORE_SWITCH && lastmovecount > currentMaxTurnLen / 3) {
                    currentMaxTurnLen = TURNS_BEFORE_SWITCH;
                    currentTurnDir = 0;
                    lastmovecount = 0;
                }
                Direction dir = rc.getLocation().directionTo(location);
                if (canPass(dir) || canPass(dir.rotateRight(), dir) || canPass(dir.rotateLeft(), dir)) {
                    tryMoveDir(dir);
                } else {
                    //rng = new Random(rc.getID());
                    //currentTurnDir = rng.nextInt() % 2;
                    while (!canPass(dir) && pathingCnt != 8) {
                        prv[pathingCnt] = dir;
                        pathingCnt++;
                        if (currentTurnDir == 0) dir = dir.rotateLeft();
                        else dir = dir.rotateRight();
                    }
                    if (pathingCnt == 8) {
                        indicator += "permblocked";
                        randomMove();
                    } else if (rc.canMove(dir)) {
                        currentTurnLen++;
                        lastmovecount++;
                        rc.move(dir);
                    }
                }
            } else {
                while (pathingCnt > 0 && canPass(prv[pathingCnt - 1])) {
                    pathingCnt--;
                }
                if (currentTurnDir == 0) {
                    while (pathingCnt > 0 && !canPass(prv[pathingCnt - 1].rotateLeft())) {
                        prv[pathingCnt] = prv[pathingCnt - 1].rotateLeft();
                        pathingCnt++;
                        if (pathingCnt == PRV_LENGTH) {
                            pathingCnt = 0;
                            return;
                        }
                    }
                    Direction moveDir = pathingCnt == 0? prv[pathingCnt] : prv[pathingCnt - 1].rotateLeft();
                    if (rc.canMove(moveDir)) {
                        currentTurnLen++;
                        lastmovecount++;
                        rc.move(moveDir);
                    } else {
                        // a robot blocking us while we are following wall, wait
                        indicator += "blocked";
                    }
                }
                else {
                    while (pathingCnt > 0 && !canPass(prv[pathingCnt - 1].rotateRight())) {
                        prv[pathingCnt] = prv[pathingCnt - 1].rotateRight();
                        pathingCnt++;
                        if (pathingCnt == PRV_LENGTH) {
                            pathingCnt = 0;
                            return;
                        }
                    }
                    Direction moveDir = pathingCnt == 0? prv[pathingCnt] : prv[pathingCnt - 1].rotateRight();
                    if (rc.canMove(moveDir)) {
                        currentTurnLen++;
                        lastmovecount++;
                        rc.move(moveDir);
                    } else {
                        // a robot blocking us while we are following wall, wait
                        indicator += "blocked";
                    }
                }
            }
        }

        lastPathingTarget = location;
        lastPathingTurn = turnCount;
    }

    static boolean canPass(Direction dir, Direction targetDir) throws GameActionException {
        MapLocation loc = rc.getLocation().add(dir);
        if (!rc.onTheMap(loc))
            return false;
        MapInfo info = rc.senseMapInfo(loc);
        if (!info.isPassable())
            return false;
        RobotInfo robot = rc.senseRobotAtLocation(loc);
        if (robot != null)
            return false;
        // disallow going into enemy HQ attack range when symmetry confirmed
        if (getClosestDis(Comm.enemyHQLocations) > 9 && getClosestDis(loc, Comm.enemyHQLocations) <= 9) {
            return false;
        }
        Direction current = info.getCurrentDirection();
        // only allow currents blowing in the direction of the target direction
        if (current == Direction.CENTER || current == targetDir
                || current == targetDir.rotateLeft() || current == targetDir.rotateRight())
            return true;
        // only allow empty carrier to go onto current for now
        return rc.getType() == RobotType.CARRIER && rc.getWeight() <= 12;
    }

    static boolean canPass(Direction dir) throws GameActionException {
        return canPass(dir, dir);
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
