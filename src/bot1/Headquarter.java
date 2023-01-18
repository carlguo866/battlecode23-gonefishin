package bot1;

import battlecode.common.*;

public class Headquarter extends Unit {
    public static void run() throws GameActionException {
        if (turnCount == 0) {
            // first turn all HQ report
            Comm.HQInit(rc.getLocation(), rc.getID());
            for (WellInfo well : rc.senseNearbyWells()) {
                Comm.reportWells(well);
            }
        }

        if (turnCount <= 3) {
            Direction[] SPAWN_DIR = {Direction.SOUTHEAST, Direction.SOUTHWEST, Direction.NORTHEAST, Direction.NORTHWEST};
            int[] PURPURSES = {Carrier.SCOUT_SE, Carrier.SCOUT_SW, Carrier.SCOUT_NE, Carrier.SCOUT_NW};
            MapLocation spawnLocation = rc.getLocation().add(SPAWN_DIR[turnCount]).add(SPAWN_DIR[turnCount]);
                for (int[] dir : BFS25) {
                    MapLocation location = new MapLocation(spawnLocation.x + dir[0], spawnLocation.y + dir[1]);
                    if(rc.canBuildRobot(RobotType.CARRIER, location)
                            && rc.senseMapInfo(location).getCurrentDirection() == Direction.CENTER) {
                        rc.buildRobot(RobotType.CARRIER, location);
                        Comm.setSpawnQ(rc.getID() / 2 * 2 + (rc.getRoundNum() % 2),
                                location.x, location.y, PURPURSES[turnCount]);
                        break;
                    }
                }
            return; // TODO fix this to allow multiple spawn per turn
        }
//        indicator += String.format("AD %s %s %sMN %s %s %s", Comm.closestWells[1][0],Comm.closestWells[1][1],Comm.closestWells[1][2], Comm.closestWells[2][0], Comm.closestWells[2][1], Comm.closestWells[2][2]);

        if (rc.getRobotCount() >= rc.getMapWidth() * rc.getMapHeight() * 0.15) {
            // this means we have basically won already, build anchor
            if (rc.canBuildAnchor(Anchor.STANDARD)) {
                rc.buildAnchor(Anchor.STANDARD);
            }
            indicator += "anchoring";
        } else {
            // spawn Launchers
            if (rc.getResourceAmount(ResourceType.MANA) >= Constants.LAUNCHER_COST_MN) { // can build launcher
                Direction dirToCenter = rc.getLocation().directionTo(new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2));
                MapLocation spawnLocation = rc.getLocation().add(dirToCenter).add(dirToCenter);
                for (int[] dir : BFS25) {
                    MapLocation location = new MapLocation(spawnLocation.x + dir[0], spawnLocation.y + dir[1]);
                    if(rc.canBuildRobot(RobotType.LAUNCHER, location)
                            && rc.senseMapInfo(location).getCurrentDirection() == Direction.CENTER) {
                        rc.buildRobot(RobotType.LAUNCHER, location);
                        Comm.setSpawnQ(rc.getID() / 2 * 2 + (rc.getRoundNum() % 2), location.x, location.y, 0);
                        break;
                    }
                }
            }
            // spawn carrier if possible
            if (turnCount >= 1 && rc.getResourceAmount(ResourceType.ADAMANTIUM) >= Constants.CARRIER_COST_AD) {
                for (int[] dir : BFS25) {
                    MapLocation location = new MapLocation(rc.getLocation().x + dir[0], rc.getLocation().y + dir[1]);
                    if(rc.canBuildRobot(RobotType.CARRIER, location)
                            && rc.senseMapInfo(location).getCurrentDirection() == Direction.CENTER) {
                        rc.buildRobot(RobotType.CARRIER, location);
                        Comm.setSpawnQ(rc.getID() / 2 * 2 + (rc.getRoundNum() % 2), location.x, location.y, Carrier.GO_MINE);
                    }
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
