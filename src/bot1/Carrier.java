package bot1;

import battlecode.common.*;
import bot1.util.FastIterableIntSet;
import bot1.util.FastIterableLocSet;
import bot1.util.FastLocIntMap;
import bot1.util.FastMath;

import java.util.Random;

public class Carrier extends Unit {
    // purposes
    public static final int MINE_MN = 1; // mining is default
    public static final int MINE_AD = 2;

    public static final int MINING = 10;
    public static final int DROPPING_RESOURCE = 11;

    public static final int REPORT_AND_RUNAWAY = 20;
    public static final int RUNAWAY = 21;

    public static final int SCOUTING = 30;
    public static final int REPORTING_INFO = 31;

    public static final int ANCHORING = 40;

    public static int lastCarrierReportRound = -1000;
    public static int carrierID = 0;

    public static int purpose = 0;
    public static int state = 0;
    public static ResourceType miningResourceType;
    public static MapLocation miningWellLoc;
    public static MapLocation miningHQLoc;

    public static MapLocation lastEnemyLoc = null;
    public static int lastEnemyRound = 0;
    public static RobotInfo closestEnemy = null;

    public static FastIterableLocSet congestedMines = new FastIterableLocSet(290);
    public static FastLocIntMap lastEnemyOnMine = new FastLocIntMap();
    private static Random rng;

    // scouting vars
    static int startHQID;
    static MapLocation startHQLoc;
    static int scoutStartRound;
    static MapLocation scoutTarget = null;
    static MapLocation scoutCenter = null;
    static double scoutAngle = 0;
    public static FastIterableLocSet[] wellsSeen = {null, null, null};
    static FastIterableLocSet[] wellsToReport = {null, null, null};
    // for islands only report location
    static MapLocation[] islandLocations = new MapLocation[GameConstants.MAX_NUMBER_ISLANDS + 1];
    static int[] islandsToReport = new int[GameConstants.MAX_NUMBER_ISLANDS];
    static int islandReportIndex = -1;

    static void run () throws GameActionException {
        if (turnCount == 0) {
            wellsSeen[ResourceType.MANA.resourceID] = new FastIterableLocSet(145);
            wellsSeen[ResourceType.ADAMANTIUM.resourceID] = new FastIterableLocSet(145);
            wellsToReport[ResourceType.MANA.resourceID] = new FastIterableLocSet(10);
            wellsToReport[ResourceType.ADAMANTIUM.resourceID] = new FastIterableLocSet(10);

            if (rc.canWriteSharedArray(0, 0)) {
                report(); // to get the carrier ID
            }

            startHQID = getClosestID(Comm.friendlyHQLocations);
            startHQLoc = Comm.friendlyHQLocations[startHQID];
            scoutCenter = startHQLoc;

            rng = new Random(rc.getID());
            purpose = rc.getID() % 4 == 0? MINE_AD : MINE_MN;
            updateWells();
            resumeWork();
        }

        indicator += String.format("S%dR%s,", state, miningResourceType);
        checkAnchor();

        if (state == SCOUTING) {
            scoutSense();
        }
        senseEnemy();

        if (state == ANCHORING) {
            anchor();
        }
        if (state == SCOUTING) {
            scoutMove();
            senseEnemy();
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
            handleMiningState();
        }

        if (state == DROPPING_RESOURCE) {
            dropResource();
        }

        // just try sense and report no matter what the role is
        scoutSense();
        if (needReport() && rc.canWriteSharedArray(0, 0)) {
            report();
        }
        MapRecorder.recordSym(500);
    }

