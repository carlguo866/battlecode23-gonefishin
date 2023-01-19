package bot1;

import battlecode.common.*;

public class MapRecorder extends RobotPlayer {
    // TODO: try to use the leftmost 22 bits for path finding, leave me the right most 10 for scouting
    // perform bit hack to reduce init cost
    public static final int SEEN_BIT = 1 << 4;
    public static final int CLOUD_BIT = 1 << 5;
    public static final int WELL_BIT = 1 << 6;
    public static final int PASSIABLE_BIT = 1 << 7;
    public static final int ISLAND_BIT = 1 << 8;
    // current use & 0xF for ordinal

    public static int[][] vals = new int[mapWidth][mapHeight];

    public static void record(int leaveBytecodeCnt) throws GameActionException {
        MapInfo[] infos = rc.senseNearbyMapInfos();
        for (int i = infos.length; --i>=0;) {
            if (Clock.getBytecodesLeft() <= leaveBytecodeCnt) {
                return;
            }
            if (vals[infos[i].getMapLocation().x][infos[i].getMapLocation().y] != 0)
                continue;
            MapInfo info = infos[i];
            int x = info.getMapLocation().x;
            int y = info.getMapLocation().y;
            int val = vals[x][y];

            val |= SEEN_BIT;
            if (info.hasCloud())
                val |= CLOUD_BIT;
            if (rc.senseWell(info.getMapLocation()) != null)
                val |= WELL_BIT;
            if (info.isPassable())
                val |= PASSIABLE_BIT;
            Direction current = info.getCurrentDirection();
            val |= current.ordinal();

            if (Comm.isSymmetryConfirmed)
                return;
            Direction symCurrent;
            int symVal;
            boolean isSym;
            for (int sym = 3; --sym >= 0;) {
                if (Comm.isSymEliminated[sym])
                    continue;
                symVal = vals[(sym & 1) == 0? mapWidth - x - 1 : x][(sym & 2) == 0? mapHeight - y - 1 : y];
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
                isSym = (val & 0xFFF0) == (symVal & 0xFFF0) && (symCurrent.ordinal() == (symVal & 0xF));
                if (!isSym) {
                    Comm.eliminateSym(sym);
                    System.out.printf("sym %d elim at %s val %d symval %d\n", sym, info.getMapLocation(), val, symVal);
                }
            }

            vals[x][y] = val;
        }
    }
}
