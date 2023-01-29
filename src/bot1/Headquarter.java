package bot1;

import battlecode.common.*;
import bot1.util.FastIterableLocSet;

public class Headquarter extends Unit {
    private static int carrierCnt = 0;

    public static FastIterableLocSet spawnableSet = null; // set by MapRecorder.hqInit()
    public static int sensablePassibleArea = 0; // set by MapRecorder.hqInit()
    public static int hqid;

    public static int lastRoundAnchorBuilt = 0;
    public static int lastCongestedRound = -100;
    public static int lastEnemyRound = -100;
    public static boolean isCongested = false;

    private static RobotInfo closestEnemy;
    private static int enemyCount = 0;
    private static int friendlyCount = 0;
    private static int spawnableTileOccupied = 0;
    private static boolean canBuildCarrier, canBuildLauncher;
    private static int usableMN, usableAD, usableEL;

    public static void run() throws GameActionException {
        if (turnCount == 0) {
            // first turn all HQ report
            hqid = Comm.HQInit(rc.getLocation(), rc.getID());
            for (WellInfo well : rc.senseNearbyWells()) {
                Comm.reportWells(well.getResourceType().resourceID, well.getMapLocation());
            }
            Comm.commit_write();
            MapRecorder.hqInit();
        }
        if (rc.getRoundNum() % Comm.CARRIER_REPORT_FREQ == 0 && hqid == 0) {
            Comm.resetCarrierCnt();
        }

        usableMN = rc.getResourceAmount(ResourceType.MANA);
        usableAD = rc.getResourceAmount(ResourceType.ADAMANTIUM);
        usableEL = rc.getResourceAmount(ResourceType.ELIXIR);

        sense();

        if (closestEnemy != null) {
            Comm.reportEnemy(hqid, closestEnemy.location, rc.getRoundNum());
            // seeing enemy immediately decongests
            isCongested = false;
            lastCongestedRound = -100;
            lastEnemyRound = rc.getRoundNum();
        }
        Comm.reportCongest(hqid, isCongested);
        indicator += String.format("#carrier %d sym %b local congest %b global congest %b",
                Comm.getCarrierCnt(), Comm.isSymmetryConfirmed, isCongested, Comm.isCongested());

        canBuildLauncher = true;
        canBuildCarrier = true;
        if (rc.getRoundNum() - lastEnemyRound > 5 && rc.getRobotCount() / Comm.numHQ > 80) {
            canBuildLauncher = false;
        }
        if (rc.getRoundNum() - lastEnemyRound <= 5 || Comm.isCongested()) {
            canBuildCarrier = false;
        }
        tryBuildAnchor();
        tryBuildLauncher();
        tryBuildCarrier();

        Comm.commit_write();
        if (turnCount <= 3) { // for symmetry check
            MapRecorder.recordSym(500);
        }
    }

    private static void tryBuildAnchor() throws GameActionException {
        boolean shouldBuild = false;
        if ((rc.getRobotCount() - carrierCnt) / Comm.numHQ > 18 && carrierCnt / Comm.numHQ > 10)
            shouldBuild = true;

        // late game
        if ((rc.getRoundNum() > 1200 && rc.getRobotCount() / Comm.numHQ > 12))
            shouldBuild = true;

        // allow for a single early anchor for healing
        if (Comm.getClosestFriendlyIslandIndex() == 0 && rc.getRoundNum() > 180
                && hqid == 0 && rc.getRobotCount() / Comm.numHQ > 18)
            shouldBuild = true;

        if (shouldBuild
                && rc.getNumAnchors(Anchor.STANDARD) == 0
                && rc.getRoundNum() - lastRoundAnchorBuilt > 100) {
            usableAD -= Constants.ANCHOR_COST_AD;
            usableMN -= Constants.ANCHOR_COST_MN;
            if (rc.canBuildAnchor(Anchor.STANDARD)) {
                rc.buildAnchor(Anchor.STANDARD);
                lastRoundAnchorBuilt = rc.getRoundNum();
            }
        }
    }

    private static void tryBuildLauncher() throws GameActionException {
        int maxLauncherSpawn = Math.min(5, usableMN / Constants.LAUNCHER_COST_MN);
        if (canBuildLauncher && (maxLauncherSpawn > enemyCount || turnCount == 0)) {
            // only spawn launcher if can spawn more than enemies close by, or just save mana for tiebreaker lol
            MapLocation closestEnemyHQ = getClosestLoc(Comm.enemyHQLocations);
            for (int i = maxLauncherSpawn; --i >= 0 && rc.isActionReady();) {
                trySpawn(RobotType.LAUNCHER, closestEnemyHQ);
            }
        }
    }

    private static void tryBuildCarrier() throws GameActionException {
        int maxCarrierSpawn = Math.min(5, usableAD / Constants.CARRIER_COST_AD);
        if (canBuildCarrier) {
            // do not spawn miner if enemies are close as miners getting killed will mess up spanw Q
            for (int i = maxCarrierSpawn; --i >= 0 && rc.isActionReady();) {
                trySpawn(RobotType.CARRIER, rc.getLocation());
            }
        }
    }

    private static void sense() throws GameActionException {
        carrierCnt = Comm.getCarrierCnt();
        closestEnemy = null;
        friendlyCount = 0;
        spawnableTileOccupied = 0;
        enemyCount = 0;
        int dis = Integer.MAX_VALUE;
        for (RobotInfo robot : rc.senseNearbyRobots(-1)) {
            if (robot.team == oppTeam) {
                if (robot.type == RobotType.LAUNCHER) {
                    enemyCount++;
                    int newDis = rc.getLocation().distanceSquaredTo(robot.location);
                    if (newDis < dis) {
                        closestEnemy = robot;
                        dis = newDis;
                    }
                }
            } else {
                friendlyCount++;
                if (spawnableSet.contains(robot.location)) {
                    spawnableTileOccupied++;
                }
            }
        }
        // congestion detection
        if (((friendlyCount > sensablePassibleArea / 2 && friendlyCount > 10)
                || (spawnableTileOccupied > spawnableSet.size / 2 && spawnableTileOccupied > 5)
                || friendlyCount > 30
                || spawnableTileOccupied > 12
                || carrierCnt / Comm.numHQ > 25
                || rc.getRobotCount() / Comm.numHQ > 80)
                && rc.getRoundNum() > 100
                && rc.getRobotCount() / Comm.numHQ > 30) {
            lastCongestedRound = rc.getRoundNum();
            if (!isCongested) {
                System.out.printf("congestion detected %d/%d, %d/%d\n", friendlyCount, sensablePassibleArea, spawnableTileOccupied, spawnableSet.size);
                isCongested = true;
            }
        } else if (isCongested
                && rc.getRoundNum() - lastCongestedRound > 30
                && rc.getRoundNum() % Comm.CARRIER_REPORT_FREQ > 60) {
            System.out.println("not congested anymore somehow??");
            isCongested = false;
        }
    }

    private static boolean trySpawn(RobotType robotType, MapLocation center) throws GameActionException {
        MapLocation bestSpawn = null;
        for (int i = spawnableSet.size; --i >= 0;) {
            if ((bestSpawn == null || spawnableSet.locs[i].distanceSquaredTo(center) < bestSpawn.distanceSquaredTo(center))
                    && rc.canBuildRobot(robotType, spawnableSet.locs[i])) {
                bestSpawn = spawnableSet.locs[i];
            }
        }
        if (bestSpawn == null) {
            System.out.printf("out of loc to build %s\n", robotType);
            return false;
        }
        rc.buildRobot(robotType, bestSpawn);
        return true;
    }
}
