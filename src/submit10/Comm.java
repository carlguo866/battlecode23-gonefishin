package submit10;

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
 * well info 96-203 bits
 * wells info starting bit 96 of 3 types, each 36 bits total 108 bits
 * each type containing 3 coords repping the 3 wells
 * 12 bits: well location
 *
 * spawn queue bit 208 - 335
 * length: 8
 * each of 16 bits, total of 128 bits
 * 12 bit: coord
 * 4 bit flag
 *
 * enemy report starting bit 336 - 359
 * each of:
 * 12 bit: coord
 * 12 bits: last seen round number, could be % 64 later
 *
 * TODO
 * 35 islands
 * each 12(6) bits for pos, 1 bit for if index confirmed, 2 bit for if conquered
 */
public class Comm extends RobotPlayer {
    private static final int ARRAY_LENGTH = 64; // this is how much we use rn
    private static final int WELL_INFO_BIT = 96;
    private static final int SPAWN_Q_BIT = 208;
    private static final int ENEMY_BIT = 336;
    private static final int ISLAND_BIT = 360;

    private static int[] buffered_share_array = new int[ARRAY_LENGTH];
    private static boolean[] is_array_changed = new boolean[ARRAY_LENGTH];
    private static boolean is_array_changed_total = false;

    private static boolean needWellsUpdate = false;

    public static int numHQ = 0;
    public static MapLocation[] friendlyHQLocations = {null, null, null, null};
    public static MapLocation[] enemyHQLocations = {null, null, null, null};

    public static int NUM_WELLS = 3; // number of wells stored per resource
    public static MapLocation[][] closestWells = new MapLocation[4][NUM_WELLS];

    public static final int SPAWN_Q_LENGTH = 8;

