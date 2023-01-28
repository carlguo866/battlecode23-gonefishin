package submit23;

import battlecode.common.*;
import submit23.util.FastIterableLocSet;

public class MapRecorder extends RobotPlayer {
    // TODO: try to use the leftmost 22 bits for path finding, leave me the right most 10 for scouting
    // perform bit hack to reduce init cost
    public static final char SEEN_BIT = 1 << 4;
    public static final char CLOUD_BIT = 1 << 5;
    public static final char WELL_BIT = 1 << 6;
    public static final char PASSIABLE_BIT = 1 << 7;
    public static final char CURRENT_MASK = 0xF;
    // current use & 0xF for ordinal

    public static char[] vals = Constants.MAP_LEN_STRING.toCharArray();

    public static boolean check(MapLocation loc, Direction targetDir) throws GameActionException {
        if (!rc.onTheMap(loc))
            return false;
        int val = vals[loc.x * mapHeight + loc.y];
        if ((val & SEEN_BIT) == 0)
            return true;
        if ((val & PASSIABLE_BIT) == 0)
            return false;
        if (rc.getType() == RobotType.CARRIER && rc.getWeight() <= 12) {
            return true;
        }
        else {
            Direction current = Direction.values()[val & CURRENT_MASK];
            return (current == targetDir || current == targetDir.rotateLeft() || current == targetDir.rotateRight() || current == Direction.CENTER);
        }
    }

    public static void recordSym(int leaveBytecodeCnt) throws GameActionException {
        MapInfo[] infos = rc.senseNearbyMapInfos();
        for (int i = infos.length; --i >= 0; ) {
            if (Clock.getBytecodesLeft() <= leaveBytecodeCnt) {
                return;
            }
            if ((vals[infos[i].getMapLocation().x * mapHeight + infos[i].getMapLocation().y] & SEEN_BIT) != 0)
                continue;
            MapInfo info = infos[i];
            int x = info.getMapLocation().x;
            int y = info.getMapLocation().y;
            char val = SEEN_BIT;
            if (info.isPassable())
                val |= PASSIABLE_BIT;
            Direction current = info.getCurrentDirection();
            val |= current.ordinal();

            if (!Comm.isSymmetryConfirmed) {
                if (info.hasCloud())
                    val |= CLOUD_BIT;
                if (rc.senseWell(info.getMapLocation()) != null)
                    val |= WELL_BIT;
                Direction symCurrent;
                int symVal;
                boolean isSym;
                for (int sym = 3; --sym >= 0; ) {
                    if (Comm.isSymEliminated[sym])
                        continue;
                    symVal = vals[((sym & 1) == 0 ? mapWidth - x - 1 : x) * mapHeight + ((sym & 2) == 0 ? mapHeight - y - 1 : y)];
                    if ((symVal & SEEN_BIT) == 0) {
                        continue;
                    }
                    switch (sym) {
                        case Comm.SYM_ROTATIONAL:
                            symCurrent = current.opposite();
                            break;
                        case Comm.SYM_VERTIAL:
                            symCurrent = Unit.Dxy2dir(current.dx, current.dy * -1);
                            break;
                        default: // HORIZONAL
                            symCurrent = Unit.Dxy2dir(current.dx * -1, current.dy);
                            break;
                    }
                    isSym = (val | CURRENT_MASK) == (symVal | CURRENT_MASK)
                            && (symCurrent.ordinal() == (symVal & CURRENT_MASK));
                    if (!isSym) {
                        Comm.eliminateSym(sym);
                    }
                }
            }
            vals[x * mapHeight + y] = val;
        }
    }

    public static FastIterableLocSet getMinableSquares(MapLocation mineLoc) {
        FastIterableLocSet set = new FastIterableLocSet(10);
        set.add(mineLoc);
        for (int i = 8; --i>=0;) {
            MapLocation loc = mineLoc.add(Constants.directions[i]);
            if (!rc.onTheMap(loc)) {
                continue;
            }
            char val = vals[loc.x * mapHeight + loc.y];
            if ((val & SEEN_BIT) == 0) {
                set.add(loc);
            } else {
                if ((val & PASSIABLE_BIT) == 0) {
                    continue;
                }
                Direction dir = Direction.values()[val & CURRENT_MASK];
                MapLocation blowInto = loc.add(dir);
                if (blowInto.isAdjacentTo(mineLoc) ||
                        (rc.onTheMap(blowInto) && (vals[blowInto.x * mapHeight + blowInto.y] & PASSIABLE_BIT) == 0 && (vals[blowInto.x * mapHeight + blowInto.y] & SEEN_BIT) != 0)) {
                    set.add(loc);
                }
            }
        }
        return set;
    }

