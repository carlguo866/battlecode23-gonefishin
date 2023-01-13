package bot1;

import battlecode.common.*;

public class Carrier extends Unit {
    private static final int MAX_WEIGHT = 40; // tunable later
    private static String indicator;

    public static final int AWAIT_CMD = 0;
    // for mining purposes, int value equal ResourceType
    public static final int MINE_AD = 1;
    public static final int MINE_MN = 2;
    public static final int MINE_EL = 3;
    public static final int SCOUT = 4;

    public static final int MINING = 100;
    public static final int DROPPING_RESOURCE = 101;

    public static final int SCOUTING = 201;
    public static final int REPORTING_INFO = 201;

    public static int purpose = AWAIT_CMD;
    public static int state = 0;
    public static ResourceType resourceType;
    public static MapLocation miningWellLoc, miningHQLoc;

    static void run () throws GameActionException {
        if (turnCount == 0) {
            for (int i = 0; i < Comm.SPAWN_Q_LENGTH; i++) {
                MapLocation location = Comm.getSpawnQLoc(i);
                if (location != null && location.compareTo(rc.getLocation()) == 0) {
                    purpose = Comm.getSpawnQFlag(i);
                    Comm.setSpawnQ(i, 63, 63, 0);
                }
            }
            assert purpose == MINE_MN || purpose == SCOUT; // others not implemented yet
            if (purpose == MINE_MN) {
                state = MINING;
                resourceType = ResourceType.values()[purpose];
                assert resourceType == ResourceType.MANA;
                miningHQLoc = Comm.friendlyHQLocations[Comm.closestHQIDToWells[purpose]];
                miningWellLoc = Comm.closestWells[purpose];
                assert miningWellLoc != null;
            } else {
                state = SCOUTING;
            }
        }

        indicator = String.format("purpose %d,", purpose);

        if (state == MINING) {
            assert Comm.closestWells[resourceType.resourceID] != null;
            if (rc.canCollectResource(miningWellLoc, -1)) {
                rc.collectResource(miningWellLoc, -1);
                indicator += "collecting,";
            } else if (rc.getResourceAmount(resourceType) >= MAX_WEIGHT) {
                state = DROPPING_RESOURCE;
            } else {
                moveToward(miningWellLoc);
                indicator += "going to mine,";
            }
        } else if (state == DROPPING_RESOURCE) {
            int amount = rc.getResourceAmount(resourceType);
            if (amount == 0) {
                state = MINING;
            } else if (rc.canTransferResource(miningHQLoc, resourceType, amount)) {
                rc.transferResource(miningHQLoc, resourceType, amount);
                indicator += "dropping";
            } else {
                moveToward(miningHQLoc);
                indicator += "going to drop";
            }

        }

        rc.setIndicatorString(indicator);
    }
}
