package bot1;

import battlecode.common.*;

/***
 *
 * to represent coord, the value in the shared array is 1 more than usual
 * so that (0,0) in shared array means null
 *
 * Shared array
 * 0-47: 4*2 6bits int specifying the coord of friendly HQs
 * 48-49: 2 bit indicating symmetry of map (0 unknown, 1 rotational, 2 vertial, 3 horizontal)
 *
 * wells info starting bit 96 of 3 types
 * each type of length 14 for a total of 42 bits (96-138)
 * 2 bits: closest HQ ID
 * 12 bits: well location
 *
 * spawn queue bit 160 - 223
 * length: 4
 * each of 16 bits, total of 64 bits
 * 12 bit: coord
 * 4 bit flag
 *
 * enemy report starting bit 224 - 247
 * each of:
 * 12 bit: coord
 * 12 bits: last seen round number, could be % 64 later
 *
 * Anchor stuff bit 248
 * 4 bit signifying if each HQ has anchor
 *
 * 35 islands
 * each 12(6) bits for pos, 4 flags for if conquered
 */
public class Comm extends RobotPlayer {
    private static final int ARRAY_LENGTH = 20; // this is how much we use rn
    private static final int WELL_INFO_BIT = 96;
    private static final int SPAWN_Q_BIT = 160;
    private static final int ENEMY_BIT = 224;

    private static int[] buffered_share_array = new int[ARRAY_LENGTH];
    private static boolean[] is_array_changed = new boolean[ARRAY_LENGTH];
    private static boolean is_array_changed_total = false;

    private static boolean needWellsUpdate = false;

    public static int numHQ = 0;
    public static MapLocation[] friendlyHQLocations = {null, null, null, null};
    public static MapLocation[] enemyHQLocations = {null, null, null, null};

    public static int[] closestHQIDToWells = new int[4];
    public static MapLocation[] closestWells = {null, null, null, null};

    public static final int SPAWN_Q_LENGTH = 4;

    public static void turn_starts() throws GameActionException {
        // TODO only update constant like variable (eg no spawn Q)
        for (int i = 0; i < ARRAY_LENGTH; i++) {
            if (rc.readSharedArray(i) != buffered_share_array[i]) {
                if (i >= 5 && i <= 8) {
                    needWellsUpdate = true;
                }
                buffered_share_array[i] = rc.readSharedArray(i);
            }
        }
        // HQ update should only be done once, at turn 1 for all HQ, and at turn 0 for other units
        if ((turnCount <= 1 && rc.getType() == RobotType.HEADQUARTERS)
                || (turnCount == 0 && rc.getType() != RobotType.HEADQUARTERS)) {
            updateHQLocations();
        }

        if (needWellsUpdate) {
            updateWells();
        }
    }

    // IMPORTANT: always ensure that any write op is performed when writable
    public static void commit_write() throws GameActionException {
        if (is_array_changed_total) {
            for (int i = 0; i < ARRAY_LENGTH; i++) {
                if (is_array_changed[i]) {
                    rc.writeSharedArray(i, buffered_share_array[i]);
                    is_array_changed[i] = false;
                }
            }
            is_array_changed_total = false;
        }
    }

    // HQ locations starting at bit 0

    // this should be called by the setup of each HQ
    public static void HQInit(MapLocation location, int HQID) throws GameActionException {
        // I am assuming HQIDs are < 8, one team using 0/2/4/6 and the other using 1/3/5/7
        assert(HQID < 8);
        HQID /= 2;
        writeBits(HQID * 12, 6, location.x + 1);
        writeBits(HQID * 12 + 6, 6, location.y + 1);
    }

    private static void updateHQLocations() {
        numHQ = 0;
        for (int i = 0; i < 4; i++) {
            int x = readBits(12 * i, 6);
            int y = readBits(12 * i + 6, 6);
            if (x == 0 && y == 0) {
                friendlyHQLocations[i] = null;
            } else {
                friendlyHQLocations[i] = new MapLocation(x - 1, y - 1);
                // assume the map is rotationally symmetric, FIXME
                enemyHQLocations[i] = new MapLocation(rc.getMapWidth() - x, rc.getMapHeight() - y);
                numHQ++;
            }
        }
    }

    // well infos starting
    public static void updateWells() {
        for (int resourceID = 1; resourceID <= 2; resourceID++) {
            int startingBit = WELL_INFO_BIT + (resourceID - 1) * 14;
            closestHQIDToWells[resourceID] = readBits(startingBit, 2);
            int x = readBits(startingBit + 2, 6);
            int y = readBits(startingBit + 8, 6);
            if (x == 0 && y == 0) {
                closestWells[resourceID] = null;
            } else {
                closestWells[resourceID] = new MapLocation(x - 1, y - 1);
            }
        }
        needWellsUpdate = false;
    }