    // this func called at the start of each HQ to get us out of jail
    public static void hqInit() throws GameActionException {
        // use scripts/pos_gen.py
        int HQ_SPAWNABLE_DX[] = {-3, 0, 0, 3, -2, -2, 2, 2, -2, -2, -1, -1, 1, 1, 2, 2, -2, 0, 0, 2, -1, -1, 1, 1, -1, 0, 0, 1};
        int HQ_SPAWNABLE_DY[] = {0, -3, 3, 0, -2, 2, -2, 2, -1, 1, -2, 2, -2, 2, -1, 1, 0, -2, 2, 0, -1, 1, -1, 1, 0, -1, 1, 0};
        MapInfo[] infos = rc.senseNearbyMapInfos();
        for (int i = infos.length; --i >= 0; ) {
            if (infos[i].isPassable()) {
                vals[infos[i].getMapLocation().x * mapHeight + infos[i].getMapLocation().y] = PASSIABLE_BIT;
                Headquarter.sensablePassibleArea++;
            }
        }
        FastIterableLocSet spawnableSet = new FastIterableLocSet(29);
        int hqX = rc.getLocation().x;
        int hqY = rc.getLocation().y;
        spawnableSet.add(hqX, hqY);
        // this is not a BFS and will miss maze-like tiles but it's fast and good enough
        for (int i = HQ_SPAWNABLE_DX.length; --i >= 0;) {
            int x = hqX + HQ_SPAWNABLE_DX[i];
            int y = hqY + HQ_SPAWNABLE_DY[i];
            if (x < 0 || x >= mapWidth || y < 0 || y >= mapHeight || (vals[x * mapHeight + y] & PASSIABLE_BIT) == 0)
                continue;
            if (spawnableSet.contains(x + 1, y)) {spawnableSet.add(x, y); continue;}
            if (spawnableSet.contains(x + 1, y + 1)) {spawnableSet.add(x, y); continue;}
            if (spawnableSet.contains(x + 1, y - 1)) {spawnableSet.add(x, y); continue;}
            if (spawnableSet.contains(x, y + 1)) {spawnableSet.add(x, y); continue;}
            if (spawnableSet.contains(x, y - 1)) {spawnableSet.add(x, y); continue;}
            if (spawnableSet.contains(x - 1, y)) {spawnableSet.add(x, y); continue;}
            if (spawnableSet.contains(x - 1, y + 1)) {spawnableSet.add(x, y); continue;}
            if (spawnableSet.contains(x - 1, y - 1)) {spawnableSet.add(x, y);}
        }
        spawnableSet.remove(hqX, hqY);
        spawnableSet.updateIterable();
        Headquarter.spawnableSet = spawnableSet;
    }

//    remnents of defensive launcher strategy
//    public static MapLocation findBestLoc(MapLocation wellLoc) {
//        // returns a defensive location around a mine
//        // all enemy HQ apply an attractive vector
//        // force proportional to 1/dis
//        double fx = 0, fy = 0;
//        double disSq;
//        for (int i = Comm.numHQ; --i >= 0;) {
//            disSq = wellLoc.distanceSquaredTo(Comm.enemyHQLocations[i]);
//            fx += (Comm.enemyHQLocations[i].x - wellLoc.x) / disSq * 10;
//            fy += (Comm.enemyHQLocations[i].y - wellLoc.y) / disSq * 10;
//        }
//        double dis = Math.sqrt(fx * fx + fy * fy);
//        if (dis <= 1e-6) {
//            fx = 1;
//            fy = 0;
//        } else {
//            fx = fx / dis;
//            fy = fy / dis;
//        }
//        int x = (int)(wellLoc.x + fx * 6);
//        int y = (int)(wellLoc.y + fy * 6);
//        for (int[] dir : Unit.BFS25) {
//            int tx = x + dir[0];
//            int ty = y + dir[1];
//            if ((vals[tx * mapWidth + mapHeight] & SEEN_BIT) == 0
//                    || ((vals[tx * mapWidth + mapHeight] & PASSIABLE_BIT) != 0 && (vals[tx * mapWidth + mapHeight] & CURRENT_MASK) == 8)) {
//                x = tx;
//                y = ty;
//                break;
//            }
//        }
//        return new MapLocation(x, y);
//    }
}
