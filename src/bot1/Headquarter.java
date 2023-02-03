package bot1;

import battlecode.common.*;
import bot1.util.FastIterableLocSet;

public class Headquarter extends Unit {
    private static int carrierCnt = 0;

    public static FastIterableLocSet spawnableSet = null; // set by MapRecorder.hqInit()
    public static int sensablePassibleArea = 0; // set by MapRecorder.hqInit()
    public static int hqid;

    public static int lastRoundAnchorBuilt = -1000;
    public static int lastCongestedRound = -100;
    public static int lastEnemyRound = -100;
    public static boolean isCongested = false;

    private static RobotInfo closestEnemy;
    private static int strength;
    private static int friendlyCount = 0;
    private static int spawnableTileOccupied = 0;
    private static boolean canBuildCarrier, canBuildLauncher;
    private static int usableMN, usableAD, usableEL;

    private static MapLocation curLoc = rc.getLocation();

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
        if (rc.getRoundNum() - lastEnemyRound > 5 && rc.getRobotCount() / Comm.numHQ > 100) {
            canBuildLauncher = false;
        }
        if (rc.getRoundNum() > 1900 && rc.getRobotCount() / Comm.numHQ > 30) {
            canBuildLauncher = false;
            // this is mostly a stalemate, accumulate mana for tiebreaker and hope for the best
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
        if (rc.getRoundNum() - lastEnemyRound <= 50) {
            return;
        }

        boolean shouldBuild = false;
        if ((rc.getRobotCount() - carrierCnt) / Comm.numHQ > 18 && carrierCnt / Comm.numHQ > 10)
            shouldBuild = true;

        // late game
        if ((rc.getRoundNum() > 1200 && rc.getRobotCount() / Comm.numHQ > 12))
            shouldBuild = true;

        // allow for a single early anchor for healing
        if (Comm.getClosestFriendlyIslandIndex() == 0 && rc.getRoundNum() > 150
                && hqid == 0 && rc.getRobotCount() / Comm.numHQ > 15)
            shouldBuild = true;

        double interval = 6500.0 / (islandCount + 30.0);
        // 4 island -> 191, 12 islands -> 154, 36 islands -> 98
        interval *= Math.sqrt(Comm.numHQ); // when more HQ, interval longer

        if (shouldBuild
                && rc.getNumAnchors(Anchor.STANDARD) == 0
                && rc.getRoundNum() - lastRoundAnchorBuilt > interval) {
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
        double x = 0, y = 0;
        if (canBuildLauncher && (maxLauncherSpawn * Launcher.MAX_HEALTH + strength >= 0 || turnCount == 0)) {
            // only spawn launcher if can spawn more than enemies close by, or just save mana for tiebreaker lol
            MapLocation spawnLoc;
            if (closestEnemy != null) {
                Direction dir2e = curLoc.directionTo(closestEnemy.location);
                spawnLoc = curLoc.subtract(dir2e).subtract(dir2e).subtract(dir2e).subtract(dir2e);
            } else if (turnCount == 0) {
                spawnLoc = new MapLocation(mapWidth / 2, mapHeight / 2);
            } else {
                spawnLoc = getClosestLoc(Comm.enemyHQLocations);
            }
            for (int i = 0; i < maxLauncherSpawn && rc.isActionReady(); i++) {
                MapLocation loc;
                if (i == 0) {
                    loc = trySpawn(RobotType.LAUNCHER, spawnLoc.x, spawnLoc.y);
                } else {
                    loc = trySpawn(RobotType.LAUNCHER, x / i, y / i);
                }
                if (loc == null) {
                    break;
                }
                x += loc.x;
                y += loc.y;
            }
        }
    }

    private static void tryBuildCarrier() throws GameActionException {
        int maxCarrierSpawn = Math.min(5, usableAD / Constants.CARRIER_COST_AD);
        if (canBuildCarrier) {
            // do not spawn miner if enemies are close as miners getting killed will mess up spanw Q
            for (int i = maxCarrierSpawn; --i >= 0 && rc.isActionReady();) {
                trySpawn(RobotType.CARRIER, curLoc.x, curLoc.y);
            }
        }
    }

    private static void sense() throws GameActionException {
        carrierCnt = Comm.getCarrierCnt();
        closestEnemy = null;
        friendlyCount = 0;
        spawnableTileOccupied = 0;
        strength = 0;
        int dis = Integer.MAX_VALUE;
        for (RobotInfo robot : rc.senseNearbyRobots(-1)) {
            if (robot.team == oppTeam) {
                switch (robot.type) {
                    case LAUNCHER:
                    case DESTABILIZER:
                        lastEnemyRound = rc.getRoundNum();
                        strength -= robot.health;
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
                switch (robot.type) {
                    case LAUNCHER:
                    case DESTABILIZER:
                        strength += robot.health;
                }
            }
        }
        // congestion detection
        if (((friendlyCount > sensablePassibleArea / 2 && friendlyCount > 10)
                || (spawnableTileOccupied > spawnableSet.size / 2 && spawnableTileOccupied > 5)
                || friendlyCount > 30
                || spawnableTileOccupied > 12
                || rc.getRobotCount() / Comm.numHQ > 80)
                && rc.getRoundNum() > 100
                && carrierCnt / Comm.numHQ > 16
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

    private static MapLocation trySpawn(RobotType robotType, double x, double y) throws GameActionException {
        MapLocation bestSpawn = null;
        double bestDis = 1e6;
        for (int i = spawnableSet.size; --i >= 0;) {
            if (turnCount == 0 && spawnableSet.locs[i].distanceSquaredTo(curLoc) == 9)
                continue;
            MapLocation loc = spawnableSet.locs[i];
            double dis = Math.hypot(x - loc.x, y - loc.y);
            if (dis + 1e-6 < bestDis && rc.canBuildRobot(robotType, loc)) {
                bestSpawn = loc;
                bestDis = dis;
            } else if (Math.abs(dis - bestDis) < 1e-6 && Math.hypot(curLoc.x - bestSpawn.x, curLoc.y - bestSpawn.y) < Math.hypot(curLoc.x - loc.x, curLoc.y - loc.y)) {
                bestSpawn = loc;
                bestDis = dis;
            }
        }
        if (bestSpawn == null) {
            System.out.printf("out of loc to build %s\n", robotType);
            return null;
        }
        rc.buildRobot(robotType, bestSpawn);
        return bestSpawn;
    }
}