    // sense and attack nearby enemies
    private static void senseEnemy() throws GameActionException {
        closestEnemy = null;
        int dis = Integer.MAX_VALUE;
        int strength = 0;
        for (RobotInfo robot : rc.senseNearbyRobots(-1, oppTeam)) {
            if (robot.type == RobotType.LAUNCHER) {
                int newDis = robot.location.distanceSquaredTo(rc.getLocation());
                if (closestEnemy == null || newDis < dis) {
                    dis = newDis;
                    closestEnemy = robot;
                    strength -= robot.health;
                }
            } else if (robot.type == RobotType.HEADQUARTERS) {
                MapRecorder.reportEnemyHQ(robot.location);
            }
        }
        if (closestEnemy != null) {
            for (RobotInfo robot : rc.senseNearbyRobots(closestEnemy.location, 16, myTeam)) {
                if (robot.type == RobotType.LAUNCHER) {
                    strength += robot.health;
                }
            }
            if (strength > 0) {
                return;
            }
            lastEnemyLoc = closestEnemy.location;
            lastEnemyRound = rc.getRoundNum();
            if (state == MINING || state == DROPPING_RESOURCE) {
                lastEnemyOnMine.remove(miningWellLoc);
                lastEnemyOnMine.add(miningWellLoc, rc.getRoundNum());
            } else if (state == ANCHORING) { // reset anchoring target
                if (targetIslandIndex != -1) {
                    islandIgnoreUntil[targetIslandIndex] = rc.getRoundNum() + 150;
                }
                targetLoc = null;
                targetIslandIndex = 0;
            }
            // try preserve the resource, unless too close, TODO if against a wall maybe just attack
            state = RUNAWAY;
            if (rc.canAttack(closestEnemy.location)) {
                indicator += "attack,";
                rc.attack(closestEnemy.location);
            }
            state = RUNAWAY;
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

    private static MapLocation targetLoc;
    private static int targetIslandIndex = -1;
    private static int[] islandIgnoreUntil = new int[islandCount + 1];
    private static int lastRandomLocRound = -100;
    private static void anchor() throws GameActionException {
        indicator += String.format("anchor %d@%s,", targetIslandIndex, targetLoc);

        if (targetIslandIndex == -1 && targetLoc != null &&
                (rc.getRoundNum() - lastRandomLocRound > 100 || rc.canSenseLocation(targetLoc))) {
            targetLoc = null;
        }

        if (targetLoc != null) {
            rc.setIndicatorLine(rc.getLocation(), targetLoc, 255, 255, 255);
        }

        int currentIslandIndex = rc.senseIsland(rc.getLocation());
        if (currentIslandIndex != -1
                && currentIslandIndex == targetIslandIndex
                && rc.senseTeamOccupyingIsland(currentIslandIndex) == Team.NEUTRAL
                && rc.canPlaceAnchor()) {
            rc.placeAnchor();
            // on island so must can report
            Comm.reportIsland(rc.getLocation(), currentIslandIndex, Comm.ISLAND_FRIENDLY);
            Comm.commit_write();
            resumeWork();
        }

        // try to find target from Comm and visual
        if (targetLoc == null || rc.getRoundNum() % 20 == 0) {
            double bestScore = -1e9;
            for (int i = 1; i <= islandCount; i++) {
                MapLocation islandLocation = islandLocations[i];
                if (islandLocation == null) {
                    islandLocation = Comm.getIslandLocation(i);
                }
                if (islandLocation == null) {
                    continue;
                }

                double score = 0;
                // ignore penalty
                if (islandIgnoreUntil[i] > rc.getRoundNum()) {
                    score -= (islandIgnoreUntil[i] - rc.getRoundNum()) * 100;
                }
                // prefer neutral island
                if (Comm.getIslandStatus(i) == Comm.ISLAND_NEUTRAL) {
                    score += 1000;
                }
                double dis2friend = Math.sqrt(getClosestDis(islandLocation, Comm.friendlyHQLocations));
                double dis2e = Math.sqrt(getClosestDis(islandLocation, Comm.enemyHQLocations));
                double dis2me = Math.sqrt(islandLocation.distanceSquaredTo(rc.getLocation()));
                // prefer islands closer to my side
                if (dis2e < dis2friend) {
                    score -= 2000;
                }
                // prefer island closer to the center
                score -= 5 * Math.abs(dis2friend - dis2e); // centerness penalty
                // prefer island closer to me
                score -= dis2me;
                // add some salt of rng
                score += FastMath.rand256() % 16;
//                System.out.printf("island loc %s score %.2f\n", islandLocation, score);
                if (score > bestScore) {
                    bestScore = score;
                    targetLoc = islandLocation;
                    targetIslandIndex = i;
                }
            }
        }

        // otherwise if visually see a target, focus on that
        int[] islands = rc.senseNearbyIslands();
        int dis = targetLoc == null? Integer.MAX_VALUE : targetLoc.distanceSquaredTo(rc.getLocation());
        for (int island : islands) {
            if (rc.senseTeamOccupyingIsland(island) == myTeam) {
                if (island == targetIslandIndex) {
                    // the original target is not placable anymore
                    islandIgnoreUntil[island] = rc.getRoundNum() + 300;
                    targetIslandIndex = -1;
                    targetLoc = null;
                }
                continue; // we can't place here
            }
            if (island == targetIslandIndex && FastMath.rand256() % 32 != 0) {
                break; // our target is correct, just go there, change target only with a small chance
            }
            if (targetIslandIndex != -1)
                continue;
            MapLocation locs[] = rc.senseNearbyIslandLocations(island);
            for (MapLocation loc : locs) {
                if (rc.getLocation().distanceSquaredTo(loc) < dis) {
                    targetLoc = loc;
                    targetIslandIndex = island;
                    dis = rc.getLocation().distanceSquaredTo(loc);
                }
            }
        }


        // randomly select a target that we haven't seen
        for (int i = 0; targetLoc == null; i++) {
            int x = FastMath.rand256() % mapWidth;
            int y = FastMath.rand256() % mapHeight;
            if ((MapRecorder.vals[x * mapHeight + y] & MapRecorder.SEEN_BIT) == 0 || i > 15) {
                targetLoc = new MapLocation(x, y);
                targetIslandIndex = -1;
                lastRandomLocRound = rc.getRoundNum();
            }
        }
        moveToward(targetLoc);
    }

    private static MapLocation lastSenseLocation = new MapLocation(-1, -1);
    private static void scoutSense() throws GameActionException {
        if (rc.getLocation().equals(lastSenseLocation))
            return;
        lastSenseLocation = rc.getLocation();
        for (WellInfo well : rc.senseNearbyWells()) {
            if (!wellsSeen[well.getResourceType().resourceID].contains(well.getMapLocation())) {
                wellsSeen[well.getResourceType().resourceID].add(well.getMapLocation());
                wellsToReport[well.getResourceType().resourceID].add(well.getMapLocation());
            }
        }

        for (int i : rc.senseNearbyIslands()) {
            if (Comm.getIslandLocation(i) != null || islandLocations[i] != null) {
                continue;
            }
            MapLocation islandloc = rc.senseNearbyIslandLocations(i)[0];
            islandsToReport[++islandReportIndex] = i;
            islandLocations[i] = islandloc;
        }
    }
    private static boolean setScoutTarget() {
        boolean success = false;
        for (; Math.abs(scoutAngle) <= Math.PI * 18; scoutAngle += Math.PI / 6 * (rc.getID() % 2 == 0? 1 : -1)) {
            // scouting along the line r=theta
            scoutTarget = scoutCenter.translate(
                    (int)(Math.cos(scoutAngle) * scoutAngle),
                    (int)(Math.sin(scoutAngle) * scoutAngle));
            if (rc.onTheMap(scoutTarget) && (MapRecorder.vals[scoutTarget.x * mapHeight + scoutTarget.y] & MapRecorder.SEEN_BIT) == 0) {
                success = true;
                break;
            }
        }
        if (!success) scoutTarget = null;
        return success;
    }

    private static void scoutMove() throws GameActionException {
        if (resumeWork()) {
            state = REPORTING_INFO;
            return;
        }

        if ((scoutTarget == null || (MapRecorder.vals[scoutTarget.x * mapHeight + scoutTarget.y] & MapRecorder.SEEN_BIT) != 0) && !setScoutTarget())  {
            System.out.println("miner out of mine after exploring map, disintegrate");
            rc.disintegrate();
            return;
        }

        indicator += String.format("T%s,A%.1fPI", scoutTarget, scoutAngle / Math.PI);
        moveToward(scoutTarget);
    }

    private static boolean needReport() {
        return wellsToReport[ResourceType.ADAMANTIUM.resourceID].size > 0
                || wellsToReport[ResourceType.MANA.resourceID].size > 0
                || islandReportIndex >= 0
                || Comm.needSymmetryReport
                || rc.getRoundNum() / Comm.CARRIER_REPORT_FREQ != lastCarrierReportRound / Comm.CARRIER_REPORT_FREQ;
    }

    private static void report() throws GameActionException {
        // do report
//        if (lastEnemyLoc != null) {
//            Comm.reportEnemy(lastEnemyLoc, lastEnemyRound);
//        }
        for (int resource = 1; resource <= 2; resource++) {
            if (wellsToReport[resource].size > 0) {
                wellsToReport[resource].updateIterable();
                for (int i = wellsToReport[resource].size; --i >= 0;) {
                    Comm.reportWells(resource, wellsToReport[resource].locs[i]);
                }
                wellsToReport[resource].clear();
            }
        }
        for (;islandReportIndex >= 0; islandReportIndex--) {
            int islandIndex = islandsToReport[islandReportIndex];
            Comm.reportIsland(islandLocations[islandIndex], islandIndex, -1);
        }
        if (rc.getRoundNum() / Comm.CARRIER_REPORT_FREQ != lastCarrierReportRound / Comm.CARRIER_REPORT_FREQ
                && rc.getNumAnchors(Anchor.STANDARD) == 0) {
            // anchoring carriers don't report
            carrierID = Comm.carrierReport();
            lastCarrierReportRound = rc.getRoundNum();
        }
        Comm.reportSym();
        Comm.commit_write();
    }

    private static void handleReportState() throws GameActionException {
        if (needReport()) {
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
        if (state == REPORT_AND_RUNAWAY) {
            state = RUNAWAY;
        } else { // reporting info
            resumeWork();
        }
    }

    private static void runaway() throws GameActionException {
        indicator += "RN,";
        miningHQLoc = getClosestLoc(Comm.friendlyHQLocations);
        if (closestEnemy == null && rc.getRoundNum() - lastEnemyRound >= 5) {
            resumeWork();
        } else {
            Direction backDir = rc.getLocation().directionTo(lastEnemyLoc).opposite();
            Direction[] dirs = {Direction.CENTER, backDir, backDir.rotateLeft(), backDir.rotateRight(),
                    backDir.rotateLeft().rotateLeft(), backDir.rotateRight().rotateRight()};
            for (int i = 2; --i >= 0 && rc.isMovementReady();) {
                double bestScore = -1e7;
                Direction bestDir = null;
                for (Direction dir : dirs) {
                    if (!rc.canMove(dir)) {
                        continue;
                    }
                    MapLocation loc = rc.getLocation().add(dir);
                    double score = 0;
                    if (loc.distanceSquaredTo(lastEnemyLoc) <= 20) {
                        score -= 1000;
                    }
                    if (loc.distanceSquaredTo(lastSenseLocation) <= 16) {
                        score -= 1000;
                    }
                    // if we have AD and can hit enemy after moving, do it
                    if (rc.getResourceAmount(ResourceType.ADAMANTIUM) >= 16
                            && closestEnemy != null
                            && loc.distanceSquaredTo(closestEnemy.location) <= 9) {
                        score += 3000;
                    }
                    // if I have MN and is one tile away from HQ, I can die to deliver it
                    if (rc.getResourceAmount(ResourceType.MANA) >= 20
                            && loc.isAdjacentTo(miningHQLoc)) {
                        score += 3000;
                    }
                    score -= Math.sqrt(loc.distanceSquaredTo(miningHQLoc)); // stay close to HQ
                    score += Math.sqrt(loc.distanceSquaredTo(lastEnemyLoc)); // get away from enemy
                    if (score > bestScore) {
                        bestScore = score;
                        bestDir = dir;
                    }
                }
                if (bestDir != null && bestDir != Direction.CENTER) {
                    rc.move(bestDir);
                }
                if (rc.getWeight() > 0 && rc.getLocation().isAdjacentTo(miningHQLoc)) {
                    int ad = rc.getResourceAmount(ResourceType.ADAMANTIUM);
                    int mn = rc.getResourceAmount(ResourceType.MANA);
                    if (rc.canTransferResource(miningHQLoc, ResourceType.ADAMANTIUM, ad)) {
                        rc.transferResource(miningHQLoc, ResourceType.ADAMANTIUM, ad);
                    } else if (rc.canTransferResource(miningHQLoc, ResourceType.MANA, mn)) {
                        rc.transferResource(miningHQLoc, ResourceType.MANA, mn);
                    }
                } else if (closestEnemy != null && rc.canAttack(closestEnemy.location)) {
                    rc.attack(closestEnemy.location);
                }
            }
        }
    }

    // when next to mine
    private static void collect() throws GameActionException {
        int hqid = getClosestID(Comm.friendlyHQLocations);
        miningHQLoc = Comm.friendlyHQLocations[hqid];
        if (rc.canCollectResource(miningWellLoc, -1)) {
            rc.collectResource(miningWellLoc, -1);
        }
        if (rc.getWeight() >= GameConstants.CARRIER_CAPACITY) {
            state = DROPPING_RESOURCE;
            return;
        }
        indicator += "mine,";
        // move in pattern to allow others miners to join and other units to pass the mine
        if (!rc.isMovementReady()) {
            return;
        }
        FastIterableLocSet minableLocs = MapRecorder.getMinableSquares(miningWellLoc, miningResourceType);
        minableLocs.updateIterable();
        // sense if there are teammates next to me that I need to let in
        boolean teammateAdjacent = false;
        for (RobotInfo robot: rc.senseNearbyRobots(rc.getLocation(), 3, myTeam)) {
            if (!minableLocs.contains(robot.location) && robot.getResourceAmount(ResourceType.MANA) + robot.getResourceAmount(ResourceType.ADAMANTIUM) < 39) {
                teammateAdjacent = true;
            }
        }
        // try to find the best mineable square that I can move to,
        // carrier with a lot of resources will only move if the new position is closer to base
        // or there are people waiting to be let in every 5 turns (so allow less resource robot to empty space first)
        MapLocation moveLoc = (rc.getWeight() > 30 && !teammateAdjacent && rc.getRoundNum() % 5 == 0) ?
                rc.getLocation() : null;
        for (int i = minableLocs.size; --i >= 0;) {
            if (minableLocs.locs[i].isAdjacentTo(rc.getLocation())
                    && rc.canMove(rc.getLocation().directionTo(minableLocs.locs[i]))) {
                if (moveLoc == null
                        || (rc.getWeight() > 20 && miningHQLoc.distanceSquaredTo(moveLoc) > miningHQLoc.distanceSquaredTo(minableLocs.locs[i]))
                        || (rc.getWeight() <= 20 && miningHQLoc.distanceSquaredTo(moveLoc) < miningHQLoc.distanceSquaredTo(minableLocs.locs[i]))) {
                    moveLoc = minableLocs.locs[i];
                }
            }
        }
        if (moveLoc != null && !moveLoc.equals(rc.getLocation())) {
            rc.move(rc.getLocation().directionTo(moveLoc));
        }
    }

    private static void handleMiningState() throws GameActionException {
        if (rc.getLocation().isAdjacentTo(miningWellLoc)) {
            collect();
        } else { // moving toward mine
            // switch mine if original too congested

            FastIterableLocSet minableSquares = MapRecorder.getMinableSquares(miningWellLoc, miningResourceType);
            minableSquares.updateIterable();

            int occupied = 0;
            for (int i = minableSquares.size; --i >= 0;) {
                MapLocation loc = minableSquares.locs[i];
                if (rc.canSenseLocation(loc) && rc.senseRobotAtLocation(loc) != null) {
                    occupied++;
                }
            }
            if (occupied >= minableSquares.size) {
                indicator += "congest";
                congestedMines.add(miningWellLoc);
                if (!resumeWork()) {
                    return;
                }
            }

            moveToward(miningWellLoc);
            moveToward(miningWellLoc);
            if (rc.getLocation().isAdjacentTo(miningWellLoc)) {
                collect();
            }
        }
    }

    private static void dropResource() throws GameActionException {
        int ad = rc.getResourceAmount(ResourceType.ADAMANTIUM);
        int mn = rc.getResourceAmount(ResourceType.MANA);
        if (!rc.getLocation().isAdjacentTo(miningHQLoc)) {
            moveToward(miningHQLoc);
        }
        if (rc.getLocation().isAdjacentTo(miningHQLoc)) {
            if (rc.canTransferResource(miningHQLoc, ResourceType.ADAMANTIUM, ad)) {
                rc.transferResource(miningHQLoc, ResourceType.ADAMANTIUM, ad);
            } else if (rc.canTransferResource(miningHQLoc, ResourceType.MANA, mn)) {
                rc.transferResource(miningHQLoc, ResourceType.MANA, mn);
            }
            if (rc.getWeight() == 0) {
                congestedMines.clear();
                if (resumeWork()) {
                    handleMiningState();
                }
            }
        }
    }

    // this func transitions into either mining or scouting or anchoring
    // and returns true if there's no need to scout anymore
    private static boolean resumeWork() {
        if (rc.getNumAnchors(Anchor.STANDARD) != 0) {
            state = ANCHORING;
            return true;
        }
        // decide what resource to mine
        if (mapWidth * mapHeight <= 1000) {
            if (rc.getRoundNum() < 150) {
                miningResourceType = ResourceType.MANA; // mana only on small map for the first 150 turns
            } else {
                miningResourceType = carrierID % 5 == 4? ResourceType.ADAMANTIUM : ResourceType.MANA;
            }
        } else if (mapWidth * mapHeight <= 1700) {
            miningResourceType = carrierID % 5 == 4? ResourceType.ADAMANTIUM : ResourceType.MANA;
        } else if (mapWidth * mapHeight <= 2600) {
            miningResourceType = carrierID % 4 == 3? ResourceType.ADAMANTIUM : ResourceType.MANA;
        } else {
            miningResourceType = carrierID % 3 == 2? ResourceType.ADAMANTIUM : ResourceType.MANA;
        }
        if (rc.getWeight() > 10) {
            // TODO maybe more complicated decision makings
            miningHQLoc = getClosestLoc(Comm.friendlyHQLocations);
            state = DROPPING_RESOURCE;
            return true;
        }
        miningWellLoc = null;
        MapLocation congestedLocation = null;
        MapLocation dangerLocation = null;
        boolean enemySeen = false;
        wellsSeen[miningResourceType.resourceID].updateIterable();
        for (int i = wellsSeen[miningResourceType.resourceID].size; --i >= 0;) {
            MapLocation loc = wellsSeen[miningResourceType.resourceID].locs[i];

            // for the first 15 rounds, we ignore wells too far away from starting HQ to encourage carrier
            // to scout around base first
            if (rc.getRoundNum() <= 15 && loc.distanceSquaredTo(startHQLoc) > 100) {
                continue;
            }

            if (congestedMines.contains(loc)) {
                // congested mines are backups in case there have been enemies seen
                if (congestedLocation == null
                        || congestedLocation.distanceSquaredTo(rc.getLocation()) > loc.distanceSquaredTo(rc.getLocation())) {
                    congestedLocation = loc;
                }
                continue;
            }

            int enemyRound = lastEnemyOnMine.getVal(loc);
            if (enemyRound != -1 && rc.getRoundNum() - enemyRound <= 60) {
                // if enemies recently seen at this location, it would be dangerous to go again
                if (dangerLocation == null || lastEnemyOnMine.getVal(dangerLocation) > enemyRound) {
                    dangerLocation = loc;
                }
                // if enemies are seen congestion probably should be reset
                congestedMines.clear();
                enemySeen = true;
            } else {
                // find the closest mine to robot
                if (miningWellLoc == null
                        || miningWellLoc.distanceSquaredTo(rc.getLocation()) > loc.distanceSquaredTo(rc.getLocation())) {
                    miningWellLoc = loc;
                }
            }
        }

        if (miningWellLoc == null && enemySeen) {
            if (congestedLocation != null) {
                miningWellLoc = congestedLocation;
            } else if (dangerLocation != null) {
                miningWellLoc = dangerLocation;
            }
        }
        if (miningWellLoc == null) {
            state = SCOUTING;
            scoutStartRound = rc.getRoundNum();
            return false;
        } else {
            state = MINING;
            return true;
        }
    }

    public static void updateWells() {
        for (int resource = 1; resource <= 2; resource++) {
            for (int i = 0; i < Comm.NUM_WELLS; i++) {
                if (Comm.closestWells[resource][i] != null) {
                    wellsSeen[resource].add(Comm.closestWells[resource][i]);
                } else {
                    break;
                }
            }
        }
    }
}
