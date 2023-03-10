package submit10;

import battlecode.common.*;

import java.util.Random;

public class Carrier extends Unit {
    // purposes
    public static final int GO_MINE = 0; // mining is default
    public static final int SCOUT_NE = 4;
    public static final int SCOUT_NW = 5;
    public static final int SCOUT_SE = 6;
    public static final int SCOUT_SW = 7;

    public static final int MINING = 100;
    public static final int DROPPING_RESOURCE = 101;

    public static final int REPORT_AND_RUNAWAY = 110;
    public static final int RUNAWAY = 111;

    public static final int SCOUTING = 201;
    public static final int REPORTING_INFO = 202;

    public static final int ANCHORING = 301;

    public static int purpose = GO_MINE;
    public static int state = 0;
    public static ResourceType miningResourceType;
    public static MapLocation miningWellLoc;
    public static MapLocation miningHQLoc;

    public static MapLocation lastEnemyLoc = null;
    public static int lastEnemyRound = 0;
    public static RobotInfo closestEnemy = null;

    private static Random rng;

    // scouting vars
    static int startHQID;
    static Direction scoutDir;
    static WellInfo[] wellsToReport = new WellInfo[300];
    static int wellReportCnt = 0, wellSeenCnt = 0;

    static void run () throws GameActionException {
        if (turnCount == 0) {
            rng = new Random(rc.getID());
            purpose = Comm.getSpawnFlag();
            if (purpose == GO_MINE) {
                state = MINING;
                findMineTarget();
            } else {
                state = SCOUTING;
                if (purpose == SCOUT_NE) scoutDir = Direction.NORTHEAST;
                else if (purpose == SCOUT_SE) scoutDir = Direction.SOUTHEAST;
                else if (purpose == SCOUT_SW) scoutDir = Direction.SOUTHWEST;
                else if (purpose == SCOUT_NW) scoutDir = Direction.NORTHWEST;
            }
        }

        indicator += String.format("state%d,", state);
        checkAnchor();
        if (state == ANCHORING) {
            anchor();
            return; // in state anchoring, ignore everything else (We should have controlled the map anyways)
        }

        if (state == SCOUTING) {
            scoutSense();
        }
        senseEnemy();

        if (state == SCOUTING) {
            scoutMove();
        }
        if (state == SCOUTING) {
            scoutSense();
            scoutMove();
        }

        if (state == REPORT_AND_RUNAWAY || state == REPORTING_INFO) {
            report();
        }

        if (state == RUNAWAY) {
            runaway();
        }

        if (state == MINING) {
            if (shouldStopMining()) {
                int hqid = getClosestID(Comm.friendlyHQLocations);
                miningHQLoc = Comm.friendlyHQLocations[hqid];
                state = DROPPING_RESOURCE;
            } else if (rc.canCollectResource(miningWellLoc, -1)) {
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
            } else { // moving toward mine
                // switch mine randomly if original too congested
                if (rc.senseNearbyRobots(miningWellLoc, 3, myTeam).length >= 7) {
                    for (int i = 0; i < 10; i++) {
                        MapLocation loc = Comm.closestWells[miningResourceType.resourceID][Constants.rng.nextInt(Comm.NUM_WELLS)];
                        if (loc != null && !loc.equals(miningWellLoc)) {
                            miningWellLoc = loc;
                            break;
                        }
                    }
                }
                moveToward(miningWellLoc);
                if (rc.senseNearbyRobots(miningWellLoc, 3, myTeam).length >= 7) {
                    for (int i = 0; i < 10; i++) {
                        MapLocation loc = Comm.closestWells[miningResourceType.resourceID][Constants.rng.nextInt(Comm.NUM_WELLS)];
                        if (loc != null && !loc.equals(miningWellLoc)) {
                            miningWellLoc = loc;
                            break;
                        }
                    }
                }
                moveToward(miningWellLoc);
                indicator += "going to mine,";
            }
        }

        if (state == DROPPING_RESOURCE) {
            int ad = rc.getResourceAmount(ResourceType.ADAMANTIUM);
            int mn = rc.getResourceAmount(ResourceType.MANA);
            if (rc.getWeight() == 0) {
                state = MINING;
                findMineTarget();
            } else if (rc.canTransferResource(miningHQLoc, ResourceType.ADAMANTIUM, ad)) {
                rc.transferResource(miningHQLoc, ResourceType.ADAMANTIUM, ad);
                indicator += "dropping";
            } else if (rc.canTransferResource(miningHQLoc, ResourceType.MANA, mn)) {
                rc.transferResource(miningHQLoc, ResourceType.MANA, mn);
                indicator += "dropping";
            } else {
                moveToward(miningHQLoc);
                indicator += "going to drop";
            }
        }

    }

