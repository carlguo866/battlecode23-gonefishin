package bot1;

import battlecode.common.*;

/***
 *
 * Shared array
 * coord pos of 63 means unset (0 is used and max pos is 59)
 * 0-47: 4*2 6bits int specifying the coord of friendly HQs
 * 48-95: 4*2 6bits int specifying the coord of enemy HQs
 *
 * wells info starting bit 96 of 3 types
 * each type of length 14
 * 2 bits: closest HQ ID
 * 12 bits: well location
 */
public class Comm extends RobotPlayer {
    private static final int ARRAY_LENGTH = 64;

    private static int[] buffered_share_array = new int[ARRAY_LENGTH];
    private static boolean[] is_array_changed = new boolean[ARRAY_LENGTH];

    public static int numHQ = 0;
    public static MapLocation[] friendlyHQLocations = new MapLocation[4];
    public static int[] closestHQIDToWells = new int[4];
    public static MapLocation[] closestWells = new MapLocation[4];

    public static void turn_starts() throws GameActionException {
        boolean changed = false;
        for (int i = 0; i < ARRAY_LENGTH; i++) {
            if (rc.readSharedArray(i) != buffered_share_array[i]) {
                changed = true;
                buffered_share_array[i] = rc.readSharedArray(i);
            }
        }
        if (changed) {
            updateFriendlyHQLocations();
            updateWells();
        }
    }

    // IMPORTANT: always ensure that any write op is performed when writable
    public static void turn_ends() throws GameActionException {
        for (int i = 0; i < ARRAY_LENGTH; i++) {
            if (is_array_changed[i]) {
                rc.writeSharedArray(i, buffered_share_array[i]);
                is_array_changed[i] = false;
            }
        }
    }

    public static void global_init() {
        // HQ
        for (int i = 0; i < 4; i++) {
            writeBits(i * 12, 12, 4095);
        }
        // wells
        writeBits(WELL_INFO_BIT + 2, 12, 4095);
        writeBits(WELL_INFO_BIT + 16, 12, 4095);
        writeBits(WELL_INFO_BIT + 30, 12, 4095);
    }

    // HQ locations starting at bit 0

    // this should be called by the setup of each HQ
    public static void HQInit(MapLocation location, int HQID) throws GameActionException {
        // I am assuming HQIDs are < 8, one team using 0/2/4/6 and the other using 1/3/5/7
        assert(HQID < 8);
        HQID /= 2;
        updateFriendlyHQLocations();
        if (friendlyHQLocations[HQID] != null) {
            // this means that shared array hasn't been inited, so we perform init
            global_init();
        }
        writeBits(HQID * 12, 6, location.x);
        writeBits(HQID * 12 + 6, 6, location.y);
        updateFriendlyHQLocations();
    }

    private static void updateFriendlyHQLocations() {
        numHQ = 0;
        for (int i = 0; i < 4; i++) {
            int x = readBits(12 * i, 6);
            int y = readBits(12 * i + 6, 6);
            if (x == 63 && y == 63) {
                friendlyHQLocations[i] = null;
            } else {
                friendlyHQLocations[i] = new MapLocation(x, y);
                numHQ++;
            }
        }
    }

    // well infos starting
    private static final int WELL_INFO_BIT = 96;

    public static void updateWells() {
        for (int resourceID = 1; resourceID <= 3; resourceID++) {
            int startingBit = WELL_INFO_BIT + (resourceID - 1) * 14;
            closestHQIDToWells[resourceID] = readBits(startingBit, 2);
            int x = readBits(startingBit + 2, 6);
            int y = readBits(startingBit + 8, 6);
            if (x == 63 && y == 63) {
                closestWells[resourceID] = null;
            } else {
                closestWells[resourceID] = new MapLocation(x, y);
            }
        }
    }

    public static void reportWells(WellInfo well) {
        MapLocation wellLocation = well.getMapLocation();
        ResourceType resourceType = well.getResourceType();

        int closestHQID = 0, minDis = Integer.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            if (friendlyHQLocations[i] != null && wellLocation.distanceSquaredTo(friendlyHQLocations[i]) < minDis) {
                minDis = wellLocation.distanceSquaredTo(friendlyHQLocations[i]);
                closestHQID = i;
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
            writeBits(startingBit + 2, 6, wellLocation.x);
            writeBits(startingBit + 8, 6, wellLocation.y);
            updateWells();
        }
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

    private static void writeBits(int startingBitIndex, int length, int value) {
        assert value < (1 << length);
        for (int i = startingBitIndex; i < startingBitIndex + length; i++) {
            int original_bit = buffered_share_array[i / 16] & (1 << (15 - i % 16)); //bit 0 = buf[0] & (1 << 15)
            original_bit = original_bit > 0? 1 : 0;
            int new_bit = value & (1 << (length - 1 - i + startingBitIndex)); // start: 6, length: 6, i: 7, shift: 4
            new_bit = new_bit > 0? 1 : 0;
            if (original_bit != new_bit) {
                is_array_changed[i / 16] = true;
                buffered_share_array[i / 16] ^= 1 << (15 - i % 16);
            }
        }
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
