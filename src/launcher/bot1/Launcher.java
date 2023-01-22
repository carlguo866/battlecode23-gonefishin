package launcher.bot1;

import battlecode.common.*;
import bot1.util.FastIterableRobotInfoSet;

import java.util.Arrays;

public class Launcher extends Unit {

    private static final int MAX_HEALTH = 200;
    private static final int DAMAGE = 30;
    private static final int ATTACK_DIS = 16;

    private static final int VISION_DIS = 20;
    
    private static int enemyHQID = 0;
    private static MapLocation enemyHQLoc = null;

    static int clearedUntilRound = 0; // to mark if enemy has been cleared

    // micro vars
    static MapLocation attackTarget = null;
    static int attackTargetHealth;
    static RobotInfo closestEnemy = null;
    static int ourTeamStrength = 2;
    // macro vars
    static RobotInfo masterLauncher = null;
    static int dis = 0;
    static int friendlyLauncherCnt = 1;
    static RobotInfo furthestFriendlyLauncher = null;

    static FastIterableRobotInfoSet friendlyLaunchers = new FastIterableRobotInfoSet();
    static FastIterableRobotInfoSet enemyLaunchers = new FastIterableRobotInfoSet();


    static void sense() throws GameActionException {
        indicator += String.format("sensing");
        attackTarget = null;
        closestEnemy = null;
//        ourTeamStrength = 2;
        // macro vars
        masterLauncher = null;
        dis = 0;
//        friendlyLauncherCnt = 1;
        furthestFriendlyLauncher = null;
        friendlyLaunchers.clear();
        enemyLaunchers.clear();
        attackTargetHealth = (int) Math.ceil((double) 250 / 30);
        for (RobotInfo robot : rc.senseNearbyRobots()) {
            if (robot.team == myTeam) {
                if (robot.type == RobotType.LAUNCHER) {
//                    ourTeamStrength += 2;
//                    friendlyLauncherCnt += 1;
                    friendlyLaunchers.add(robot);
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
                } else if (robot.type == RobotType.LAUNCHER){
                    enemyLaunchers.add(robot);
//
                } else {
                    int health = (int) Math.ceil((double) robot.health / 30);
                    if (attackTargetHealth > health) {
                        attackTarget = robot.location;
                        attackTargetHealth = health;
                    }
                }
//                } else if (robot.type == RobotType.CARRIER) {
//                    ourTeamStrength -= 1;
//                } else if (robot.type == RobotType.LAUNCHER) {
//                    ourTeamStrength -= 2;
//                } else if (robot.type == RobotType.DESTABILIZER) {
//                    ourTeamStrength -= 3;
//                }
//                attackTarget = targetPriority(attackTarget, robot);
                if (closestEnemy == null || rc.getLocation().distanceSquaredTo(closestEnemy.location) >
                        rc.getLocation().distanceSquaredTo(robot.location)) {
                    closestEnemy = robot;
                }
            }
        }
        friendlyLaunchers.updateIterable();
        enemyLaunchers.updateIterable();
//        System.out.println(Arrays.deepToString(enemyLaunchers.locs));
        if (enemyLaunchers.locs[0] != null){
            attackTarget = targetPriority(friendlyLaunchers, enemyLaunchers);
        }
    }

    static void micro() throws GameActionException {
        if (attackTarget != null) {
            if (attackTarget != null && rc.canAttack(attackTarget)) {
                rc.attack(attackTarget);
            }
            if (rc.isMovementReady()) {
                // move toward enemy if sensed an enemy outside attack range
                if (rc.isActionReady()) {
                    Direction forwardDir = rc.getLocation().directionTo(attackTarget);
                    Direction[] dirs = {forwardDir, forwardDir.rotateLeft(), forwardDir.rotateRight(),
                            forwardDir.rotateLeft().rotateLeft(), forwardDir.rotateRight().rotateRight()};
                    for (Direction dir : dirs) {
                        if (rc.getLocation().add(dir).distanceSquaredTo(attackTarget) <= Constants.LAUNCHER_ATTACK_DIS
                                && rc.canMove(dir)) {
                            rc.move(dir);
                            if (rc.canAttack(attackTarget)) {
                                rc.attack(attackTarget);
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
        sense();
        micro();
        if (attackTarget == null) { // macro
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

    private static MapLocation targetPriority(FastIterableRobotInfoSet friendlyLaunchers, FastIterableRobotInfoSet enemyLaunchers) {
        MapLocation targetLoc = null;
        int minHitRatio = 10;
        int maxCanAttackFriends = 1;
        boolean yes = false;
        for (MapLocation enemyLoc: enemyLaunchers.locs){
            if (enemyLoc == null) break;
            int enemyHealth = enemyLaunchers.getHealth(enemyLoc);
            if (targetLoc == null) {
                targetLoc = enemyLoc;
            }
//            RobotType targetType = enemyLaunchers.getRobotType(targetLoc);
//            RobotType enemyType = enemyLaunchers.getRobotType(enemyLoc);

//            if (targetType != RobotType.LAUNCHER && enemyType == RobotType.LAUNCHER){
//                targetLoc = enemyLoc;
//            } else if (targetType == RobotType.LAUNCHER && enemyType != RobotType.LAUNCHER){
//                continue;
//            }

            int canAttackFriends = 1;
            for (MapLocation friendLoc: friendlyLaunchers.locs){
                if (friendLoc == null) break;
                if (friendLoc.distanceSquaredTo(targetLoc) <= VISION_DIS){
                    canAttackFriends+=1;
                }
            }
//            System.out.println(String.format("loc%s", enemy));
            int hitRatio = (int) Math.ceil((double) enemyHealth / (canAttackFriends));
            if (targetLoc == enemyLoc) {
                minHitRatio = hitRatio;
            } else {
                if (minHitRatio > (int) hitRatio){
                    targetLoc = enemyLoc;
                    maxCanAttackFriends = canAttackFriends;
                } else if (minHitRatio == canAttackFriends){
                    targetLoc = rc.getLocation().distanceSquaredTo(targetLoc) <
                            rc.getLocation().distanceSquaredTo(enemyLoc)? targetLoc : enemyLoc;
                    maxCanAttackFriends = canAttackFriends;
                }
            }
        }
//        indicator += String.format("pos%s", rc.getLocation());
        indicator += String.format("tgt%s", targetLoc);
        indicator += String.format("friends%s ", maxCanAttackFriends);
        indicator += String.format("ratio%s ", minHitRatio);
//        indicator += String.format("target%s ", target.getID());
//        rc.setIndicatorLine(rc.getLocation(), target.location, 0, 0 , 0);
//        assert (!yes || (yes && target.type == RobotType.LAUNCHER)): String.format("targettype%s", target.type);
        return targetLoc;
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

