package bot1;

import battlecode.common.*;
import static bot1.Constants.*;


/**
 * This class contains logic / variable that is shared between all units
 * pathfinding logics will be here
 */
public class Unit extends RobotPlayer {

    static Direction[] prv = new Direction[60];
    static int count = 0;
    /*
    static int[][] gamemap = new int[60][60];
    static int[][] moves = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    static void randomMove() throws GameActionException{
        int starting_i = rng.nextInt(directions.length);
        for (int i = starting_i; i < starting_i + 8; i++) {
            Direction dir = directions[i % 8];
            if (rc.canMove(dir)) rc.move(dir);
        }
    }
 
    static int[] findPath(int startx, int starty, int endx, int endy){
        int[][] parent = new int[60][60];
        Arrays.fill(parent, -1);
        int[][] queue = new int[3600][2];
        int l = 0, r = 0;
        queue[0][0] = startx;
        queue[0][1] = starty;
        while (l<=r){
            int nowx = queue[l][0];
            int nowy = queue[l][1];
            if (nowx == endx && nowy == endy) {
                break;
            }
            l++;
            for (int i = 0; i < 8; i++){
                int newx = nowx + moves[i][0];
                int newy = nowy + moves[i][1];
                if (gamemap[newx][newy] > 0 && parent[newx][newy] == -1){
                    r++;
                    queue[r][0] = newx;
                    queue[r][1] = newy;
                    parent[newx][newy] = nowx * 60 + nowy;
                }

            }
        } 
        if (parent[endx][endy] > 0){
            int[][] path = new int[3600][2];
            int len = 0;
            int nowx = endx;
            int nowy = endy;
            while (nowx != startx || nowy != starty) {
                path[len][0] = nowx;
                path[len][1] = nowy;
                len++;
                int nxt = parent[nowx][nowy];
                nowy = nxt % 60;
                nowx = (nxt - nowy) / 60;
            }
            
            return path[len-2];
        }  
        int[] x = {-1, -1};
        return x;
    }
*/
    static void moveToward(MapLocation location) throws GameActionException {
        if (rc.isMovementReady()) {
            /*
            MapInfo[] mp = rc.senseNearbyMapInfos(-1);
            int optdis = 10000000;
            MapLocation target = location;
            for (int i = 0; i < mp.length; i++) {
                MapLocation loc = mp[i].getMapLocation();
                
                if (!mp[i].isPassable()) {
                    gamemap[loc.x][loc.y] = -1;
                }
                else {
                    if (location.distanceSquaredTo(loc) < optdis) {
                        optdis = location.distanceSquaredTo(loc);
                        target = loc;
                    }
                    gamemap[loc.x][loc.y] = 1;
                }
            }
            int[] path = findPath(rc.getLocation().x, rc.getLocation().y, target.x, target.y);
            if (path[0] < 0){
                randomMove();
            }
            
            Direction dir = rc.getLocation().directionTo(new MapLocation(path[0], path[1]));
            if (rc.canMove(dir))
                rc.move(dir);
            else {
                randomMove();
            }
            */
            
            if (count == 0) {
                Direction dir = rc.getLocation().directionTo(location);
                while (!rc.canMove(dir)) {
                    prv[count] = dir;
                    count++;
                    dir = dir.rotateLeft();
                }
                rc.move(dir);
            }
            else {
                while (count > 0 && rc.canMove(prv[count-1])) {
                    count--;
                }
                while (count > 0 && !rc.canMove(prv[count-1].rotateLeft())) {
                    prv[count] = prv[count-1].rotateLeft();
                    count++;
                }
                if (count == 0) {
                    rc.move(prv[count]);
                }
                else {
                    rc.move(prv[count-1].rotateLeft());
                }
                
            }
        }
    }
}
