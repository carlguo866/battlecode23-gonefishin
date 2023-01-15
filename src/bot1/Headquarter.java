package bot1;

import battlecode.common.*;
import static bot1.Constants.*;

public class Headquarter extends Unit {
    public static void run() throws GameActionException {
        if (turnCount == 0) {
            // first turn all HQ report
            Comm.HQInit(rc.getLocation(), rc.getID());
            for (WellInfo well : rc.senseNearbyWells()) {
                Comm.reportWells(well);
            }
        }

        if (rc.getRobotCount() >= rc.getMapWidth() * rc.getMapHeight() * 0.15) {
            // this means we have basically won already, build anchor
            if (rc.canBuildAnchor(Anchor.STANDARD)) {
                rc.buildAnchor(Anchor.STANDARD);
            }
            indicator += "anchoring";
        } else {
            // spawn Launchers
            if (rc.getResourceAmount(ResourceType.MANA) >= 60) { // can build launcher
                Direction dirToCenter = rc.getLocation().directionTo(new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2));
                MapLocation spawnLocation = rc.getLocation().add(dirToCenter).add(dirToCenter);
                for (int[] dir : BFS25) {
                    MapLocation location = new MapLocation(spawnLocation.x + dir[0], spawnLocation.y + dir[1]);
                    if(rc.canBuildRobot(RobotType.LAUNCHER, location)) {
                        rc.buildRobot(RobotType.LAUNCHER, location);
                        Comm.setSpawnQ(rc.getID() / 2, location.x, location.y, Carrier.MINE_MN);
                        break;
                    }
                }
            }
            // spawn carrier if MANA well is found
            if (turnCount >= 1 && Comm.closestWells[ResourceType.MANA.resourceID] != null) {
                Direction direction = rc.getLocation().directionTo(Comm.closestWells[ResourceType.MANA.resourceID]);
                MapLocation location = rc.getLocation().add(direction);
                if(rc.canBuildRobot(RobotType.CARRIER, location)) {
                    rc.buildRobot(RobotType.CARRIER, location);
                    Comm.setSpawnQ(rc.getID() / 2, location.x, location.y, Carrier.MINE_MN);
                }
            }
        }

        RobotInfo closestEnemy = null;
        int dis = Integer.MAX_VALUE;
        for (RobotInfo robot : rc.senseNearbyRobots(-1, oppTeam)) {
            if (robot.type == RobotType.LAUNCHER) {
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
    }
}
