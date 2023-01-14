package bot1;

import battlecode.common.*;
import static bot1.Constants.*;

public class Launcher extends Unit {
    private static int enemyHQID = 0;
    private static MapLocation enemyHQLoc = null;

    static void run () throws GameActionException {
        if (turnCount == 0) {
            // reset the spawn flag
            for (int i = 0; i < Comm.SPAWN_Q_LENGTH; i++) {
                MapLocation location = Comm.getSpawnQLoc(i);
                if (location != null && location.compareTo(rc.getLocation()) == 0) {
                    int purpose = Comm.getSpawnQFlag(i);
                    Comm.setSpawnQ(i, -1, -1, 0);
                    Comm.commit_write(); // write immediately instead of at turn ends in case we move out of range
                }
            }
            // prioritize the closest enemy HQ
            enemyHQID = getClosestID(Comm.enemyHQLocations);
            enemyHQLoc = Comm.enemyHQLocations[enemyHQID];
        }
        if (rc.getRoundNum() <= 3) {
            // first two rounds just wait for the other two
            return;
        }
        String indicator = "";

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, oppTeam);
        RobotInfo[] friends = rc.senseNearbyRobots(-1, myTeam);
        // if enemies present, use micro strat
        if (enemies.length > 0) {
            RobotInfo attackTarget = null;
            RobotInfo closestEnemy = null;
            int ourTeamStrength = 2;
            for (RobotInfo robot : enemies) {
                if (robot.type == RobotType.CARRIER)
                    ourTeamStrength -= 1;
                if (robot.type == RobotType.LAUNCHER)
                    ourTeamStrength -= 2;
                else if (robot.type == RobotType.DESTABILIZER) {
                    ourTeamStrength -= 3;
                }

                attackTarget = targetPriority(attackTarget, robot);
                if (closestEnemy == null || rc.getLocation().distanceSquaredTo(closestEnemy.location) >
                        rc.getLocation().distanceSquaredTo(robot.location)) {
                    closestEnemy = robot;
                }
            }
            for (RobotInfo robot : friends) {
                if (robot.type == RobotType.LAUNCHER)
                    ourTeamStrength += 2;
            }

            if (attackTarget != null) {
                if (rc.canAttack(attackTarget.location)) {
                    rc.attack(attackTarget.location);
                }
            }

            // move toward enemy if favored, ow go away
            if (ourTeamStrength >= 0) {
                moveToward(closestEnemy.location);
            } else {
                moveToward(rc.getLocation().add(rc.getLocation().directionTo(closestEnemy.location).opposite()));
            }
            indicator = String.format("Micro strength %d target %s", ourTeamStrength, attackTarget.location);
        }

        // no enemies present, macro strat
        RobotInfo masterLauncher = null;
        int dis = 0;
        RobotInfo furthestLauncher = null;
        for (RobotInfo robot : friends) {
            if (robot.type != RobotType.LAUNCHER)
                continue;
            if (robot.getID() < rc.getID() &&
                    (masterLauncher == null  || robot.getID() < masterLauncher.getID())) {
                masterLauncher = robot;
            }
            if (furthestLauncher == null) {
                int newDis = robot.getLocation().distanceSquaredTo(rc.getLocation());
                if (newDis > dis) {
                    dis = newDis;
                    furthestLauncher = robot;
                }
            }
        }
        // the launcher with the smallest ID is the master, everyone follows him
        if (masterLauncher != null) {
            indicator = String.format("Following master %d at %s", masterLauncher.getID(), masterLauncher.location);
            moveToward(masterLauncher.location);
        } else { // I am the master
            // if there is a launcher going far away, most likely it has seen something, follow him
            if (dis >= 9) {
                indicator = String.format("Master following %s", furthestLauncher.location);
                moveToward(furthestLauncher.location);
            } else {
                // if I am next to enemy HQ and hasn't seen anything, go to the next HQ
                if (rc.getLocation().distanceSquaredTo(enemyHQLoc) <= 4) {
                    for (int i = enemyHQID + 1; i <= enemyHQID + 4; i++) {
                        if (Comm.enemyHQLocations[i % 4] != null) {
                            enemyHQID = i % 4;
                            enemyHQLoc = Comm.enemyHQLocations[i % 4];
                        }
                    }
                }
                indicator = String.format("Master going to enemy at %s", enemyHQLoc);
                moveToward(enemyHQLoc);
            }
        }

        rc.setIndicatorString(indicator);
    }

    private static RobotInfo targetPriority(RobotInfo r1, RobotInfo r2) {
        if (r1 == null)
            return r2;
        if (!rc.canActLocation(r2.location))
            return r1;
        // Prioritize Launcher
        if (r2.type == RobotType.LAUNCHER && r1.type != RobotType.LAUNCHER)
            return r2;
        if (r1.type == RobotType.LAUNCHER && r2.type != RobotType.LAUNCHER)
            return r1;
        // Prioritize lower HP
        if (r1.health != r2.health)
            return r1.health < r2.health? r1 : r2;
        // Prioritize closer unit, ow it doesn't matter
        int disR1 = r1.location.distanceSquaredTo(rc.getLocation());
        int disR2 = r2.location.distanceSquaredTo(rc.getLocation());
        return disR1 < disR2? r1 : r2;
    }
}

