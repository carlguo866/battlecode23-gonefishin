package bot1;

import battlecode.common.*;

public class Carrier extends Unit {
    private static final int MAX_WEIGHT = 40; // tunable later

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
                    Comm.setSpawnQ(i, -1, -1, 0);
                    Comm.commit_write(); // write immediately instead of at turn ends in case we move out of range
                }
            }
            if (purpose != MINE_MN && purpose != SCOUT) {
                purpose = MINE_MN;
            }
//            assert purpose == MINE_MN || purpose == SCOUT; // others not implemented yet
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

        indicator += String.format("purpose %d,", purpose);

        if (state == MINING) {
            assert Comm.closestWells[resourceType.resourceID] != null;
            if (rc.canCollectResource(miningWellLoc, -1)) {
                rc.collectResource(miningWellLoc, -1);
                indicator += "collecting,";
                // moving pattern to allow others to join
                Direction dir = rc.getLocation().directionTo(miningWellLoc);
                // if can move on to the mine, do it
                if (dir != Direction.CENTER) {
                    if (rc.canMove(dir)) {
                        rc.move(dir);
                    } else { // or try to move in a circle
                        if (dir == Direction.NORTHEAST || dir == Direction.NORTHWEST || dir == Direction.SOUTHEAST || dir == Direction.SOUTHWEST) {
                            dir = dir.rotateRight();
                        } else {
                            dir = dir.rotateRight().rotateRight();
                        }
                        if (rc.canMove(dir)) rc.move(dir);
                    }
                }
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

    }
}
