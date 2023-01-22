package bot1;

import battlecode.common.*;
import bot1.util.FastIterableLocSet;

import java.util.Map;

public class Headquarter extends Unit {
    private static int carrierCnt = 0;

    public static FastIterableLocSet spawnableSet = null; // set by MapRecorder.hqInit()
    public static int sensablePassibleArea = 0; // set by MapRecorder.hqInit()
    public static int hqid;

    public static int lastRoundAnchorBuilt = 0;
    public static int lastCongestedRound = -100;
    public static int lastEnemyRound = -100;
    public static boolean isCongested = false;

    public static void run() throws GameActionException {
        if (turnCount == 0) {
            // first turn all HQ report
            hqid = Comm.HQInit(rc.getLocation(), rc.getID());
            for (WellInfo well : rc.senseNearbyWells()) {
                Comm.reportWells(well.getResourceType().resourceID, well.getMapLocation());
            }
            Comm.commit_write();
            MapRecorder.hqInit();
            if (Comm.friendlyHQLocations[0].equals(rc.getLocation())) {
                trySpawn(RobotType.CARRIER, new MapLocation(mapWidth / 2, mapHeight / 2), Carrier.SCOUT_SYMMETRY);
            }
        }

        if (!Comm.isSymmetryConfirmed && turnCount > 0 && turnCount % 150 == 0) {
            System.out.println("too long no sym, send another sym scout");
            trySpawn(RobotType.CARRIER, new MapLocation(mapWidth / 2, mapHeight / 2), Carrier.SCOUT_SYMMETRY);
        }

        RobotInfo closestEnemy = null;
        int dis = Integer.MAX_VALUE;
        int enemyCount = 0;
        int friendlyCount = 0;
        int spawnableTileOccupied = 0;
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
                || (spawnableTileOccupied > spawnableSet.size / 2 && spawnableTileOccupied > 5))
                || friendlyCount > 30
                || spawnableTileOccupied > 12
                && rc.getRoundNum() > 100
                && rc.getRobotCount() / Comm.numHQ > 30) {
            lastCongestedRound = rc.getRoundNum();
            if (!isCongested) {
                System.out.printf("congestion detected %d/%d, %d/%d\n", friendlyCount, sensablePassibleArea, spawnableTileOccupied, spawnableSet.size);
                isCongested = true;
            }
        } else if (isCongested
                && rc.getRoundNum() - lastCongestedRound > 30) {
            System.out.println("not congested anymore somehow??");
            isCongested = false;
        }
        if (closestEnemy != null) {
            Comm.reportEnemy(closestEnemy.location, rc.getRoundNum());
            // seeing enemy immediately decongests
            isCongested = false;
            lastCongestedRound = -100;
            lastEnemyRound = rc.getRoundNum();
        }
        Comm.reportCongest(hqid, isCongested);
        indicator += String.format("enemy %s round %d congest %b", Comm.getEnemyLoc(), Comm.getEnemyRound(), isCongested);

        if (Comm.isCongested()
                && rc.getRoundNum() - lastEnemyRound > 50
                && rc.canBuildAnchor(Anchor.STANDARD)
                && rc.getNumAnchors(Anchor.STANDARD) == 0
                && rc.getRoundNum() - lastRoundAnchorBuilt > 50) {
            rc.buildAnchor(Anchor.STANDARD);
            lastRoundAnchorBuilt = rc.getRoundNum();
        }
        int maxLauncherSpawn = Math.max(5, rc.getResourceAmount(ResourceType.MANA) / Constants.LAUNCHER_COST_MN);
        if (maxLauncherSpawn > enemyCount) {
            // only spawn launcher if can spawn more than enemies close by, or just save mana for tiebreaker lol
            for (int i = 5; --i >= 0
                    && rc.isActionReady()
                    && rc.getResourceAmount(ResourceType.MANA) >= Constants.LAUNCHER_COST_MN;) {
                trySpawn(RobotType.LAUNCHER, new MapLocation(mapWidth / 2, mapHeight / 2), -1);
            }
        }
        if (!Comm.isCongested() && closestEnemy == null) {
            // do not spawn miner if enemies are close as miners getting killed will mess up spanw Q
            for (int i = 5; --i >= 0
                    && rc.isActionReady()
                    && rc.getResourceAmount(ResourceType.ADAMANTIUM) >= Constants.CARRIER_COST_AD;) {
                trySpawn(RobotType.CARRIER, rc.getLocation(), ++carrierCnt % 3 == 0? Carrier.MINE_AD : Carrier.MINE_MN);
            }
        }

        Comm.commit_write();
        if (turnCount <= 3) { // for symmetry check
            MapRecorder.recordSym(500);
        }
    }

    // if no need to set spawn flag pass -1
    private static boolean trySpawn(RobotType robotType, MapLocation center, int spawnFlag) throws GameActionException {
        MapLocation bestSpawn = null;
        for (int i = spawnableSet.size; --i >= 0;) {
            if ((bestSpawn == null || spawnableSet.locs[i].distanceSquaredTo(center) < bestSpawn.distanceSquaredTo(center))
                    && rc.canBuildRobot(robotType, spawnableSet.locs[i])) {
                bestSpawn = spawnableSet.locs[i];
            }
        }
        if (bestSpawn == null) {
            System.out.println("out of loc to build");
            return false;
        }
        if (spawnFlag > 0 && !Comm.trySetSpawnFlag(bestSpawn, spawnFlag)) {
            System.out.println("try spawn failed Q full");
            return false;
        }
        rc.buildRobot(robotType, bestSpawn);
        return true;
    }
}
