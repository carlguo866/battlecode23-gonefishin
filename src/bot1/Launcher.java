package bot1;

import battlecode.common.*;

public class Launcher extends Unit {
    private static int enemyHQID = 0;
    private static MapLocation enemyHQLoc = null;

    static int clearedUntilRound = 0; // to mark if enemy has been cleared

    // micro vars
    static RobotInfo attackTarget = null;
    static RobotInfo closestEnemy = null;
    static int ourTeamStrength = 2;
    // macro vars
    static RobotInfo masterLauncher = null;
    static int dis = 0;
    static int friendlyLauncherCnt = 1;
    static RobotInfo furthestFriendlyLauncher = null;

    static void sense() throws GameActionException {
        // micro vars
        attackTarget = null;
        closestEnemy = null;
        ourTeamStrength = 2;
        // macro vars
        masterLauncher = null;
        dis = 0;
        friendlyLauncherCnt = 1;
        furthestFriendlyLauncher = null;
        for (RobotInfo robot : rc.senseNearbyRobots()) {
            if (robot.team == myTeam) {
                if (robot.type == RobotType.LAUNCHER) {
                    ourTeamStrength += 2;
                    friendlyLauncherCnt += 1;
                    masterLauncher = getMasterLauncher(masterLauncher, robot);
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
    }

    static void micro() throws GameActionException {
        if (closestEnemy != null) {
            if (attackTarget != null && rc.canAttack(attackTarget.location)) {
                rc.attack(attackTarget.location);
            }
            if (rc.isMovementReady()) {
                // move toward enemy if sensed an enemy outside attack range
                if (rc.isActionReady()) {
                    Direction forwardDir = rc.getLocation().directionTo(closestEnemy.location);
                    Direction[] dirs = {forwardDir, forwardDir.rotateLeft(), forwardDir.rotateRight(),
                            forwardDir.rotateLeft().rotateLeft(), forwardDir.rotateRight().rotateRight()};
                    for (Direction dir : dirs) {
                        if (rc.getLocation().add(dir).distanceSquaredTo(closestEnemy.location) <= Constants.LAUNCHER_ATTACK_DIS
                                && rc.canMove(dir)) {
                            rc.move(dir);
                            if (rc.canAttack(closestEnemy.location)) {
                                rc.attack(closestEnemy.location);
                            }
                        }
                    }
                } else {
                    // if at disadvantage pull back
                    if (ourTeamStrength < 0 || rc.getHealth() <= 12) {
                        Direction backDir = rc.getLocation().directionTo(closestEnemy.location).opposite();
                        Direction[] dirs = {backDir, backDir.rotateLeft(), backDir.rotateRight(),
                                backDir.rotateLeft().rotateLeft(), backDir.rotateRight().rotateRight()};
                        for (Direction dir : dirs) {
                            if (rc.canMove(dir)) {
                                rc.move(dir);
                                break;
                            }
                        }
                    } else {
                        // if I can back off to a location that I can still attack from, kite back
                        Direction backDir = rc.getLocation().directionTo(closestEnemy.location).opposite();
                        Direction[] dirs = {backDir, backDir.rotateLeft(), backDir.rotateRight(),
                                backDir.rotateLeft().rotateLeft(), backDir.rotateRight().rotateRight()};
                        for (Direction dir : dirs) {
                            if (rc.getLocation().add(dir).distanceSquaredTo(closestEnemy.location) <= Constants.LAUNCHER_ATTACK_DIS
                                    && rc.canMove(dir)) {
                                rc.move(dir);
                                break;
                            }
                        }
                    }
                }
            }
            indicator += String.format("Micro strength %d target", ourTeamStrength);
        }
    }

    static void run () throws GameActionException {
        if (turnCount == 0) {
            // future: get from spawn queue if there are more than one roles
            // prioritize the closest enemy HQ
            enemyHQID = getClosestID(Comm.enemyHQLocations);
            enemyHQLoc = Comm.enemyHQLocations[enemyHQID];
        }
        if (rc.getRoundNum() <= 6) {
            // first two rounds just wait for the other two to join and move together
            return;
        }

        sense();
        micro();
        if (closestEnemy == null) { // macro
            // If enemy reported recently that is close
            MapLocation enemyLocation = Comm.getEnemyLoc();
            if (enemyLocation != null
                    && rc.getRoundNum() - Comm.getEnemyRound() <= 50
                    && Comm.getEnemyRound() > clearedUntilRound
                    && rc.getLocation().distanceSquaredTo(enemyLocation) <= 64) {
                if (rc.getLocation().distanceSquaredTo(enemyLocation) <= 4) {
                    // enemy has been cleared
                    clearedUntilRound = rc.getRoundNum();
                } else {
                    moveToward(enemyLocation);
                    indicator += String.format("M2E@%s", enemyLocation);
                }
            } else {
                // if I am next to enemy HQ and hasn't seen anything, go to the next HQ
                if (rc.getLocation().distanceSquaredTo(enemyHQLoc) <= 4) {
                    for (int i = enemyHQID + 1; i <= enemyHQID + 4; i++) {
                        if (Comm.enemyHQLocations[i % 4] != null) {
                            enemyHQID = i % 4;
                            break;
                        }
                    }
                }
                enemyHQLoc = Comm.enemyHQLocations[enemyHQID]; // in case symmetry changes...
                indicator += String.format("M2EHQ@%s", enemyHQLoc);
                moveToward(enemyHQLoc);
            }
        }
        sense();
        micro();
    }

    private static RobotInfo targetPriority(RobotInfo r1, RobotInfo r2) {
        if (!rc.canActLocation(r2.location))
            return r1;
        if (r1 == null)
            return r2;
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

    private static RobotInfo getMasterLauncher(RobotInfo r1, RobotInfo r2) {
        // self may be master
        if (r2.health < rc.getHealth() || (r2.health == rc.getHealth() && r2.getID() > rc.getID()))
            return r1;
        if (r1 == null)
            return r2;
        if (r1.health != r2.health)
            return r1.health < r2.health? r2 : r1;
        return r1.getID() < r2.getID()? r1 : r2;
    }
}

