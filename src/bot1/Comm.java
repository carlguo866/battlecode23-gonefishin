package bot1;

import battlecode.common.*;
import bot1.util.FastIterableIntSet;

/***
 *
 * to represent coord, the value in the shared array is 1 more than usual
 * so that (0,0) in shared array means null
 *
 * Shared array
 * 0-47: 4*2 6bits int specifying the coord of friendly HQs
 * 48-50: 3 bit whether symmetries of the map have been eliminated:
 * [ROTATIONAL, VERTIAL, HORIZONTAL]
 * 64-67: 4 bits indicating whether the 4 HQs are congested
 *
 * well info 96-215 bits
 * 2 types, each 60 bits (5 positionsk)
 *
 * enemy report starting 224
 * 2 bits broadcast roundNumber for alive check
 * 12 bits location,
 *
 * amp starting 400, 8 bits total
 * 2 bits for alive check
 *
 * 35 islands
 * 12 bits for pos, 2 bits for status
 */
public class Comm extends RobotPlayer {
    private static final int ARRAY_LENGTH = 64; // this is how much we use rn
    private static final int SYM_BIT = 48;
    private static final int CONGEST_BIT = 64;
    private static final int CARRIER_REPORT_BIT = 80;
    private static final int WELL_INFO_BIT = 96;
    private static final int ENEMY_ARR_IDX = 14;
    private static final int AMPLIFIER_BIT = 400;
    private static final int ENEMY_BIT = ENEMY_ARR_IDX * 16;
    private static final int ISLAND_BIT = 534;

    private static final int CARRIER_REPORT_LEN = 16;

    public static final int ISLAND_NEUTRAL = 0;
    public static final int ISLAND_FRIENDLY = 1;
    public static final int ISLAND_ENEMY = 2;
    public static final int ISLAND_ON_THE_WAY = 3; // carrier is on the way

    private static int[] buffered_share_array = new int[ARRAY_LENGTH];
    private static FastIterableIntSet changedIndexes = new FastIterableIntSet(ARRAY_LENGTH);

    private static boolean needWellsUpdate = false;

    public static int numHQ = 0;
    public static MapLocation[] friendlyHQLocations = {null, null, null, null};
    public static MapLocation[] enemyHQLocations = {null, null, null, null};

    public static int NUM_WELLS = 5; // number of wells stored per resource
    public static MapLocation[][] closestWells = new MapLocation[4][NUM_WELLS];
    public static MapLocation[][] islands = new MapLocation[3][35];

    public static final int SPAWN_Q_LENGTH = 16;

    public static void turn_starts() throws GameActionException {
        // TODO only update constant like variable (eg no spawn Q)
        boolean needSymUpdate = false;
        for (int i = 0; i < ARRAY_LENGTH; i++) {
            if (rc.readSharedArray(i) != buffered_share_array[i]) {
                if (i >= 6 && i <= 13) {
                    needWellsUpdate = true;
                }
                if (i == 3 && !isSymmetryConfirmed) {
                    needSymUpdate = true;
                }
                buffered_share_array[i] = rc.readSharedArray(i);
            }
        }
        // HQ update should only be done once, at turn 1 for all HQ, and at turn 0 for other units
        if ((turnCount <= 1 && rc.getType() == RobotType.HEADQUARTERS)
                || (turnCount == 0 && rc.getType() != RobotType.HEADQUARTERS)) {
            updateHQLocations();
        }

        if (needSymUpdate || turnCount == 0) {
            updateSym();
        }

        if (needWellsUpdate && rc.getType() != RobotType.LAUNCHER) {
            updateWells();
        }
    }

    // IMPORTANT: always ensure that any write op is performed when writable
    public static void commit_write() throws GameActionException {
        if (changedIndexes.size > 0) {
            changedIndexes.updateIterable();
            int[] indexes = changedIndexes.ints;
            for (int i = changedIndexes.size; --i>=0;) {
                rc.writeSharedArray(indexes[i], buffered_share_array[indexes[i]]);
            }
            changedIndexes.clear();
        }
    }

    // HQ locations starting at bit 0

    // this should be called by the setup of each HQ
    public static int HQInit(MapLocation location, int HQID) {
        for (int i = 0; i < GameConstants.MAX_STARTING_HEADQUARTERS; i++) {
            if (friendlyHQLocations[i] == null) {
                friendlyHQLocations[i] = location;
                writeBits(i * 12, 12, loc2int(location));
                numHQ = i + 1;
                guessSym();
                return i;
            }
        }
        assert false;
        return -1;
    }

