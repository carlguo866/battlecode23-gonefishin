package bot1;

import battlecode.common.*;

import java.util.Random;
import bot1.util.FastIterableLocSet;

public class Carrier extends Unit {
    // purposes
    public static final int MINE_MN = 1; // mining is default
    public static final int MINE_AD = 2;
    public static final int SCOUT_SYMMETRY = 3;

    public static final int MINING = 10;
    public static final int DROPPING_RESOURCE = 11;

    public static final int REPORT_AND_RUNAWAY = 20;
    public static final int RUNAWAY = 21;

    public static final int SCOUTING = 30;
    public static final int REPORTING_INFO = 31;

    public static final int ANCHORING = 40;

    public static int purpose = 0;
    public static int state = 0;
    public static ResourceType miningResourceType;
    public static MapLocation miningWellLoc;
    public static MapLocation miningHQLoc;

    public static MapLocation lastEnemyLoc = null;
    public static int lastEnemyRound = 0;
    public static RobotInfo closestEnemy = null;

    public static FastIterableLocSet congestedMines = new FastIterableLocSet(20);
    private static Random rng;

    // scouting vars
    static int startHQID;
    static MapLocation startHQLoc;
    static int scoutStartRound;
    static MapLocation scoutTarget = null;
    static MapLocation scoutCenter = null;
    static double scoutAngle = 0;
    static WellInfo[] wellsToReport = new WellInfo[300];
    static int wellReportCnt = 0, wellSeenCnt = 0;