    private static boolean shouldStopMining() {
        if (rc.getWeight() >= GameConstants.CARRIER_CAPACITY)
            return true;
        // early game optim, get launcher out asap
        if (rc.getRoundNum() <= 100 && (rc.getResourceAmount(ResourceType.ADAMANTIUM) >= 25 || rc.getResourceAmount(ResourceType.MANA) >= 30))
            return true;
        return false;
    }

    // sense and attack nearby enemies
    private static void senseEnemy() throws GameActionException {
        closestEnemy = null;
        int dis = Integer.MAX_VALUE;
        for (RobotInfo robot : rc.senseNearbyRobots(-1, oppTeam)) {
            if (robot.type == RobotType.LAUNCHER) {
                int newDis = robot.location.distanceSquaredTo(rc.getLocation());
                if (closestEnemy == null || newDis < dis) {
                    dis = newDis;
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
                        if (rc.getLocation().add(dir).distanceSquaredTo(closestEnemy.location) <= Constants.CARRIER_ATTACK_DIS
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
                        if (rc.onTheMap(loc) && rc.canSenseLocation(loc)) {
                            RobotInfo robot = rc.senseRobotAtLocation(loc);
                            if (robot == null || robot.team != myTeam || robot.type == RobotType.HEADQUARTERS) {
                                if (rc.canAttack(loc)) {
                                    rc.attack(loc);
                                    indicator += String.format("throw %s,", loc);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            state = REPORT_AND_RUNAWAY;
        }
    }

    private static void checkAnchor() throws GameActionException {
        for (int i = 0; i < 4; i++) {
            MapLocation loc = Comm.friendlyHQLocations[i];
            if (loc != null && rc.getLocation().isAdjacentTo(loc)) {
                if (rc.canTakeAnchor(loc, Anchor.STANDARD)) {
                    rc.takeAnchor(loc, Anchor.STANDARD);
                    state = ANCHORING;
                }
            }
        }
    }

    private static void anchor() throws GameActionException {
        indicator += "anchoring";
        int[] islands = rc.senseNearbyIslands();
        if (rc.canPlaceAnchor()) {
            rc.placeAnchor();
            state = MINING;
            return;
        }
        MapLocation targetLoc = null;
        int dis = Integer.MAX_VALUE;
        for (int island : islands) {
            if (rc.senseTeamOccupyingIsland(island) != Team.NEUTRAL) {
                // we can't place here
                continue;
            }
            MapLocation locs[] = rc.senseNearbyIslandLocations(island);
            for (MapLocation loc : locs) {
                if (rc.getLocation().distanceSquaredTo(loc) < dis) {
                    targetLoc = loc;
                    dis = rc.getLocation().distanceSquaredTo(loc);
                }
            }
        }
        if (targetLoc == null) {
            randomMove();
        } else {
            indicator += targetLoc;
            moveToward(targetLoc);
        }
    }

    private static void scoutSense() {
        for (WellInfo well : rc.senseNearbyWells()) {
            boolean seenWell = false;
            for (int i = 0; i < wellSeenCnt; i++) {
                // FIXME inefficient
                if (wellsToReport[i].getMapLocation() == well.getMapLocation()) {
                    seenWell = true;
                    break;
                }
            }
            if (!seenWell) {
                wellsToReport[wellSeenCnt++] = well;
            }
        }
    }
    private static void scoutMove() throws GameActionException {
        if (Math.sqrt(getClosestDis(Comm.enemyHQLocations)) - 3 <= Math.sqrt(getClosestDis(Comm.friendlyHQLocations))) {
            indicator += "close2E,endscout,";
            state = REPORTING_INFO;
            return;
        }
        int closestHQID = getClosestID(Comm.friendlyHQLocations);
        if (closestHQID != startHQID
                && Comm.friendlyHQLocations[closestHQID].distanceSquaredTo(rc.getLocation()) <= 64) {
            indicator += "close2HQ,endscout,";
            state = REPORTING_INFO;
            return;
        }
        int dx = scoutDir.dx;
        int dy = scoutDir.dy;
        for (RobotInfo robot : rc.senseNearbyRobots(-1, myTeam)) {
            Direction dir = rc.getLocation().directionTo(robot.location);
            if (robot.type == RobotType.CARRIER && (dir == scoutDir || dir == scoutDir.rotateLeft() || dir == scoutDir.rotateRight())) {
                indicator += String.format("scout%s", scoutDir);
                indicator += String.format("dir%sendscout,", dir);
                state = REPORTING_INFO;
                return;
            }
        }
        // no need to go too close to edge in one direction
        if ((dx > 0 && rc.getMapWidth() - rc.getLocation().x <= 4)
                || (dx < 0 && rc.getLocation().x <= 3)) {
            dx = 0;
        }
        if ((dy > 0 && rc.getMapHeight() - rc.getLocation().y <= 4)
                || (dy < 0 && rc.getLocation().y <= 3)) {
            dy = 0;
        }
        scoutDir = Dxy2dir(dx, dy);
        if (scoutDir == Direction.CENTER) {
            indicator += "gotcorner,endscout";
            state = REPORTING_INFO;
            return;
        }
        if (rc.getRoundNum() >= 12) {
            indicator += "timereached,endscout";
            state = REPORTING_INFO;
            return;
        }

        tryMoveDir(scoutDir);
    }

    private static boolean isNeedReport() {
        return lastEnemyRound > Comm.getEnemyRound() || wellReportCnt < wellSeenCnt;
    }

    private static void report() throws GameActionException {
        if (isNeedReport()) {
            indicator += "goreport,";
            int closestHQID = getClosestID(Comm.friendlyHQLocations);
            if (!rc.canWriteSharedArray(0, 0) && rc.isMovementReady()) {
                moveToward(Comm.friendlyHQLocations[closestHQID]);
            }
            if (!rc.canWriteSharedArray(0, 0) && rc.isMovementReady()) {
                moveToward(Comm.friendlyHQLocations[closestHQID]);
            }
            if (rc.canWriteSharedArray(0, 0)) {
                // do report
                if (lastEnemyLoc != null) {
                    Comm.reportEnemy(lastEnemyLoc, lastEnemyRound);
                }
                while(wellReportCnt < wellSeenCnt)
                    Comm.reportWells(wellsToReport[wellReportCnt++]);
                Comm.commit_write();
            } else { // still can't write, wait for more moves and don't transition
                return;
            }
        }
        // state transitions
        indicator += "notneedreport,";
        if (state == REPORT_AND_RUNAWAY) {
            state = RUNAWAY;
        } else { // state == REPORT_INFO
            findMineTarget();
            state = MINING;
        }
    }

    private static void runaway() throws GameActionException {
        if (state == RUNAWAY) {
            indicator += "run,";
            if (closestEnemy == null && rc.getRoundNum() - lastEnemyRound >= 3) {
                findMineTarget();
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
    }

    private static void findMineTarget() {
        if (Comm.closestWells[ResourceType.MANA.resourceID][0] == null) {
            miningResourceType = ResourceType.ADAMANTIUM;
        } else {
            // Mine AD with 1/3 prob if both mines available
            miningResourceType = rng.nextInt(3) == 0? ResourceType.ADAMANTIUM : ResourceType.MANA;
        }
        int miningWellIndex = getClosestID(Comm.closestWells[miningResourceType.resourceID]);
        miningWellLoc = Comm.closestWells[miningResourceType.resourceID][miningWellIndex];
    }
}
