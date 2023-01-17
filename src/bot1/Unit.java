package bot1;

import battlecode.common.*;
import java.util.*;
import java.lang.Math;
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
    static void randomMove() throws GameActionException {
        int starting_i = rng.nextInt(directions.length);
        for (int i = starting_i; i < starting_i + 8; i++) {
            Direction dir = directions[i % 8];
            if (rc.canMove(dir) && rc.senseMapInfo(rc.getLocation().add(dir)).getCurrentDirection() == Direction.CENTER) rc.move(dir);
        }
    }

    static void follow(MapLocation location) throws GameActionException {
        if (rc.isMovementReady()) {
            Direction dir = rc.getLocation().directionTo(location);
            if (rc.canMove(dir) && rc.senseMapInfo(rc.getLocation().add(dir)).getCurrentDirection() == Direction.CENTER) {
                rc.move(dir);
            } else if (rc.canMove(dir.rotateRight()) && rc.senseMapInfo(rc.getLocation().add(dir.rotateRight())).getCurrentDirection() == Direction.CENTER) {
                rc.move(dir.rotateRight());
            } else if (rc.canMove(dir.rotateLeft()) && rc.senseMapInfo(rc.getLocation().add(dir.rotateLeft())).getCurrentDirection() == Direction.CENTER) {
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
            pos = 0;
        }
        lastPathingTarget = location;
        lastPathingTurn = turnCount;

        
        if (rc.isMovementReady()) {
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
                }
                return ;
            }
            
            if (pathingCnt == 0) {
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

    static int[] actionArray = new int[3600];
    static int pos = 0;
    static int[] gamemap = new int[3600];
    static Direction[] currentmap = new Direction[3600];

    static int heuristicDis(MapLocation a, MapLocation b) {
        return (int)Math.sqrt(a.distanceSquaredTo(b));
    }

    static int getId(MapLocation a){
        return a.x * 60 + a.y;
    }

    static MapLocation getLoc(int x){
        return new MapLocation((x-x%60)/60, x%60);
    }

    static int getDis(MapLocation a, MapLocation b){
        if (gamemap[getId(a)] == 0 || gamemap[getId(b)] == 0) {
            return 1000000;
        }
        if (currentmap[getId(a)] != Direction.CENTER || currentmap[getId(b)] != Direction.CENTER) {
            return 1000000;
        }
        return 1;
    }

    static void moveTowardA(MapLocation target) throws GameActionException {
        PriorityQueue<Integer> pQueue = new PriorityQueue<Integer>();
        int[] distances = new int[3600];
        //Arrays.fill(distances, 1000000);
        distances[getId(rc.getLocation())] = 1;

        int[] priorities = new int[25000];
        //Arrays.fill(priorities, 1000000);
        priorities[getId(rc.getLocation())] = heuristicDis(rc.getLocation(), target) + 1;
        pQueue.add((Integer)(priorities[getId(rc.getLocation())] * 10000 + getId(rc.getLocation())));
        boolean[] visited = new boolean[3600];
        int[] parent = new int[3600];
        while (!pQueue.isEmpty()) {
            int top = pQueue.poll();
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
                        pQueue.add((Integer) priorities[id] * 10000 + id);
                    }
                }
            }
            visited[lowestPriorityIndex] = true;
        }
    }
}