    private static void updateHQLocations() throws GameActionException {
        numHQ = 0;
        for (int i = 0; i < 4; i++) {
            friendlyHQLocations[i] = int2loc(readBits(12 * i, 12));
            if (friendlyHQLocations[i] != null) {
                numHQ++;
            } else {
                break;
            }
        }
        if (turnCount == 1 && rc.getType() == RobotType.HEADQUARTERS && !isSymmetryConfirmed) {
            for (int i = numHQ; --i >= 0;) {
                for (int sym = 3; --sym >= 0;) {
                    if (isSymEliminated[sym])
                        continue;
                    MapLocation loc = friendlyHQLocations[i];
                    MapLocation symLoc = new MapLocation(
                            (sym & 1) == 0? mapWidth - loc.x - 1 : loc.x,
                            (sym & 2) == 0? mapHeight - loc.y - 1 : loc.y);
                    if (!rc.canSenseLocation(symLoc)) continue;
                    RobotInfo robot = rc.senseRobotAtLocation(symLoc);
                    if (robot == null || robot.type != RobotType.HEADQUARTERS || robot.team != oppTeam) {
                        isSymEliminated[sym] = true;
                        writeBits(SYM_BIT + sym, 1, 1);
                        System.out.printf("eliminate sym %d from HQ at %s\n", sym, loc);
                    }
                }
            }
            guessSym();
        }
    }

    public static void reportCongest(int hqid, boolean isCongested) {
        writeBits(CONGEST_BIT + hqid, 1, isCongested? 1 : 0);
    }

    public static boolean isCongested() {
        return readBits(CONGEST_BIT, 4) > 0;
    }

    // well infos starting
    public static void updateWells() {
        for (int resourceID = 1; resourceID <= 2; resourceID++) {
            for (int i = 0; i < NUM_WELLS; i++) {
                int startingBit = WELL_INFO_BIT + ((resourceID - 1) * NUM_WELLS + i) * 12;
                closestWells[resourceID][i] = int2loc(readBits(startingBit, 12));
            }
        }
        if (rc.getType() == RobotType.CARRIER && turnCount != 0) {
            Carrier.updateWells();
        }
        needWellsUpdate = false;
    }

    public static void reportWells(int resourceID, MapLocation wellLocation) {
        int closestHQID = Unit.getClosestID(wellLocation, friendlyHQLocations);
        int maxDis = friendlyHQLocations[closestHQID].distanceSquaredTo(wellLocation);

        int updateIndex = -1;
        for (int i = 0; i < NUM_WELLS; i++) {
            if (closestWells[resourceID][i] == null) {
                updateIndex = i;
                break;
            }
            if (closestWells[resourceID][i].equals(wellLocation)){
                return;
            }
            int original_dis = Unit.getClosestDis(closestWells[resourceID][i], friendlyHQLocations);
            if (original_dis > maxDis) {
                maxDis = original_dis;
                updateIndex = i;
            }
        }

        if (updateIndex != -1) {
            // update shared array
            int startingBit = WELL_INFO_BIT + ((resourceID - 1) * NUM_WELLS + updateIndex) * 12;
            writeBits(startingBit, 12, loc2int(wellLocation));
            needWellsUpdate = true;
        }
    }

    // Carrier report
    public static final int CARRIER_REPORT_FREQ = 100;
    public static int getCarrierCnt() {
        return readBits(CARRIER_REPORT_BIT, CARRIER_REPORT_LEN);
    }
    public static int carrierReport() {
        int cnt = getCarrierCnt() + 1;
        writeBits(CARRIER_REPORT_BIT, CARRIER_REPORT_LEN, cnt);
        return cnt;
    }
    public static void resetCarrierCnt() {
        writeBits(CARRIER_REPORT_BIT, CARRIER_REPORT_LEN, 0);
    }

    // enemy report
    // encoding 2 bit of round + 12 bit of loc
    private static final int ENEMY_LEN = 10;

    // called by HQ 0 at start
    public static void updateExpiredEnemy() throws GameActionException {
        int curRound = rc.getRoundNum() % 4;
        int roundToClear = 0;
        switch (curRound) {
            case 3: roundToClear = 1; break;
            case 2: roundToClear = 0; break;
            case 1: roundToClear = 3; break;
            case 0: roundToClear = 2; break;
        }
        for (int i = ENEMY_LEN; --i >= 0;) {
            int val = buffered_share_array[ENEMY_ARR_IDX + i];
            if (val != 0 && (val >> 12) == roundToClear) {
                rc.writeSharedArray(ENEMY_ARR_IDX + i, 0);
//                System.out.printf("clear %s", int2loc(val & 0xFFF));
            }
        }
    }