    static void run () throws GameActionException {
        if (turnCount == 0) {
            startHQID = getClosestID(Comm.friendlyHQLocations);
            startHQLoc = Comm.friendlyHQLocations[startHQID];
            scoutCenter = startHQLoc;

            rng = new Random(rc.getID());
            purpose = Comm.getSpawnFlag();
            if (purpose == 0) {
                purpose = MINE_MN;
                System.out.println("Carrier spawn Q no flag");
            }
            resumeWork();
        }

        indicator += String.format("P%dS%d,", purpose, state);
        checkAnchor();
        if (state == ANCHORING) {
            anchor();
            return; // in state anchoring, ignore everything else (We should have controlled the map anyways)
        }

        if (state == SCOUTING) {
            if (purpose == SCOUT_SYMMETRY) {
                MapRecorder.recordSym(4000);
            } else {
                MapRecorder.recordFast(4000);
            }
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
            handleReportState();
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

        // just try sense and report no matter what the role is
        scoutSense();
        if (isNeedReport() && rc.canWriteSharedArray(0, 0)) {
            report();
        }
        if (purpose != SCOUT_SYMMETRY) {
            MapRecorder.recordFast(500);
        }
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

    private static MapLocation lastSenseLocation = new MapLocation(-1, -1);
    private static void scoutSense() throws GameActionException {
        if (rc.getLocation().equals(lastSenseLocation))
            return;
        lastSenseLocation = rc.getLocation();
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
        boolean success = false;
        for (; Math.abs(scoutAngle) <= Math.PI * 18; scoutAngle += Math.PI / 6 * (rc.getID() % 2 == 0? 1 : -1)) {
            // scouting along the line r=theta
            scoutTarget = scoutCenter.translate(
                    (int)(Math.cos(scoutAngle) * scoutAngle),
                    (int)(Math.sin(scoutAngle) * scoutAngle));
            if (rc.onTheMap(scoutTarget) && (MapRecorder.vals[scoutTarget.x][scoutTarget.y] & MapRecorder.SEEN_BIT) == 0) {
                success = true;
                break;
            }
        }
        if (!success) scoutTarget = null;
        return success;
    }

    private static void scoutMove() throws GameActionException {
        if (purpose == SCOUT_SYMMETRY && rc.getRoundNum() - scoutStartRound >= 30) {
            // if I have been scouting for 30 turns consecutively and everything the same, it really doesn't matter
            System.out.println("sym scout too long, eliminate sym arbitrarily");
            for (int sym = 2; sym >= 0 && !Comm.isSymmetryConfirmed; sym--) {
                Comm.eliminateSym(sym);
            }
        }

        if (purpose == SCOUT_SYMMETRY && Comm.isSymmetryConfirmed) {
            state = REPORTING_INFO;
            return;
        }

        if ((purpose == MINE_AD || purpose == MINE_MN) && !needScoutWell()) {
            state = REPORTING_INFO;
            return;
        }

        if (scoutTarget == null || (MapRecorder.vals[scoutTarget.x][scoutTarget.y] & MapRecorder.SEEN_BIT) != 0)  {
            if (!setScoutTarget()) {
                System.out.println("nothing to scout");
                state = 0;
                return;
            }
        }
        indicator += String.format("T%s,A%.1fPI,v%d", scoutTarget, scoutAngle / Math.PI, MapRecorder.vals[scoutTarget.x][scoutTarget.y]);
        moveToward(scoutTarget);
    }

    private static boolean isNeedReport() {
        return lastEnemyRound > Comm.getEnemyRound() || wellReportCnt < wellSeenCnt || Comm.needSymmetryReport;
    }

    private static void report() throws GameActionException {
        // do report
        if (lastEnemyLoc != null) {
            Comm.reportEnemy(lastEnemyLoc, lastEnemyRound);
        }
        while(wellReportCnt < wellSeenCnt)
            Comm.reportWells(wellsToReport[wellReportCnt++]);
        Comm.reportSym();
        Comm.commit_write();
    }

    private static void handleReportState() throws GameActionException {
        if (isNeedReport()) {
            indicator += "2RP,";
            int closestHQID = getClosestID(Comm.friendlyHQLocations);
            if (!rc.canWriteSharedArray(0, 0) && rc.isMovementReady()) {
                moveToward(Comm.friendlyHQLocations[closestHQID]);
            }
            if (!rc.canWriteSharedArray(0, 0) && rc.isMovementReady()) {
                moveToward(Comm.friendlyHQLocations[closestHQID]);
            }
            if (rc.canWriteSharedArray(0, 0)) {
                report();
            } else { // still can't write, wait for more moves and don't transition
                return;
            }
        }
        // state transitions
        indicator += "NORP,";
        resumeWork();
    }

    private static void runaway() throws GameActionException {
        if (state == RUNAWAY) {
            indicator += "RN,";
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

    private static void mine() throws GameActionException {
        if (rc.getWeight() >= GameConstants.CARRIER_CAPACITY) {
            int hqid = getClosestID(Comm.friendlyHQLocations);
            miningHQLoc = Comm.friendlyHQLocations[hqid];
            state = DROPPING_RESOURCE;
        } else if (rc.getLocation().isAdjacentTo(miningWellLoc)) {
            // may be able to collect twice per turn
            if (rc.canCollectResource(miningWellLoc, -1)) {
                rc.collectResource(miningWellLoc, -1);
            }
            if (rc.canCollectResource(miningWellLoc, -1)) {
                rc.collectResource(miningWellLoc, -1);
            }
            indicator += "mine,";
            // move in pattern to allow others to join
            Direction dirToMine = rc.getLocation().directionTo(miningWellLoc);
            if (dirToMine == Direction.CENTER && rc.isMovementReady()) {
                int hqid = getClosestID(Comm.friendlyHQLocations);
                miningHQLoc = Comm.friendlyHQLocations[hqid];
                // robot in the center moves away to the closest minable square to home HQ
                FastIterableLocSet set = MapRecorder.getMinableSquares(miningWellLoc);
                set.updateIterable();
                MapLocation moveLoc = null;
                for (int i = set.size; --i >= 0;) {
                    if (!set.locs[i].equals(miningWellLoc) && rc.canMove(rc.getLocation().directionTo(set.locs[i]))) {
                        if (moveLoc == null || miningHQLoc.distanceSquaredTo(moveLoc) > miningHQLoc.distanceSquaredTo(set.locs[i])) {
                            moveLoc = set.locs[i];
                        }
                    }
                }
                if (moveLoc != null) {
                    rc.move(rc.getLocation().directionTo(moveLoc));
                }
            } else if (rc.canMove(dirToMine)) {
                // robot with the least resource go to the center of the mine
                // or if I sensed a carrier waiting to get in adjacent to, I move to center
                boolean leastResource = true;
                boolean teammateAdjacent = false;
                for (RobotInfo robot: rc.senseNearbyRobots(miningWellLoc, 8, myTeam)) {
                    if (robot.location.isAdjacentTo(miningWellLoc)) {
                        if (robot.getResourceAmount(ResourceType.MANA) + robot.getResourceAmount(ResourceType.ADAMANTIUM) < rc.getWeight()) {
                            leastResource = false;
                        }
                    } else if (robot.location.isAdjacentTo(rc.getLocation()) && robot.getResourceAmount(ResourceType.MANA) + robot.getResourceAmount(ResourceType.ADAMANTIUM) < 39) {
                        teammateAdjacent = true;
                    }
                }
                if (leastResource || teammateAdjacent) {
                    rc.move(dirToMine);
                }
            }
        } else { // moving toward mine
            // switch mine if original too congested
            if (rc.senseNearbyRobots(miningWellLoc, 3, myTeam).length >= MapRecorder.getMinableSquares(miningWellLoc).size - 1) {
                indicator += "congest";
                congestedMines.add(miningWellLoc);
                tryFindMine();
            }
            moveToward(miningWellLoc);
            moveToward(miningWellLoc);
            indicator += "2mine,";
        }
    }

    private static void dropResource() throws GameActionException {
        int ad = rc.getResourceAmount(ResourceType.ADAMANTIUM);
        int mn = rc.getResourceAmount(ResourceType.MANA);
        if (!rc.getLocation().isAdjacentTo(miningHQLoc)) {
            moveToward(miningHQLoc);
            indicator += "2drop";
        }
        if (rc.getLocation().isAdjacentTo(miningHQLoc)) {
            if (rc.canTransferResource(miningHQLoc, ResourceType.ADAMANTIUM, ad)) {
                rc.transferResource(miningHQLoc, ResourceType.ADAMANTIUM, ad);
                indicator += "drop";
            } else if (rc.canTransferResource(miningHQLoc, ResourceType.MANA, mn)) {
                rc.transferResource(miningHQLoc, ResourceType.MANA, mn);
                indicator += "drop";
            }
            if (rc.getWeight() == 0) {
                congestedMines.clear();
                state = MINING;
                tryFindMine();
                mine();
            }
        }
    }

    private static void tryFindMine() {
        if (needScoutWell()) {
            state = SCOUTING;
            scoutStartRound = rc.getRoundNum();
        } else {
            miningWellLoc = null;
            for (MapLocation loc : Comm.closestWells[miningResourceType.resourceID]) {
                if (loc == null)
                    break;
                if (congestedMines.contains(loc))
                    continue;
                if (miningWellLoc == null
                        || miningWellLoc.distanceSquaredTo(rc.getLocation()) > loc.distanceSquaredTo(rc.getLocation())) {
                    miningWellLoc = loc;
                }
            }
            if (miningWellLoc == null) {
                System.out.printf("all resource of type %s congested, disintegrate", miningResourceType);
                rc.disintegrate();
            }
            state = MINING;
        }
    }

    private static boolean needScoutWell() {
        // if we find a well of the required type we stop
        for (int i = wellReportCnt; i < wellSeenCnt; i++) {
            if (wellsToReport[i].getResourceType() == miningResourceType)
                return false;
        }
        if (Comm.closestWells[miningResourceType.resourceID][0] == null)
            return true;
        // for the first 15 turns we wanna scout around base if no well is found around
        if (rc.getRoundNum() <= 15 && getClosestDis(Comm.closestWells[miningResourceType.resourceID]) > 64)
            return true;
        return false;
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
                scoutCenter = new MapLocation(mapWidth / 2, mapHeight / 2);
                scoutStartRound = rc.getRoundNum();
            }
        }
    }
}
