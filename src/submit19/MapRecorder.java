package submit19;

import battlecode.common.*;
import submit19.util.FastIterableLocSet;

public class MapRecorder extends RobotPlayer {
    // TODO: try to use the leftmost 22 bits for path finding, leave me the right most 10 for scouting
    // perform bit hack to reduce init cost
    public static final int SEEN_BIT = 1 << 4;
    public static final int CLOUD_BIT = 1 << 5;
    public static final int WELL_BIT = 1 << 6;
    public static final int PASSIABLE_BIT = 1 << 7;
    public static final int ISLAND_BIT = 1 << 8;
    public static final int ACCESSIBLE_BIT = 1 << 9;
    public static final int RECORDED_BIT = 1 << 10;
    public static final int CURRENT_MASK = 0xF;
    public static final int SYM_MASK = 0xF0; // all bits used in symmetry checking except current
    // current use & 0xF for ordinal

    public static int[][] vals = new int[mapWidth][mapHeight];

    public static void recordSym(int leaveBytecodeCnt) throws GameActionException {
        if ((vals[rc.getLocation().x][rc.getLocation().y] & RECORDED_BIT) != 0) {
            return;
        }
        MapInfo[] infos = rc.senseNearbyMapInfos();
        for (int i = infos.length; --i >= 0; ) {
            if (Clock.getBytecodesLeft() <= leaveBytecodeCnt) {
                return;
            }
            if ((vals[infos[i].getMapLocation().x][infos[i].getMapLocation().y] & SEEN_BIT) != 0)
                continue;
            MapInfo info = infos[i];
            int x = info.getMapLocation().x;
            int y = info.getMapLocation().y;
            int val = SEEN_BIT;
            if (info.hasCloud())
                val |= CLOUD_BIT;
            if (rc.senseWell(info.getMapLocation()) != null)
                val |= WELL_BIT;
            if (info.isPassable())
                val |= PASSIABLE_BIT;
            Direction current = info.getCurrentDirection();
            val |= current.ordinal();

            Direction symCurrent;
            int symVal;
            boolean isSym;
            for (int sym = 3; --sym >= 0; ) {
                if (Comm.isSymEliminated[sym])
                    continue;
                symVal = vals[(sym & 1) == 0 ? mapWidth - x - 1 : x][(sym & 2) == 0 ? mapHeight - y - 1 : y];
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
                isSym = (val & SYM_MASK) == (symVal & SYM_MASK) && (symCurrent.ordinal() == (symVal & CURRENT_MASK));
                if (!isSym) {
                    Comm.eliminateSym(sym);
                    System.out.printf("sym %d elim at %s val %d symval %d\n", sym, info.getMapLocation(), val, symVal);
                }
            }
            vals[x][y] = val;
        }
        vals[rc.getLocation().x][rc.getLocation().y] |= RECORDED_BIT;
    }

    // currently used to record whether areas have been scouted
    public static void recordFast(int leaveBytecodeCnt) throws GameActionException {
        if ((vals[rc.getLocation().x][rc.getLocation().y] & RECORDED_BIT) != 0) {
            return;
        }
        MapInfo[] infos = rc.senseNearbyMapInfos();
        for (int i = infos.length; --i >= 0; ) {
            if (Clock.getBytecodesLeft() <= leaveBytecodeCnt) {
                return;
            }
            vals[infos[i].getMapLocation().x][infos[i].getMapLocation().y] = (infos[i].isPassable()? (SEEN_BIT | PASSIABLE_BIT) : SEEN_BIT) | infos[i].getCurrentDirection().ordinal();
        }
        vals[rc.getLocation().x][rc.getLocation().y] |= RECORDED_BIT;
    }

    public static FastIterableLocSet getMinableSquares(MapLocation mineLoc) {
        FastIterableLocSet set = new FastIterableLocSet(10);
        set.add(mineLoc);
        for (int i = 8; --i>=0;) {
            MapLocation loc = mineLoc.add(Constants.directions[i]);
            if (!rc.onTheMap(loc)) {
                continue;
            }
            int val = vals[loc.x][loc.y];
            if ((val & SEEN_BIT) == 0) {
                set.add(loc);
            } else {
                if ((val & PASSIABLE_BIT) == 0) {
                    continue;
                }
                Direction dir = Direction.values()[val & CURRENT_MASK];
                MapLocation blowInto = loc.add(dir);
                if (blowInto.isAdjacentTo(mineLoc) ||
                        (rc.onTheMap(blowInto) && (vals[blowInto.x][blowInto.y] & PASSIABLE_BIT) == 0 && (vals[blowInto.x][blowInto.y] & SEEN_BIT) != 0)) {
                    set.add(loc);
                }
            }
        }
        return set;
    }

