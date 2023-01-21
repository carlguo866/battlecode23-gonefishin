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

    static int heuristicDis(MapLocation a, MapLocation b) {
        return (int)Math.sqrt((double)a.distanceSquaredTo(b));
        //return Math.max(Math.abs(a.x-b.x), Math.abs(a.y-b.y));
    }

    static int getId(MapLocation a){
        return (a.x + 3) * 65 + (a.y + 3);
    }

    private static boolean[] visited = new boolean[4000];
    static double getAngle(MapLocation a, MapLocation b) {
        double angle = (double) Math.toDegrees(Math.atan2(b.y - a.y, b.x - a.x));
    
        if(angle < 0){
            angle += 360;
        }
    
        return angle;
    }

    static int bestMetric = 10000;
    static MapLocation lasttarget;
    static int staycount = 0;
    static void moveToward(MapLocation location) throws GameActionException {
        Direction dir;
        MapLocation now = rc.getLocation();
        MapLocation target = location;
        MapLocation target2 = location;
        dir = now.directionTo(target);
        now = now.add(dir);
        System.out.println("*********");
        System.out.println(location);
        System.out.println(dir);
        MapLocation[] queue = new MapLocation[100];
        int head = 0;
        int tail = 0;
        while (rc.canSenseLocation(now) && rc.onTheMap(now)) {
            if (now.x == target.x && now.y == target.y) {
                break;
            }
            System.out.println("------------");
            System.out.println(now);
            MapInfo info = rc.senseMapInfo(now);
            if (!info.isPassable() || info.getCurrentDirection() == dir.opposite() ||
            rc.senseRobotAtLocation(now) != null) {
                head = 0;
                tail = 0;
                queue[0] = now;
                visited[getId(now)] = true;
                visited[getId(rc.getLocation())] = true;
                double leftmost = 0;
                double rightmost = 0;
                MapLocation left = now;
                MapLocation right = now;
                double ang0 = getAngle(rc.getLocation(), now);
                System.out.println("=========");
                while (head <= tail) {
                    MapLocation top = queue[head++];
                    System.out.println(top);
                    double ang = getAngle(rc.getLocation(), top) - ang0;
                    if (ang < -180) ang += 360;
                    if (ang > 180) ang = ang - 360;
                    if (ang > leftmost) {
                        leftmost = ang;
                        left = top;
                    }
                    if (ang < rightmost) {
                        rightmost = ang;
                        right = top;
                    }
                    MapLocation neigh = top.add(Direction.EAST);
                    if (rc.canSenseLocation(neigh) && !visited[getId(neigh)]) {
                        if (!rc.onTheMap(neigh)) {
                            queue[++tail] = neigh;
                            visited[getId(neigh)] = true;
                        }
                        else {
                            info = rc.senseMapInfo(neigh);
                            if (!info.isPassable() || 
                            (rc.senseRobotAtLocation(neigh) != null && rc.senseRobotAtLocation(neigh).getType() == RobotType.HEADQUARTERS)) {
                                queue[++tail] = neigh;
                                visited[getId(neigh)] = true;
                            }
                        }
                    }
                    neigh = top.add(Direction.WEST);
                    if (rc.canSenseLocation(neigh) && !visited[getId(neigh)]) {
                        if (!rc.onTheMap(neigh)) {
                            queue[++tail] = neigh;
                            visited[getId(neigh)] = true;
                        }
                        else {
                            info = rc.senseMapInfo(neigh);
                            if (!info.isPassable() || 
                            (rc.senseRobotAtLocation(neigh) != null && rc.senseRobotAtLocation(neigh).getType() == RobotType.HEADQUARTERS)) {
                                queue[++tail] = neigh;
                                visited[getId(neigh)] = true;
                            }
                        }
                    }
                    neigh = top.add(Direction.SOUTH);
                    if (rc.canSenseLocation(neigh) && !visited[getId(neigh)]) {
                        if (!rc.onTheMap(neigh)) {
                            queue[++tail] = neigh;
                            visited[getId(neigh)] = true;
                        }
                        else {
                            info = rc.senseMapInfo(neigh);
                            if (!info.isPassable() ||
                            (rc.senseRobotAtLocation(neigh) != null && rc.senseRobotAtLocation(neigh).getType() == RobotType.HEADQUARTERS)) {
                                queue[++tail] = neigh;
                                visited[getId(neigh)] = true;
                            }
                        }
                    }
                    neigh = top.add(Direction.NORTH);
                    if (rc.canSenseLocation(neigh) && !visited[getId(neigh)]) {
                        if (!rc.onTheMap(neigh)) {
                            queue[++tail] = neigh;
                            visited[getId(neigh)] = true;
                        }
                        else {
                            info = rc.senseMapInfo(neigh);
                            if (!info.isPassable() || 
                            (rc.senseRobotAtLocation(neigh) != null && rc.senseRobotAtLocation(neigh).getType() == RobotType.HEADQUARTERS)) {
                                queue[++tail] = neigh;
                                visited[getId(neigh)] = true;
                            }
                        }
                    }
                }
                for (int i = 0; i <= tail; i++) {
                    visited[getId(queue[i])] = false;
                }
                if (heuristicDis(rc.getLocation(), left) + heuristicDis(left, target) < 
                    heuristicDis(rc.getLocation(), right) + heuristicDis(right, target)) {
                    target = left.add(rc.getLocation().directionTo(left).rotateLeft().rotateLeft());
                    target2 = right.add(rc.getLocation().directionTo(right).rotateRight().rotateRight());
                }
                else {
                    target = right.add(rc.getLocation().directionTo(right).rotateRight().rotateRight());
                    target2 = left.add(rc.getLocation().directionTo(left).rotateLeft().rotateLeft());
                }
                now = rc.getLocation();
            }
            
            dir = now.directionTo(target);
            now = now.add(dir);
        }
        int newmetric = heuristicDis(rc.getLocation(), target) + heuristicDis(location, target);
        if (lasttarget == null || lasttarget.x != location.x || lasttarget.y != location.y) {
            lasttarget = location;
            bestMetric = newmetric;
        }
        if (newmetric > bestMetric) {
            target = target2;
        }
        //System.out.println("$$$$$$$");
        //System.out.println(startingdir);
        if (rc.canMove(rc.getLocation().directionTo(target))){
            rc.move(rc.getLocation().directionTo(target));
            staycount = 0;
        }
        else {
            staycount += 1;
            if (staycount > 3) {
                randomMove();
                staycount = 0;
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