    public static MapLocation updateEnemy() throws GameActionException {
        if (rc.senseNearbyCloudLocations().length == 0) {
            return null;
        }
        MapLocation rv = null;
        int bestDis = Integer.MAX_VALUE;
        int writeLoc = 0;
        boolean canWrite = rc.canWriteSharedArray(0, 0);
        for (int i = ENEMY_LEN; --i >= 0;) {
            int val = buffered_share_array[ENEMY_ARR_IDX + i];
            if (val != 0) {
                MapLocation loc = int2loc(val & 0xFFF);
                if (rc.canSenseLocation(loc)) {
                    RobotInfo robot = rc.senseRobotAtLocation(loc);
                    if (robot != null && robot.team != oppTeam) {
                        if (canWrite) {
                            rc.writeSharedArray(ENEMY_ARR_IDX + i, 0);
//                            System.out.printf("launcher clear %s", loc);
                        }
                        continue;
                    }
                }
                int dis = rc.getLocation().distanceSquaredTo(loc);
                if (dis < bestDis) {
                    bestDis = dis;
                    rv = loc;
                }
            } else {
                writeLoc = i;
            }
        }
        if (canWrite) {
            int curRound = rc.getRoundNum() % 4;
            RobotInfo[] enemies = rc.senseNearbyRobots(-1, oppTeam);
            for (int i = Math.min(5, enemies.length); --i >= 0;) {
                RobotInfo robot = enemies[i];
                switch (robot.type) {
                    case LAUNCHER:
                    case DESTABILIZER:
                    case AMPLIFIER:
                        rc.writeSharedArray(ENEMY_ARR_IDX + writeLoc, loc2int(robot.location) + (curRound << 12));
                        writeLoc++;
                        if (writeLoc == ENEMY_LEN) {
                            writeLoc = 0;
                        }
                }
            }
        }
        return bestDis <= 25? rv : null;
    }

    // island storage
    public static int getIslandIndex(MapLocation t) {
        for (int i = 0; i < 35; i++) {
            int val = readBits(ISLAND_BIT + i * 14, 14);
            if (val == 0) continue;
            MapLocation loc = int2loc((val - val % 4) / 4);
            if (loc.x == t.x && loc.y == t.y) {
                return (i+1);
            }
        }
        System.out.println("ISLAND NOT FOUND IN COMM");
        return 36;
    }

    // returns the index, or 0 if not found
    private static int islandCache = 0;
    private static int islandCacheRound = 100;
    public static int getClosestFriendlyIslandIndex() {
        // with 35 islands this takes 3500 bytecode... implement caching
        if (rc.getRoundNum() - islandCacheRound < 20) {
            return islandCache;
        }
        int rv = 0;
        MapLocation curLoc = rc.getLocation();
        int dis = Integer.MAX_VALUE;
        for (int i = islandCount; --i >= 0;) {
            int val = readBits(ISLAND_BIT + i * 14, 14);
            if ((val & 3) == ISLAND_FRIENDLY) {
                int newDis = curLoc.distanceSquaredTo(int2loc(val >> 2));
                if (rv == 0 || dis > newDis) {
                    rv = i + 1;
                    dis = newDis;
                }
            }
        }
        islandCache = rv;
        islandCacheRound = rc.getRoundNum();
        return rv;
    }

    public static MapLocation getIslandLocation(int index) {
        return int2loc(readBits(ISLAND_BIT + (index - 1) * 14, 12));
    }

    public static int getIslandStatus(int index) {
        return readBits(ISLAND_BIT + (index - 1) * 14 + 12, 2);
    }

    // status of -1 means no status report (from carrier, default to neutral)
    public static void reportIsland(MapLocation loc, int index, int status) throws GameActionException{
        //storing island index i at (i-1) since island indexes starts at 1
        int val = readBits(ISLAND_BIT + (index - 1) * 14, 14);
        if (val != 0 && (val % 4 == status || status == -1)) {
            return;
        }
        if (status == -1) {
            status = ISLAND_NEUTRAL;
        }
        writeBits(ISLAND_BIT + (index-1) * 14, 14, (loc2int(loc) * 4) + status);
    }

    public static MapLocation getClosestIsland() {
        MapLocation targetLoc = null;
        int dis = Integer.MAX_VALUE;
        for (int i = 0; i < islandCount; i++) {
            int val = readBits(ISLAND_BIT + i * 14, 14);
            if (val == 0 || val % 4 == ISLAND_FRIENDLY) {
                continue;
            }
            MapLocation loc = int2loc(val >> 2);
            if (rc.getLocation().distanceSquaredTo(loc) < dis) {
                targetLoc = loc;
                dis = rc.getLocation().distanceSquaredTo(loc);
            }
        }
        return targetLoc;
    }