    // use scripts/pos_gen.py
    private static final int HQ_SPAWNABLE_DELTA[][] = {{-3, 0}, {0, -3}, {0, 3}, {3, 0}, {-2, -2}, {-2, 2}, {2, -2}, {2, 2}, {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}, {-2, 0}, {0, -2}, {0, 2}, {2, 0}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}, {-1, 0}, {0, -1}, {0, 1}, {1, 0}};
    // this func called at the start of each HQ to get us out of jail
    // should take 6k bytecode on regular maps, about 8k if too much currents around HQ
    public static void hqInit() throws GameActionException {
        MapInfo[] infos = rc.senseNearbyMapInfos();
        for (int i = infos.length; --i >= 0; ) {
            if (infos[i].isPassable()) {
                vals[infos[i].getMapLocation().x][infos[i].getMapLocation().y] = PASSIABLE_BIT | infos[i].getCurrentDirection().ordinal();
                Headquarter.sensablePassibleArea++;
            }
        }
        Headquarter.spawnableSet = new FastIterableLocSet(29);
        int hqX = rc.getLocation().x;
        int hqY = rc.getLocation().y;
        vals[hqX][hqY] |= ACCESSIBLE_BIT;
        // this is not a BFS and will miss maze-like tiles but it's fast and good enough
        for (int i = HQ_SPAWNABLE_DELTA.length; --i >= 0;) {
            int x = hqX + HQ_SPAWNABLE_DELTA[i][0];
            int y = hqY + HQ_SPAWNABLE_DELTA[i][1];
            if (x < 0 || x >= mapWidth || y < 0 || y >= mapHeight || (vals[x][y] & PASSIABLE_BIT) == 0)
                continue;
            do {
                if (x + 1 < mapWidth && (vals[x + 1][y] & ACCESSIBLE_BIT) != 0)
                {vals[x][y] |= ACCESSIBLE_BIT; break;}
                if (x + 1 < mapWidth && y - 1 >= 0 && (vals[x + 1][y - 1] & ACCESSIBLE_BIT) != 0)
                {vals[x][y] |= ACCESSIBLE_BIT; break;}
                if (x + 1 < mapWidth && y + 1 < mapHeight && (vals[x + 1][y + 1] & ACCESSIBLE_BIT) != 0)
                {vals[x][y] |= ACCESSIBLE_BIT; break;}
                if (x - 1 >= 0 && (vals[x - 1][y] & ACCESSIBLE_BIT) != 0)
                {vals[x][y] |= ACCESSIBLE_BIT; break;}
                if (x - 1 >= 0 && y - 1 >= 0 && (vals[x - 1][y - 1] & ACCESSIBLE_BIT) != 0)
                {vals[x][y] |= ACCESSIBLE_BIT; break;}
                if (x - 1 >= 0 && y + 1 < mapHeight && (vals[x - 1][y + 1] & ACCESSIBLE_BIT) != 0)
                {vals[x][y] |= ACCESSIBLE_BIT; break;}
                if (y - 1 >= 0 && (vals[x][y - 1] & ACCESSIBLE_BIT) != 0)
                {vals[x][y] |= ACCESSIBLE_BIT; break;}
                if (y + 1 < mapHeight && (vals[x][y + 1] & ACCESSIBLE_BIT) != 0)
                {vals[x][y] |= ACCESSIBLE_BIT; break;}
            } while (false);
            if ((vals[x][y] & ACCESSIBLE_BIT) != 0 && (vals[x][y] & CURRENT_MASK) == 8) {
                Headquarter.spawnableSet.add(x, y);
            }
        }
        if (Headquarter.spawnableSet.size < 5) {
            System.out.println("HQ spawnable area too small, allow spawning on currents and unaccessible areas");
            Headquarter.spawnableSet.updateIterable(); // this makes sure the previous locations have priority
            for (int i = HQ_SPAWNABLE_DELTA.length; --i >= 0;) {
                int x = hqX + HQ_SPAWNABLE_DELTA[i][0];
                int y = hqY + HQ_SPAWNABLE_DELTA[i][1];
                if (x < 0 || x >= mapWidth || y < 0 || y >= mapHeight || (vals[x][y] & PASSIABLE_BIT) == 0 || (x - hqX) * (x - hqX) + (y - hqY) * (y - hqY) > 9)
                    continue;
                Headquarter.spawnableSet.add(x, y);
            }
        }
        Headquarter.spawnableSet.updateIterable();
    }
}
