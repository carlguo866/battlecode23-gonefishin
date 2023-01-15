package rushbot;

import battlecode.common.*;

public class Launcher extends Unit {
    private static final int ATTACK_DIS = 16;
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

        // micro vars
        RobotInfo attackTarget = null;
        RobotInfo closestEnemy = null;
        int ourTeamStrength = 2;
        // macro vars
        RobotInfo masterLauncher = null;
        int dis = 0;
        int friendlyLauncherCnt = 1;
        RobotInfo furthestFriendlyLauncher = null;

        for (RobotInfo robot : rc.senseNearbyRobots()) {
            if (robot.team == myTeam) {
                if (robot.type == RobotType.LAUNCHER) {
                    ourTeamStrength += 2;
                    friendlyLauncherCnt += 1;
                    if (robot.getID() < rc.getID() &&
                            (masterLauncher == null  || robot.getID() < masterLauncher.getID())) {
                        masterLauncher = robot;
                    }
                    if (furthestFriendlyLauncher == null) {
                        int newDis = robot.getLocation().distanceSquaredTo(rc.getLocation());
                        if (newDis > dis) {
                            dis = newDis;
                            furthestFriendlyLauncher = robot;
                        }
                    }
                }
            } else {
                if (robot.type == RobotType.HEADQUARTERS) {
                    // disregard enemy HQ
                    continue;
                } else if (robot.type == RobotType.CARRIER) {
                    ourTeamStrength -= 1;
                } else if (robot.type == RobotType.LAUNCHER) {
                    ourTeamStrength -= 2;
                } else if (robot.type == RobotType.DESTABILIZER) {
                    ourTeamStrength -= 3;
                }
                attackTarget = targetPriority(attackTarget, robot);
                if (closestEnemy == null || rc.getLocation().distanceSquaredTo(closestEnemy.location) >
                        rc.getLocation().distanceSquaredTo(robot.location)) {
                    closestEnemy = robot;
                }
            }
        }
        // micro if an enemy is sensed
        if (closestEnemy != null) {
            if (attackTarget != null && rc.canAttack(attackTarget.location)) {
                rc.attack(attackTarget.location);
            }
            if (rc.isMovementReady()) {
                // move toward enemy if sensed an enemy outside attack range
                if (rc.isActionReady()) {
                    Direction forwardDir = rc.getLocation().directionTo(closestEnemy.location).opposite();
                    Direction[] dirs = {forwardDir, forwardDir.rotateLeft(), forwardDir.rotateRight(),
                            forwardDir.rotateLeft().rotateLeft(), forwardDir.rotateRight().rotateRight()};
                    for (Direction dir : dirs) {
                        if (rc.getLocation().add(dir).distanceSquaredTo(closestEnemy.location) <= ATTACK_DIS
                                && rc.canMove(dir)) {
                            rc.move(dir);
                            if (rc.canAttack(closestEnemy.location)) {
                                rc.attack(closestEnemy.location);
                            }
                        }
                    }
                } else {
                    // if I can back off to a location that I can still attack from, kite back
                    Direction backDir = rc.getLocation().directionTo(closestEnemy.location).opposite();
                    Direction[] dirs = {backDir, backDir.rotateLeft(), backDir.rotateRight(),
                            backDir.rotateLeft().rotateLeft(), backDir.rotateRight().rotateRight()};
                    for (Direction dir : dirs) {
                        if (rc.getLocation().add(dir).distanceSquaredTo(closestEnemy.location) <= ATTACK_DIS
                                && rc.canMove(dir)) {
                            rc.move(dir);
                            break;
                        }
                    }
                }
            }
            indicator += String.format("Micro strength %d target %s", ourTeamStrength, attackTarget.location);
        } else { // macro
            // the launcher with the smallest ID is the master, everyone follows him
            if (masterLauncher != null) {
                indicator += String.format("Following master %d at %s", masterLauncher.getID(), masterLauncher.location);
                follow(masterLauncher.location);
            } else { // I am the master
                // if there is a launcher going far away while there are few launchers,
                // most likely it has seen something, follow him
                if (dis >= 9 && friendlyLauncherCnt <= 3) {
                    indicator += String.format("Master following %s", furthestFriendlyLauncher.location);
                    follow(furthestFriendlyLauncher.location);
                } else {
                    // if I am next to enemy HQ and hasn't seen anything, go to the next HQ
                    if (rc.getLocation().distanceSquaredTo(enemyHQLoc) <= 4) {
                        for (int i = enemyHQID + 1; i <= enemyHQID + 4; i++) {
                            if (Comm.enemyHQLocations[i % 4] != null) {
                                enemyHQID = i % 4;
                                enemyHQLoc = Comm.enemyHQLocations[i % 4];
                                break;
                            }
                        }
                    }
                    indicator += String.format("M2E@%s", enemyHQLoc);
                    moveToward(enemyHQLoc);
                }
            }
        }
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