    // symmetry checker
    // bit 0-2: whether sym is eliminated
    public static final int SYM_ROTATIONAL = 0;
    public static final int SYM_VERTIAL = 1;
    public static final int SYM_HORIZONTAL = 2;

    public static int symmetry;
    public static boolean isSymmetryConfirmed;
    public static boolean needSymmetryReport;
    public static boolean[] isSymEliminated = new boolean[3];

    public static void eliminateSym(int sym) throws GameActionException {
        isSymEliminated[sym] = true;
        if (rc.canWriteSharedArray(0, 0)) {
            writeBits(SYM_BIT + sym, 1, 1);
            commit_write();
        } else {
            needSymmetryReport = true;
        }
        guessSym();
    }

    public static void updateSym() {
        int bits = readBits(SYM_BIT, 3);
        needSymmetryReport = false;
        for (int sym = 3; --sym >= 0; ) {
            if (!isSymEliminated[sym] && (bits & (1 << (2 - sym))) > 0) {
                isSymEliminated[sym] = true;
            } else if (isSymEliminated[sym] && (bits & (1 << (2 - sym))) == 0) {
                needSymmetryReport = true;
            }
        }
        guessSym();
    }

    public static void reportSym() throws GameActionException {
        if (!needSymmetryReport)
            return;
        int bits = readBits(SYM_BIT, 3);
        for (int sym = 3; --sym >= 0; ) {
            if (isSymEliminated[sym] && (bits & (1 << (2 - sym))) == 0) {
                writeBits(SYM_BIT + sym, 1, 1);
            }
        }
        needSymmetryReport = false;
    }

    public static void guessSym() {
        int numPossible = 0;
        for (int sym = 3; --sym >=0; ) {
            if (!isSymEliminated[sym]) {
                numPossible++;
                symmetry = sym;
            }
        }
        if (numPossible == 0) {
            System.out.println("impossible that no sym is correct, guess rotation");
            symmetry = 0;
            numPossible = 1;
        }
        if (numPossible == 1) {
            isSymmetryConfirmed = true;
        } else {
            isSymmetryConfirmed = false;
        }
        // update enemy HQ loc
        for (int i = numHQ; --i >= 0;) {
            MapLocation loc = friendlyHQLocations[i];
            enemyHQLocations[i] = new MapLocation(
                    (symmetry & 1) == 0? mapWidth - loc.x - 1 : loc.x,
                    (symmetry & 2) == 0? mapHeight - loc.y - 1 : loc.y);
        }
    }

    // helper funcs
    private static int readBits(int left, int length) {
        int endingBitIndex = left + length - 1;
        int rv = 0;
        while (left <= endingBitIndex) {
            int right = Math.min(left | 0xF, endingBitIndex);
            rv = (rv << (right - left + 1)) + ((buffered_share_array[left/16] % (1 << (16 - left%16))) >> (15 - right % 16));
            left = right + 1;
        }
        return rv;
    }

    private static void writeBits(int startingBitIndex, int length, int value) {
        assert value < (1 << length);
        int current_length = length;
        while (current_length > 0){
            int current_ending = startingBitIndex + current_length - 1;
            int len = Math.min(current_ending%16+1, current_length);
            int left = current_ending - len + 1;
            int original_value = (buffered_share_array[left/16] % (1 << (16 - left%16))) >> (15 - current_ending % 16);
            int new_value = value % (1 << len);
            value >>= len;
            if (new_value != original_value){
                changedIndexes.add(current_ending / 16);
                buffered_share_array[current_ending / 16] ^= (new_value^original_value) << (15 - current_ending % 16);
            }
            current_length -= len;
        }
    }

    private static MapLocation int2loc(int val) {
        if (val == 0) {
            return null;
        }
        return new MapLocation(val / 64 - 1, val % 64 - 1);
    }

    private static int loc2int(MapLocation loc) {
        if (loc == null)
            return 0;
        return ((loc.x + 1) * 64) + (loc.y + 1);
    }

    // sanity check func
    public static void test_bit_ops() throws GameActionException {
        writeBits(11, 10, 882);
        assert(readBits(11, 10) == 882);
        writeBits(8, 20, 99382);
        assert(readBits(8, 20) == 99382);
        writeBits(900, 10, 9);
        assert(readBits(900, 10) == 9);
        assert(readBits(8, 20) == 99382);
        writeBits(905, 10, 922);
        assert(readBits(905, 10) == 922);
    }
}