    public static void reportWells(WellInfo well) {
        MapLocation wellLocation = well.getMapLocation();
        ResourceType resourceType = well.getResourceType();

        int closestHQID = 0, minDis = Integer.MAX_VALUE;
        if (rc.getType() == RobotType.HEADQUARTERS) {
            closestHQID = rc.getID() / 2;
            minDis = rc.getLocation().distanceSquaredTo(wellLocation);
        } else {
            for (int i = 0; i < 4; i++) {
                if (friendlyHQLocations[i] != null && wellLocation.distanceSquaredTo(friendlyHQLocations[i]) < minDis) {
                    minDis = wellLocation.distanceSquaredTo(friendlyHQLocations[i]);
                    closestHQID = i;
                }
            }
        }
        assert minDis != Integer.MAX_VALUE;

        int original_dis = Integer.MAX_VALUE;
        if (closestWells[resourceType.resourceID] != null){
            original_dis = closestWells[resourceType.resourceID].distanceSquaredTo(friendlyHQLocations[closestHQIDToWells[resourceType.resourceID]]);
        }
        if (minDis < original_dis) {
            // update shared array
            int startingBit = WELL_INFO_BIT + (resourceType.resourceID - 1) * 14;
            writeBits(startingBit, 2, closestHQID);
            writeBits(startingBit + 2, 6, wellLocation.x + 1);
            writeBits(startingBit + 8, 6, wellLocation.y + 1);
            needWellsUpdate = true;
        }
    }

    // spawn Q starts
    public static MapLocation getSpawnQLoc(int index) {
        assert index < SPAWN_Q_LENGTH;
        int x = readBits(SPAWN_Q_BIT + 16 * index, 6);
        int y = readBits(SPAWN_Q_BIT + 16 * index + 6, 6);
        if (x == 0 && y == 0)
            return null;
        return new MapLocation(x - 1, y - 1);
    }

    public static int getSpawnQFlag(int index) {
        assert index < SPAWN_Q_LENGTH;
        return readBits(SPAWN_Q_BIT + 16 * index + 12, 4);
    }

    // to reset a spawn Q position, set (index, -1, -1, 0)
    public static void setSpawnQ(int index, int x, int y, int flag) {
        assert index < SPAWN_Q_LENGTH;
        writeBits(SPAWN_Q_BIT + index * 16, 6, x + 1);
        writeBits(SPAWN_Q_BIT + index * 16 + 6, 6, y + 1);
        writeBits(SPAWN_Q_BIT + index * 16 + 12, 4, flag);
    }

    // enemy report starting
    public static void reportEnemy(MapLocation location, int roundNumber) {
        if (getEnemyLoc() != null && roundNumber <= getEnemyRound()) { // ignore old report
            return;
        }
        writeBits(ENEMY_BIT, 6, location.x + 1);
        writeBits(ENEMY_BIT + 6, 6, location.y + 1);
        writeBits(ENEMY_BIT + 12, 12, roundNumber);
    }

    public static MapLocation getEnemyLoc() {
        int x = readBits(ENEMY_BIT, 6);
        int y = readBits(ENEMY_BIT + 6, 6);
        if (x == 0 && y == 0)
            return null;
        return new MapLocation(x - 1, y - 1);
    }

    public static int getEnemyRound() {
        return readBits(ENEMY_BIT + 12, 12);
    }

    // helper funcs
    private static int readBits(int startingBitIndex, int length) {
        int rv = 0;
        for (int i = startingBitIndex; i < startingBitIndex + length; i++) {
            int bit = buffered_share_array[i / 16] & (1 << (15 - i % 16)); //bit 0 = buf[0] & (1 << 15)
            bit = bit > 0? 1 : 0;
            rv += bit << (startingBitIndex + length - 1 - i);
        }
        return rv;
    }

    // TODO this needs optimization
    private static void writeBits(int startingBitIndex, int length, int value) {
        assert value < (1 << length);
        for (int i = startingBitIndex; i < startingBitIndex + length; i++) {
            int original_bit = buffered_share_array[i / 16] & (1 << (15 - i % 16)); //bit 0 = buf[0] & (1 << 15)
            original_bit = original_bit > 0? 1 : 0;
            int new_bit = value & (1 << (length - 1 - i + startingBitIndex)); // start: 6, length: 6, i: 7, shift: 4
            new_bit = new_bit > 0? 1 : 0;
            if (original_bit != new_bit) {
                is_array_changed[i / 16] = true;
                is_array_changed_total = true;
                buffered_share_array[i / 16] ^= 1 << (15 - i % 16);
            }
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
    public static void test_bit_ops() {
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
