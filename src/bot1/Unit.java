package bot1;

import battlecode.common.*;
import static bot1.Constants.*;
import bot1.BinaryHeap;


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
        int starting_i = rng.nextInt(directions.length);
        for (int i = starting_i; i < starting_i + 8; i++) {
            Direction dir = directions[i % 8];
            if (rc.canMove(dir) && rc.senseMapInfo(rc.getLocation().add(dir)).getCurrentDirection() == Direction.CENTER) rc.move(dir);
        }
    }

    // new path finding code from Ray
    private static final int PRV_LENGTH = 60;
    private static Direction[] prv = new Direction[PRV_LENGTH];
    private static int lastturn = 0;
    private static int pathlen = 0;
    private static int pathingCnt = 0;
    private static int currentMaxPathLen = 10;
    private static MapLocation lastPathingTarget = null;
    private static int lastPathingTurn = 0;

    static void longMoveToward(MapLocation location) throws GameActionException {
        
    }

    static void moveToward(MapLocation location) throws GameActionException {
        // reset queue when target location changes or there's gap in between calls
        if (!location.equals(lastPathingTarget) || lastPathingTurn < turnCount - 1) {
            pathingCnt = 0;
            pos = 0;
            lastturn = 0;
            pathlen = 0;
            currentMaxPathLen = 10;
        }
        lastPathingTarget = location;
        lastPathingTurn = turnCount;

        
        if (rc.isMovementReady()) {
            /*
            MapInfo[] maps = rc.senseNearbyMapInfos();
            for (int i = 0; i < maps.length; i++) {
                gamemap[getId(maps[i].getMapLocation())] = maps[i].isPassable()?1:0;
                if (rc.senseRobotAtLocation(maps[i].getMapLocation())!= null) {
                    gamemap[getId(maps[i].getMapLocation())] = 0;
                    pos = 0;
                }
                currentmap[getId(maps[i].getMapLocation())] = maps[i].getCurrentDirection();
            }
            if (pos == 0) {
                moveTowardA(location);
            }
            if (pos > 0) {
                Direction dir = rc.getLocation().directionTo(getLoc(actionArray[pos-1]));
                if (rc.canMove(dir)) {
                    rc.move(dir);
                    pos--;
                    return ;
                }
                else {
                    pos = 0;
                }
            }
             */
            pathlen += 1;
            if (pathlen > currentMaxPathLen) {
                pathlen = 0;
                currentMaxPathLen = currentMaxPathLen * 3;
                lastturn = (lastturn + 1) % 2;
                pathingCnt = 0;
            }
            if (pathingCnt == 0) {
                pathlen = 0;
                Direction dir = rc.getLocation().directionTo(location);
                while ((!rc.canMove(dir) || rc.senseMapInfo(rc.getLocation().add(dir)).getCurrentDirection() != Direction.CENTER) 
                        && pathingCnt != 8) {
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
                    if (lastturn == 1) dir = dir.rotateLeft();
                    else dir = dir.rotateRight();
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
                while (pathingCnt > 0
                        && (rc.senseMapInfo(rc.getLocation().add(prv[pathingCnt - 1])).isPassable() && 
                        rc.senseMapInfo(rc.getLocation().add(prv[pathingCnt - 1])).getCurrentDirection() == Direction.CENTER)) {
                    pathingCnt--;
                }
                while (pathingCnt > 0 &&
                        (!rc.senseMapInfo(rc.getLocation().add(prv[pathingCnt - 1].rotateLeft())).isPassable() ||
                        rc.senseMapInfo(rc.getLocation().add(prv[pathingCnt - 1].rotateLeft())).getCurrentDirection() != Direction.CENTER)) {
                    prv[pathingCnt] = prv[pathingCnt - 1].rotateLeft();;
                    pathingCnt++;
                }
                Direction moveDir = pathingCnt == 0? prv[pathingCnt] : prv[pathingCnt - 1].rotateLeft();
                if (rc.canMove(moveDir) && rc.senseMapInfo(rc.getLocation().add(moveDir)).getCurrentDirection() == Direction.CENTER) {
                    rc.move(moveDir);
                } else {
                    // a robot blocking us while we are following wall, wait
                    indicator += "blocked";
                    return;
                }
            }
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

    static boolean canPass(MapLocation loc) throws GameActionException {
        if (!rc.senseMapInfo(loc).isPassable() || rc.senseMapInfo(loc).getCurrentDirection() != Direction.CENTER)
            return false;
        RobotInfo robot = rc.senseRobotAtLocation(loc);
        if (robot != null && robot.type == RobotType.HEADQUARTERS)
            return false;
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

    static int[] actionArray = new int[3600];
    static int pos = 0;
    static int[] gamemap = new int[3600];
    static Direction[] currentmap = new Direction[3600];

    static int heuristicDis(MapLocation a, MapLocation b) {
        return a.distanceSquaredTo(b);
    }

    static int getId(MapLocation a){
        return a.x * 60 + a.y;
    }

    static MapLocation getLoc(int x){
        return new MapLocation((x-x%60)/60, x%60);
    }

    static int getDis(MapLocation a, MapLocation b){
        if (gamemap[getId(a)] == 0 || gamemap[getId(b)] == 0) {
            return 10000;
        }
        if (currentmap[getId(a)] != Direction.CENTER || currentmap[getId(b)] != Direction.CENTER) {
            return 10000;
        }
        return 1;
    }

    static void moveTowardA(MapLocation target) throws GameActionException {
        if (gamemap[getId(target)] == 0) {
            pos = 0;
            return ;
        }
        BinaryHeap pQueue = new BinaryHeap(3600);
        int[] distances = new int[3600];
        //Arrays.fill(distances, 1000000);
        distances[getId(rc.getLocation())] = 1;

        int[] priorities = new int[3600];
        //Arrays.fill(priorities, 1000000);
        priorities[getId(rc.getLocation())] = heuristicDis(rc.getLocation(), target) + 1;
        pQueue.insert(priorities[getId(rc.getLocation())] * 10000 + getId(rc.getLocation()));
        boolean[] visited = new boolean[3600];
        int[] parent = new int[3600];
        while (!pQueue.isEmpty()) {
            int top = pQueue.findMin();
            pQueue.delete(0);
            int lowestPriorityIndex = top % 10000;
            if (visited[lowestPriorityIndex]) {
                continue;
            }
            if (lowestPriorityIndex == getId(target)) {
                int now = getId(target);
                pos = 0;
                while (now != getId(rc.getLocation())){
                    actionArray[pos] = now;
                    pos++;
                    now = parent[now];
                }
                return ;
            }

            for (int i = 0; i < Direction.allDirections().length; i++) {
                Direction dir = Direction.allDirections()[i];
                if (dir == Direction.CENTER) {
                    continue;
                }
                MapLocation neigh = getLoc(lowestPriorityIndex).add(dir);
                if (!rc.onTheMap(neigh)) {
                    continue;
                }
                int id = getId(neigh);
                if (gamemap[id] == 0){
                    continue;
                }
                if (!visited[id]) {
                    if (distances[lowestPriorityIndex] + getDis(getLoc(lowestPriorityIndex), neigh) < distances[id] 
                        || distances[id] == 0) {
                        parent[id] = lowestPriorityIndex;
                        distances[id] = distances[lowestPriorityIndex] + getDis(getLoc(lowestPriorityIndex), neigh);
                        priorities[id] = distances[id] + heuristicDis(neigh,target);
                        pQueue.insert(priorities[id] * 10000 + id);
                    }
                }
            }
            visited[lowestPriorityIndex] = true;
        }
    }
}
