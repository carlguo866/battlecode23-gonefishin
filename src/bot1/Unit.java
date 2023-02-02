package bot1;

import battlecode.common.*;
import bot1.util.FastMath;


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

    static void randomMove() throws GameActionException {
        int starting_i = FastMath.rand256() % Constants.directions.length;
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
    private static Direction[] prv = new Direction[PRV_LENGTH];
    private static int pathingCnt = 0;
    private static MapLocation lastPathingTarget = null;
    private static MapLocation lastLocation = null;
    private static int stuckCnt = 0;
    private static int lastPathingTurn = 0;
    private static int currentTurnDir = FastMath.rand256() % 2;
    private static int disableTurnDirRound = 0;

    private static Direction[] prv_ = new Direction[PRV_LENGTH];
    private static int pathingCnt_ = 0;
    static int MAX_DEPTH = 15;

    static void moveToward(MapLocation location) throws GameActionException {
        // reset queue when target location changes or there's gap in between calls
        if (!location.equals(lastPathingTarget) || lastPathingTurn < turnCount - 4) {
            pathingCnt = 0;
            stuckCnt = 0;
        }
        indicator += String.format("2%sc%dt%s,", location, pathingCnt, currentTurnDir == 0? "L":"R");
        if (rc.isMovementReady()) {
            // we increase stuck count only if it's a new turn (optim for empty carriers)
            if (rc.getLocation().equals(lastLocation)) {
                if (turnCount != lastPathingTurn) {
                    stuckCnt++;
                }
            } else {
                lastLocation = rc.getLocation();
                stuckCnt = 0;
            }
            lastPathingTarget = location;
            lastPathingTurn = turnCount;
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
            if (pathingCnt == 0) {
                //if free of obstacle: try go directly to target
                Direction dir = rc.getLocation().directionTo(location);
                boolean dirCanPass = canPass(dir);
                boolean dirRightCanPass = canPass(dir.rotateRight(), dir);
                boolean dirLeftCanPass = canPass(dir.rotateLeft(), dir);
                if (dirCanPass || dirRightCanPass || dirLeftCanPass) {
                    if (dirCanPass && rc.canMove(dir)) {
                        rc.move(dir);
                    } else if (dirRightCanPass && rc.canMove(dir.rotateRight())) {
                        rc.move(dir.rotateRight());
                    } else if (dirLeftCanPass && rc.canMove(dir.rotateLeft())) {
                        rc.move(dir.rotateLeft());
                    }
                } else {
                    //encounters obstacle; run simulation to determine best way to go
                    if (rc.getRoundNum() > disableTurnDirRound) {
                        currentTurnDir = getTurnDir(dir, location);
                    }
                    while (!canPass(dir) && pathingCnt != 8) {
                        rc.setIndicatorLine(rc.getLocation(), rc.getLocation().add(dir), 0, 0, 255);
                        if (!rc.onTheMap(rc.getLocation().add(dir))) {
                            currentTurnDir ^= 1;
                            pathingCnt = 0;
                            indicator += "edge switch";
                            disableTurnDirRound = rc.getRoundNum() + 100;
                            return;
                        }
                        prv[pathingCnt] = dir;
                        pathingCnt++;
                        if (currentTurnDir == 0) dir = dir.rotateLeft();
                        else dir = dir.rotateRight();
                    }
                    if (pathingCnt == 8) {
                        indicator += "permblocked";
                    } else if (rc.canMove(dir)) {
                        rc.move(dir);
                    }
                }
            } else {
                //update stack of past directions, move to next available direction
                while (pathingCnt > 0 && canPass(prv[pathingCnt - 1])) {
                    rc.setIndicatorLine(rc.getLocation(), rc.getLocation().add(prv[pathingCnt - 1]), 0, 255, 0);
                    pathingCnt--;
                }
                int pathingCntCutOff = Math.min(PRV_LENGTH, pathingCnt + 8); // if 8 then all dirs blocked
                while (pathingCnt > 0 && !canPass(currentTurnDir == 0?prv[pathingCnt - 1].rotateLeft():prv[pathingCnt - 1].rotateRight())) {
                    prv[pathingCnt] = currentTurnDir == 0?prv[pathingCnt - 1].rotateLeft():prv[pathingCnt - 1].rotateRight();
                    rc.setIndicatorLine(rc.getLocation(), rc.getLocation().add(prv[pathingCnt]), 255, 0, 0);
                    if (!rc.onTheMap(rc.getLocation().add(prv[pathingCnt]))) {
                        currentTurnDir ^= 1;
                        pathingCnt = 0;
                        indicator += "edge switch";
                        disableTurnDirRound = rc.getRoundNum() + 100;
                        return;
                    }
                    pathingCnt++;
                    if (pathingCnt == pathingCntCutOff) {
                        pathingCnt = 0;
                        indicator += "cutoff";
                        return;
                    }
                }
                Direction moveDir = pathingCnt == 0? prv[pathingCnt] : 
                    (currentTurnDir == 0?prv[pathingCnt - 1].rotateLeft():prv[pathingCnt - 1].rotateRight());
                if (rc.canMove(moveDir)) {
                    rc.move(moveDir);
                } else {
                    // a robot blocking us while we are following wall, wait
                    indicator += "blocked";
                }
            }
        }
        lastPathingTarget = location;
        lastPathingTurn = turnCount;
    }

    static int getSteps(MapLocation a, MapLocation b) {
        int xdif = a.x - b.x;
        int ydif = a.y - b.y;
        if (xdif < 0) xdif = -xdif;
        if (ydif < 0) ydif = -ydif;
        if (xdif > ydif) return xdif;
        else return ydif;
    }

    static int getCenterDir(Direction dir) throws GameActionException {
        double a = rc.getLocation().x - rc.getMapWidth()/2.0;
        double b = rc.getLocation().y - rc.getMapHeight()/2.0;
        double c = dir.dx;
        double d = dir.dy;
        if (a * d - b * c > 0) return 1;
        return 0;
    }

    private static final int BYTECODE_CUTOFF = 3000;
    static int getTurnDir(Direction direction, MapLocation target) throws GameActionException{
        //int ret = getCenterDir(direction);
        MapLocation now = rc.getLocation();
        int moveLeft = 0;
        int moveRight = 0;
        
        pathingCnt_ = 0;
        Direction dir = direction;
        while (!canPass(now.add(dir), dir) && pathingCnt_ != 8) {
            prv_[pathingCnt_] = dir;
            pathingCnt_++;
            dir = dir.rotateLeft();
            if (pathingCnt_ > 8) {
                break;
            }
        }
        now = now.add(dir);

        int byteCodeRem = Clock.getBytecodesLeft();
        if (byteCodeRem < BYTECODE_CUTOFF)
            return FastMath.rand256() % 2;
        //simulate turning left
        while (pathingCnt_ > 0) {
            moveLeft++;
            if (moveLeft > MAX_DEPTH) {
                break;
            }
            if (Clock.getBytecodesLeft() < BYTECODE_CUTOFF) {
                moveLeft = -1;
                break;
            }
            while (pathingCnt_ > 0 && canPass(now.add(prv_[pathingCnt_ - 1]), prv_[pathingCnt_ - 1])) {
                pathingCnt_--;
            }
            while (pathingCnt_ > 0 && !canPass(now.add(prv_[pathingCnt_ - 1].rotateLeft()), prv_[pathingCnt_ - 1].rotateLeft())) {
                prv_[pathingCnt_] = prv_[pathingCnt_ - 1].rotateLeft();
                pathingCnt_++;
                if (pathingCnt_ > 8) {
                    moveLeft = -1;
                    break;
                }
            }
            if (pathingCnt_ > 8 || pathingCnt_ == 0) {
                break;
            }
            Direction moveDir = pathingCnt_ == 0? prv_[pathingCnt_] : prv_[pathingCnt_ - 1].rotateLeft();
            now = now.add(moveDir);
        }
        MapLocation leftend = now;
        pathingCnt_ = 0;
        now = rc.getLocation();
        dir = direction;
        //simulate turning right
        while (!canPass(dir) && pathingCnt_ != 8) {
            prv_[pathingCnt_] = dir;
            pathingCnt_++;
            dir = dir.rotateRight();
            if (pathingCnt_ > 8) {
                break;
            }
        }
        now = now.add(dir);
        
        while (pathingCnt_ > 0) {
            moveRight++;
            if (moveRight > MAX_DEPTH) {
                break;
            }
            if (Clock.getBytecodesLeft() < BYTECODE_CUTOFF) {
                moveRight = -1;
                break;
            }
            while (pathingCnt_ > 0 && canPass(now.add(prv_[pathingCnt_ - 1]), prv_[pathingCnt_ - 1])) {
                pathingCnt_--;
            }
            while (pathingCnt_ > 0 && !canPass(now.add(prv_[pathingCnt_ - 1].rotateRight()), prv_[pathingCnt_ - 1].rotateRight())) {
                prv_[pathingCnt_] = prv_[pathingCnt_ - 1].rotateRight();
                pathingCnt_++;
                if (pathingCnt_ > 8) {
                    moveRight = -1;
                    break;
                }
            }
            if (pathingCnt_ > 8 || pathingCnt_ == 0) {
                break;
            }
            Direction moveDir = pathingCnt_ == 0? prv_[pathingCnt_] : prv_[pathingCnt_ - 1].rotateRight();
            now = now.add(moveDir);
        }
        MapLocation rightend = now;
        //find best direction
        if (moveLeft == -1 || moveRight == -1) return FastMath.rand256() % 2;
        if (moveLeft + getSteps(leftend, target) <= moveRight + getSteps(rightend, target)) return 0;
        else return 1;
        
    }

    static boolean canPass(MapLocation loc, Direction targetDir) throws GameActionException {
        if (loc.equals(rc.getLocation())) return true;
        if (!MapRecorder.check(loc, targetDir)) return false;
        if (!rc.canSenseLocation(loc)) return true;
        RobotInfo robot = rc.senseRobotAtLocation(loc);
        if (robot == null || rc.getNumAnchors(Anchor.STANDARD) == 1)
            return true;
        return false;
//        return FastMath.rand256() % 4 == 0; // rng doesn't seem to help
    }

    static boolean canPass(Direction dir, Direction targetDir) throws GameActionException {
        MapLocation loc = rc.getLocation().add(dir);
        if (!MapRecorder.check(loc, targetDir)) return false;
        RobotInfo robot = rc.senseRobotAtLocation(loc);
        // anchoring carriers don't yield to other robots
        if (robot == null || rc.getNumAnchors(Anchor.STANDARD) == 1)
            return true;
        return FastMath.rand256() % 4 == 0; // Does rng help here? Each rng is 10 bytecode btw
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