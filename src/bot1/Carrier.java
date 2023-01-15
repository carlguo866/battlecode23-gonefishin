package bot1;

import battlecode.common.*;

public class Carrier extends Unit {
    private static final int MAX_WEIGHT = 40; // tunable later
    private static final int ATTACK_DIS = 9;

    public static final int AWAIT_CMD = 0;
    // for mining purposes, int value equal ResourceType
    public static final int MINE_AD = 1;
    public static final int MINE_MN = 2;
    public static final int MINE_EL = 3;
    public static final int SCOUT = 4;

    public static final int MINING = 100;
    public static final int DROPPING_RESOURCE = 101;

    public static final int RUNAWAY_AND_REPORT = 110;
    public static final int RUNAWAY = 111;

    public static final int SCOUTING = 201;
    public static final int REPORTING_INFO = 201;

    public static int purpose = AWAIT_CMD;
    public static int state = 0;
    public static ResourceType resourceType;
    public static MapLocation miningWellLoc, miningHQLoc;

    public static MapLocation lastEnemyLoc = null;
    public static int lastEnemyRound = 0;

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

        RobotInfo closestEnemy = null;
        int dis = Integer.MAX_VALUE;
        for (RobotInfo robot : rc.senseNearbyRobots(-1, oppTeam)) {
            if (robot.type == RobotType.LAUNCHER) {
                int newDis = robot.location.distanceSquaredTo(rc.getLocation());
                if (closestEnemy == null || newDis < dis) {
                    newDis = dis;
                    closestEnemy = robot;
                }
            }
        }
        if (closestEnemy != null) {
            lastEnemyLoc = closestEnemy.location;
            lastEnemyRound = rc.getRoundNum();
            if (rc.canAttack(closestEnemy.location)) {
                indicator += "attack,";
                rc.attack(closestEnemy.location);
            } else { // no resource or too far away
                if (rc.getWeight() >= 20) { // ow not worth it to get close to attack
                    Direction forwardDir = rc.getLocation().directionTo(closestEnemy.location);
                    Direction[] dirs = {forwardDir.rotateRight().rotateRight(), forwardDir.rotateLeft().rotateLeft(),
                            forwardDir.rotateLeft(), forwardDir.rotateRight(), forwardDir};
                    for (Direction dir : dirs) {
                        if (rc.getLocation().add(dir).distanceSquaredTo(closestEnemy.location) <= ATTACK_DIS
                                && rc.canMove(dir)) {
                            rc.move(dir);
                            assert rc.canAttack(closestEnemy.location);
                            indicator += "goattack,";
                            if (rc.canAttack(closestEnemy.location)) {
                                rc.attack(closestEnemy.location);
                            }
                        }
                    }
                }
                // If I can move and still have stuff, throw it all away, just don't hit a teammate
                if (rc.isMovementReady() && rc.getWeight() > 0) {
                    for (int i = 1; i <= 20; i++) {
                        MapLocation loc = new MapLocation(rc.getLocation().x + BFS25[i][0],
                                rc.getLocation().y + BFS25[i][1]);
                        if (!rc.onTheMap(loc)) continue;
                        RobotInfo robot = rc.senseRobotAtLocation(loc);
                        if (robot == null || robot.team != myTeam || robot.type == RobotType.HEADQUARTERS) {
                            if (rc.canAttack(loc)) {
                                rc.attack(loc);
                                indicator += String.format("throw %s,", loc);
                            }
                        }
                    }
                }
            }
            state = RUNAWAY_AND_REPORT;
        }

        if (state == RUNAWAY_AND_REPORT) {
            indicator += "goreport,";
            int closestHQID = getClosestID(Comm.friendlyHQLocations);
            if (rc.canWriteSharedArray(0, 0)) {
                Comm.reportEnemy(lastEnemyLoc, lastEnemyRound);
                Comm.commit_write();
                state = RUNAWAY;
            } else {
                moveToward(Comm.friendlyHQLocations[closestHQID]);
                if (rc.canWriteSharedArray(0, 0)) {
                    Comm.reportEnemy(lastEnemyLoc, lastEnemyRound);
                    Comm.commit_write();
                    state = RUNAWAY;
                } else {
                    moveToward(Comm.friendlyHQLocations[closestHQID]);
                    if (rc.canWriteSharedArray(0, 0)) {
                        Comm.reportEnemy(lastEnemyLoc, lastEnemyRound);
                        Comm.commit_write();
                        state = RUNAWAY;
                    }
                }
            }
        }
        if (state == RUNAWAY) {
            indicator += "run,";
            if (closestEnemy == null && rc.getRoundNum() - lastEnemyRound >= 3) {
                state = MINING;
            } else {
                Direction backDir = rc.getLocation().directionTo(lastEnemyLoc).opposite();
                Direction[] dirs = {backDir, backDir.rotateLeft(), backDir.rotateRight(),
                        backDir.rotateLeft().rotateLeft(), backDir.rotateRight().rotateRight()};
                boolean hasMoved = true;
                while (hasMoved && rc.isMovementReady()) {
                    hasMoved = false;
                    for (Direction dir : dirs) {
                        if (rc.canMove(dir)) {
                            rc.move(dir);
                            hasMoved = true;
                            break;
                        }
                    }
                }
            }
        }

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
            } else if (rc.getWeight() >= MAX_WEIGHT) {
                state = DROPPING_RESOURCE;
            } else {
                moveToward(miningWellLoc);
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
