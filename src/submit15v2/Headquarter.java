package submit15v2;

import battlecode.common.*;

public class Headquarter extends Unit {
    private static int carrierCnt = 0;

    public static void run() throws GameActionException {
        if (turnCount == 0) {
            // first turn all HQ report
            Comm.HQInit(rc.getLocation(), rc.getID());
            for (WellInfo well : rc.senseNearbyWells()) {
                Comm.reportWells(well.getResourceType().resourceID, well.getMapLocation());
            }
            if (Comm.friendlyHQLocations[0].equals(rc.getLocation())) {
                trySpawn(RobotType.CARRIER, rc.getLocation(), Carrier.SCOUT_SYMMETRY);
            }
        }

        if (!Comm.isSymmetryConfirmed && turnCount > 0 && turnCount % 150 == 0) {
            System.out.println("too long no sym, send another sym scout");
            trySpawn(RobotType.CARRIER, rc.getLocation(), Carrier.SCOUT_SYMMETRY);
        }

        RobotInfo closestEnemy = null;
        int dis = Integer.MAX_VALUE;
        int enemyCount = 0;
        for (RobotInfo robot : rc.senseNearbyRobots(-1, oppTeam)) {
            if (robot.type == RobotType.LAUNCHER) {
                enemyCount++;
                int newDis = rc.getLocation().distanceSquaredTo(robot.location);
                if (newDis < dis) {
                    closestEnemy = robot;
                    dis = newDis;
                }
            }
        }
        if (closestEnemy != null) {
            Comm.reportEnemy(closestEnemy.location, rc.getRoundNum());
        }
        indicator += String.format("enemy %s round %d", Comm.getEnemyLoc(), Comm.getEnemyRound());

        if (rc.getRobotCount() >= mapWidth * mapHeight * 0.15) {
            // this means we have basically won already, build anchor
            if (rc.canBuildAnchor(Anchor.STANDARD)) {
                rc.buildAnchor(Anchor.STANDARD);
            }
            indicator += "anchoring";
        } else {
            int maxLauncherSpawn = Math.max(5, rc.getResourceAmount(ResourceType.MANA) / Constants.LAUNCHER_COST_MN);
            if (maxLauncherSpawn > enemyCount) {
                // only spawn launcher if can spawn more than enemies close by, or just save mana for tiebreaker lol
                for (int i = 5; --i >= 0
                        && rc.isActionReady()
                        && rc.getResourceAmount(ResourceType.MANA) >= Constants.LAUNCHER_COST_MN;) {
                    Direction dirToCenter = rc.getLocation().directionTo(new MapLocation(mapWidth / 2, mapHeight / 2));
                    MapLocation spawnLocation = rc.getLocation().add(dirToCenter).add(dirToCenter);
                    trySpawn(RobotType.LAUNCHER, spawnLocation, -1);
                }
            }
            if (closestEnemy == null) {
                // do not spawn miner if enemies are close as miners getting killed will mess up spanw Q
                for (int i = 5; --i >= 0
                        && rc.isActionReady()
                        && rc.getResourceAmount(ResourceType.ADAMANTIUM) >= Constants.CARRIER_COST_AD;) {
                    trySpawn(RobotType.CARRIER, rc.getLocation(), ++carrierCnt % 3 == 0? Carrier.MINE_AD : Carrier.MINE_MN);
                }
            }
        }

        if (turnCount <= 3) { // for symmetry check
            MapRecorder.recordSym(2000);
        }
        Comm.commit_write();
    }

    // if no need to set spawn flag pass -1
    private static boolean trySpawn(RobotType robotType, MapLocation center, int spawnFlag) throws GameActionException {
        for (int[] dir : BFS25) {
            MapLocation location = new MapLocation(center.x + dir[0], center.y + dir[1]);
            if(rc.canBuildRobot(robotType, location)
                    && rc.senseMapInfo(location).getCurrentDirection() == Direction.CENTER) {
                if (spawnFlag > 0 && !Comm.trySetSpawnFlag(location, spawnFlag)) {
                    System.out.println("try spawn failed Q full");
                    return false;
                }
                rc.buildRobot(robotType, location);
                return true;
            }
        }
        return false;
    }
}
