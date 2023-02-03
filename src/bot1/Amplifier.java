package bot1;

import battlecode.common.*;

public class Amplifier extends Unit {
    public static int ampid = 4;

    private static int strength;
    private static MapLocation closestEnemy;
    private static MapLocation centerOfFriend;
    private static MapLocation currentLoc;
    private static int enemyHQID = 0;
    private static MapLocation enemyHQLoc = null;

    public static void run() throws GameActionException {
        if (turnCount == 0) {
            enemyHQID = getClosestID(Comm.enemyHQLocations);
            enemyHQLoc = Comm.enemyHQLocations[enemyHQID];
            // find the closest HQ that doesn't have a amp
            for (int i = Comm.numHQ; --i >= 0;) {
                if (!Comm.isAmpAlive(i) && (ampid == 4
                        || Comm.friendlyHQLocations[i].distanceSquaredTo(rc.getLocation())
                        < Comm.friendlyHQLocations[ampid].distanceSquaredTo(rc.getLocation()))) {
                    ampid = i;
                }
            }
            if (ampid == 4) {
                System.out.println("bug: all amp id taken");
                ampid = 0;
            }
        }

        sense();
        MapLocation commEnemy = Comm.updateEnemy();
        if (closestEnemy == null)
            closestEnemy = commEnemy;
        indicator += String.format("amp%d,strength%d,", ampid, strength);

        if (rc.isMovementReady()) {
            if (closestEnemy == null && centerOfFriend == null) {
                if (rc.getLocation().distanceSquaredTo(enemyHQLoc) <= 16) {
                    for (int i = enemyHQID + 1; i <= enemyHQID + 4; i++) {
                        if (Comm.enemyHQLocations[i % 4] != null) {
                            enemyHQID = i % 4;
                            break;
                        }
                    }
                }
                moveToward(enemyHQLoc);
            } else {
                Direction bestMoveDir = null;
                int bestScore = getScore(currentLoc);
                for (int i = 8; --i >= 0;) {
                    Direction dir = Constants.directions[i];
                    if (!rc.canMove(dir)) {
                        continue;
                    }
                    int score = getScore(currentLoc.add(dir));
                    if (score > bestScore) {
                        bestScore = score;
                        bestMoveDir = dir;
                    }
                }
                if (bestMoveDir != null) {
                    rc.move(bestMoveDir);
                    currentLoc = rc.getLocation();
                }
            }
        }

        int [] islandIndexes = rc.senseNearbyIslands();
        for (int i = islandIndexes.length; --i >=0; ) {
            int islandIndex = islandIndexes[i];
            MapLocation islandLoc = Comm.getIslandLocation(islandIndex);
            if (islandLoc == null) {
                islandLoc = getClosestLoc(rc.senseNearbyIslandLocations(islandIndex));
            }
            Team occupyingTeam = rc.senseTeamOccupyingIsland(islandIndex);
            int islandState = Comm.ISLAND_NEUTRAL;
            if (occupyingTeam == myTeam) {
                islandState = Comm.ISLAND_FRIENDLY;
            } else if (occupyingTeam == oppTeam) {
                islandState = Comm.ISLAND_ENEMY;
            }
            Comm.reportIsland(islandLoc, islandIndex, islandState);
        }
        Comm.amplifierUpdate(ampid);
        Comm.commit_write();
        MapRecorder.recordSym(500);
    }

    private static int getScore(MapLocation loc) {
        int score = 0;
        if (closestEnemy != null) {
            int dis2e = loc.distanceSquaredTo(closestEnemy);
            score += dis2e;
            if (dis2e <= 16) {
                score -= 1000;
            } else if (dis2e <= 20) {
                score -= 100;
            }
        }
        if (centerOfFriend != null) {
            score -= loc.distanceSquaredTo(centerOfFriend);
        }
        return score;
    }

    private static void sense() {
        currentLoc = rc.getLocation();
        strength = 0;
        closestEnemy = null;
        centerOfFriend = null;
        int x = 0, y = 0, cnt = 0;
        RobotInfo[] robots = rc.senseNearbyRobots();
        for (int i = robots.length; --i >= 0;) {
            RobotInfo robot = robots[i];
            if (robot.team == oppTeam) {
                if (robot.type == RobotType.LAUNCHER) {
                    strength -= robot.health;
                    if (closestEnemy == null ||
                            robot.location.distanceSquaredTo(currentLoc) < closestEnemy.distanceSquaredTo(currentLoc)) {
                        closestEnemy = robot.location;
                    }
                } else if (robot.type == RobotType.HEADQUARTERS) {
                    MapRecorder.reportEnemyHQ(robot.location);
                }
            } else {
                if (robot.type == RobotType.LAUNCHER) {
                    strength += robot.health;
                    x += robot.location.x;
                    y += robot.location.y;
                    cnt++;
                }
            }
        }
        centerOfFriend = cnt == 0? null : new MapLocation(x / cnt, y / cnt);
    }
}
