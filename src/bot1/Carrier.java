package bot1;

import battlecode.common.*;

import java.util.Map;
import java.util.Random;

public class Carrier extends Unit {
    // purposes
    public static final int MINE_MN = 1; // mining is default
    public static final int MINE_AD = 2;
    public static final int SCOUT_SYMMETRY = 3;

    public static final int MINING = 100;
    public static final int DROPPING_RESOURCE = 101;

    public static final int REPORT_AND_RUNAWAY = 110;
    public static final int RUNAWAY = 111;

    public static final int SCOUTING = 201;
    public static final int REPORTING_INFO = 202;

    public static final int ANCHORING = 301;

    public static int purpose = 0;
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
    static MapLocation startHQLoc;
    static int scoutStartRound;
    static MapLocation scoutTarget = null;
    static double scoutAngle = 0;
    static WellInfo[] wellsToReport = new WellInfo[300];
    static int wellReportCnt = 0, wellSeenCnt = 0;

    static void run () throws GameActionException {
        if (turnCount == 0) {
            startHQID = getClosestID(Comm.friendlyHQLocations);
            startHQLoc = Comm.friendlyHQLocations[startHQID];

            rng = new Random(rc.getID());
            purpose = Comm.getSpawnFlag();
            if (purpose == 0) {
                purpose = MINE_MN;
                System.out.println("Carrier spawn Q no flag");
            }
            resumeWork();
        }

        indicator += String.format("purpose%dstate%d,", purpose, state);
        checkAnchor();
        if (state == ANCHORING) {
            anchor();
            return; // in state anchoring, ignore everything else (We should have controlled the map anyways)
        }

        if (state == SCOUTING) {
            MapRecorder.record(4000);
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
            mine();
        }

        if (state == DROPPING_RESOURCE) {
            dropResource();
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

    private static void scoutSense() throws GameActionException {
        for (WellInfo well : rc.senseNearbyWells()) {
            boolean seenWell = false;
            for (int i = 0; i < wellSeenCnt; i++) {
                // FIXME inefficient
                if (wellsToReport[i].getMapLocation() == well.getMapLocation()) {
                    seenWell = true;
                    break;
                }
            }
            for (int i = 0; i < Comm.NUM_WELLS; i++) {
                if (Comm.closestWells[well.getResourceType().resourceID][i] == null)
                    break;
                if (Comm.closestWells[well.getResourceType().resourceID][i].equals(well.getMapLocation())) {
                    seenWell = true;
                    break;
                }
            }
            if (!seenWell) {
                wellsToReport[wellSeenCnt++] = well;
            }
        }
    }
    private static boolean setScoutTarget() {
        for (; Math.abs(scoutAngle) <= Math.PI * 8;) {
            // scouting along the line r=theta
            scoutTarget = startHQLoc.translate(
                    (int)(Math.cos(scoutAngle) * scoutAngle),
                    (int)(Math.sin(scoutAngle) * scoutAngle));
            if (rc.onTheMap(scoutTarget) && (MapRecorder.vals[scoutTarget.x][scoutTarget.y] & MapRecorder.SEEN_BIT) == 0) {
                return true;
            }
            scoutAngle += Math.PI / 6 * (rc.getID() % 2 == 0? 1 : -1);
        }
        return false;
    }

    private static void scoutMove() throws GameActionException {
        if (purpose == SCOUT_SYMMETRY) {
            if (Comm.isSymmetryConfirmed) {
                state = REPORTING_INFO;
            } else {
                moveToward(new MapLocation(mapWidth / 2, mapHeight / 2));
            }
        } else { // mine scouting
            if (Comm.closestWells[miningResourceType.resourceID][0] != null ||
                wellSeenCnt > wellReportCnt) {
                state = REPORTING_INFO;
            } else {
                if (scoutTarget == null || (MapRecorder.vals[scoutTarget.x][scoutTarget.y] & MapRecorder.SEEN_BIT) != 0)  {
                    if (!setScoutTarget()) {
                        System.out.println("nothing to scout");
                        state = 0;
                        return;
                    }
                }
                indicator += String.format("target %s,angle %.1fPI", scoutTarget, scoutAngle / Math.PI);
                moveToward(scoutTarget);
            }
        }
    }

    private static boolean isNeedReport() {
        return lastEnemyRound > Comm.getEnemyRound() || wellReportCnt < wellSeenCnt || Comm.needSymmetryReport;
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
                Comm.reportSym();
                Comm.commit_write();
            } else { // still can't write, wait for more moves and don't transition
                return;
            }
        }
        // state transitions
        indicator += "notneedreport,";
        resumeWork();
    }

    private static void runaway() throws GameActionException {
        if (state == RUNAWAY) {
            indicator += "run,";
            if (closestEnemy == null && rc.getRoundNum() - lastEnemyRound >= 3) {
                resumeWork();
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

    private static void tryFindMine() {
        if (Comm.closestWells[miningResourceType.resourceID][0] == null) {
            state = SCOUTING;
            scoutStartRound = rc.getRoundNum();
        } else {
            int miningWellIndex = getClosestID(Comm.closestWells[miningResourceType.resourceID]);
            miningWellLoc = Comm.closestWells[miningResourceType.resourceID][miningWellIndex];
            state = MINING;
        }
    }

    private static void mine() throws GameActionException {
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

    private static void dropResource() throws GameActionException {
        int ad = rc.getResourceAmount(ResourceType.ADAMANTIUM);
        int mn = rc.getResourceAmount(ResourceType.MANA);
        if (rc.getWeight() == 0) {
            state = MINING;
            tryFindMine();
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

    // after reporting info or running away
    private static void resumeWork() {
        if (purpose == MINE_AD || purpose == MINE_MN) {
            miningResourceType = purpose == MINE_AD? ResourceType.ADAMANTIUM : ResourceType.MANA;
            tryFindMine();
        } else { // symmetry scouting
            if (Comm.isSymmetryConfirmed) {
                System.out.println("sym confirmed as sym scout, go mine");
                purpose = (rng.nextInt(2) == 0? MINE_AD : MINE_MN);
                miningResourceType = purpose == MINE_AD? ResourceType.ADAMANTIUM : ResourceType.MANA;
                tryFindMine();
            } else {
                state = SCOUTING;
                scoutStartRound = rc.getRoundNum();
            }
        }
    }
}
