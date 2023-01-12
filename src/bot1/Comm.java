package bot1;

import battlecode.common.*;

/***
 *
 * Shared array
 * coord pos of 63 means unset (0 is used and max pos is 59)
 * 0-47: 4*2 6bits int specifying the coord of friendly HQs
 * 48-97: 4*2 6bits int specifying the coord of enemy HQs
 */
public class Comm extends RobotPlayer {
    private static final int ARRAY_LENGTH = 64;

    private static int[] buffered_share_array = new int[ARRAY_LENGTH];
    private static boolean[] is_array_changed = new boolean[ARRAY_LENGTH];

    public static void turn_starts() throws GameActionException {
        for (int i = 0; i < ARRAY_LENGTH; i++) {
            buffered_share_array[i] = rc.readSharedArray(i);
        }
    }

    public static void turn_ends() throws GameActionException {
        if (!rc.canWriteSharedArray(0, 0))
            return;

        for (int i = 0; i < ARRAY_LENGTH; i++) {
            if (is_array_changed[i]) {
                rc.writeSharedArray(i, buffered_share_array[i]);
                is_array_changed[i] = false;
            }
        }
    }

    // this should be called by the setup of each HQ
    public static void HQInit(MapLocation location, int HQID) throws GameActionException {
        // I am assuming HQIDs are < 8, one team using 0/2/4/6 and the other using 1/3/5/7
        assert(HQID < 8);
        HQID /= 2;
        MapLocation[] locations = getFriendlyHQLocations();
        if (locations[HQID] != null) {
            // this means that shared array hasn't been inited, so we perform init
            for (int i = 0; i < 4; i++) {
                writeBits(i * 12, 6, 63);
                writeBits(i * 12 + 6, 6, 63);
            }
        }
        writeBits(HQID * 12, 6, location.x);
        writeBits(HQID * 12 + 6, 6, location.y);
    }

    public static MapLocation[] getFriendlyHQLocations() {
        MapLocation[] locations = new MapLocation[4];
        for (int i = 0; i < 4; i++) {
            int x = readBits(12 * i, 6);
            int y = readBits(12 * i + 6, 6);
            if (x == 63 && y == 63) {
                locations[i] = null;
            } else {
                locations[i] = new MapLocation(x, y);
            }
        }
        return locations;
    }

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