    public static void turn_starts() throws GameActionException {
        // TODO only update constant like variable (eg no spawn Q)
        for (int i = 0; i < ARRAY_LENGTH; i++) {
            if (rc.readSharedArray(i) != buffered_share_array[i]) {
                if (i >= 6 && i <= 13) {
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

        if (needWellsUpdate &&
                (rc.getType() == RobotType.HEADQUARTERS || rc.getType() == RobotType.CARRIER)) {
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
    public static void HQInit(MapLocation location, int HQID) {
        for (int i = 0; i < GameConstants.MAX_STARTING_HEADQUARTERS; i++) {
            if (friendlyHQLocations[i] == null) {
                friendlyHQLocations[i] = location;
                writeBits(i * 12, 12, loc2int(location));
                return;
            }
        }
        assert false;
    }

    private static void updateHQLocations() {
        numHQ = 0;
        for (int i = 0; i < 4; i++) {
            friendlyHQLocations[i] = int2loc(readBits(12 * i, 12));
            if (friendlyHQLocations[i] != null) {
                // assume the map is rotationally symmetric, FIXME
                enemyHQLocations[i] = new MapLocation(rc.getMapWidth() - friendlyHQLocations[i].x - 1,
                        rc.getMapHeight() - friendlyHQLocations[i].y - 1);
                numHQ++;
            }
        }
    }

    // well infos starting
    public static void updateWells() {
        for (int resourceID = 1; resourceID <= 2; resourceID++) {
            for (int i = 0; i < NUM_WELLS; i++) {
                int startingBit = WELL_INFO_BIT + ((resourceID - 1) * 3 + i) * 12;
                closestWells[resourceID][i] = int2loc(readBits(startingBit, 12));
            }
        }
        needWellsUpdate = false;
    }

    public static void reportWells(WellInfo well) {
        MapLocation wellLocation = well.getMapLocation();
        int resourceID = well.getResourceType().resourceID;
        int closestHQID = Unit.getClosestID(wellLocation, friendlyHQLocations);
        int maxDis = friendlyHQLocations[closestHQID].distanceSquaredTo(wellLocation);

        int updateIndex = -1;
        for (int i = 0; i < NUM_WELLS; i++) {
            if (closestWells[resourceID][i] == null) {
                updateIndex = i;
                break;
            }
            if (closestWells[resourceID][i].equals(well.getMapLocation())){
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
            int startingBit = WELL_INFO_BIT + ((resourceID - 1) * 3 + updateIndex) * 12;
            writeBits(startingBit, 12, loc2int(wellLocation));
            needWellsUpdate = true;
        }
    }

    // spawn Q starts, works like a hashtable based on robot location int, collision goes to next pos
    // can be further optimized to use int directly
    public static int getSpawnFlag() throws GameActionException {
        int robotLoc = loc2int(rc.getLocation());
        for (int i = robotLoc; i < robotLoc + SPAWN_Q_LENGTH; i++) {
            int val = readBits(SPAWN_Q_BIT + 16 * (i % SPAWN_Q_LENGTH), 16);
            if ((val >> 4) == robotLoc) {
                writeBits(SPAWN_Q_BIT + 16 * (i % SPAWN_Q_LENGTH), 16, 0);
                commit_write();
                return val & 0xF;
            }
        }
        return 0;
    }

    public static boolean trySetSpawnFlag(MapLocation loc, int flag) {
        int locVal = loc2int(loc);
        for (int i = locVal; i < locVal + SPAWN_Q_LENGTH; i++) {
            int oldLoc = readBits(SPAWN_Q_BIT + 16 * (i % SPAWN_Q_LENGTH), 12);
            if (oldLoc == 0) {
                writeBits(SPAWN_Q_BIT + 16 * (i % SPAWN_Q_LENGTH), 16, (locVal << 4) + flag);
                return true;
            }
        }
        System.out.println("spawn Q full");
        return false;
    }

    // enemy report starting
    public static void reportEnemy(MapLocation location, int roundNumber) {
        if (getEnemyLoc() != null && roundNumber <= getEnemyRound()) { // ignore old report
            return;
        }
        writeBits(ENEMY_BIT, 12, loc2int(location));
        writeBits(ENEMY_BIT + 12, 12, roundNumber);
    }

    public static MapLocation getEnemyLoc() {
        return int2loc(readBits(ENEMY_BIT, 12));
    }

    public static int getEnemyRound() {
        return readBits(ENEMY_BIT + 12, 12);
    }

    // island storage
    public static void reportIsland(MapLocation loc, int index) {
        if (getIslandPos(index) != null)
            return;
    }

    // return 0 if not found
    public static int getNextFreeIslandIndex() {
        return 0;
    }

    public static MapLocation getIslandPos(int index) {
        return null;
    }

    // helper funcs
    private static int read_Segment_of_bits(int left, int right, int rv){
        int X = buffered_share_array[left/16];
        int x = 16 - left%16, y = 15 - right%16;
        int length = right - left + 1;
        return (rv << length) + ((X%(1<<x))>>y);
    }

    private static int readBits(int startingBitIndex, int length) {
        int endingBitIndex = startingBitIndex + length - 1;
        int rv = 0;
        int current = startingBitIndex;
        while (current <= endingBitIndex){
            int left = current;
            int right = Math.min(left/16*16+15, endingBitIndex);
            rv = read_Segment_of_bits (left, right, rv);
            current = right + 1;
        }
        return rv;
    }

    private static void writeBits(int startingBitIndex, int length, int value) {
        assert value < (1 << length);
        int current_ending = startingBitIndex + length - 1;
        int current_length = length;
        int current_value = value;
        while (current_length > 0){
            current_ending = startingBitIndex + current_length - 1;
            int len = Math.min(current_ending%16+1, current_length);
            int left = current_ending - len + 1;
            int original_value = read_Segment_of_bits (left, current_ending, 0);
            int new_value = current_value % (1 << len);
            current_value >>= len;
            if (new_value != original_value){
                is_array_changed[current_ending / 16] = true;
                is_array_changed_total = true;
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
