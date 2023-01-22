package submit17;

import battlecode.common.*;
import submit17.util.FastIterableRobotInfoSet;

import java.util.AbstractMap;

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
    static RobotType attackTargetType;
    static RobotInfo closestEnemy = null;
    static int ourTeamStrength = 1;
    // macro vars
    static RobotInfo masterLauncher = null;
    static int dis = 0;
    //    static int friendlyLauncherCnt = 1;
    static RobotInfo furthestFriendlyLauncher = null;

    static FastIterableRobotInfoSet friendlyLaunchers = new FastIterableRobotInfoSet();
    static FastIterableRobotInfoSet enemyLaunchers = new FastIterableRobotInfoSet();

    static Direction cachedDirection = null;

    static void sense() throws GameActionException {
        attackTarget = null;
        closestEnemy = null;
        ourTeamStrength = 1;
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
                    if (friendlyLaunchers.size < 8)
                        friendlyLaunchers.add(robot);
                    ourTeamStrength += 1;
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
                } else if (robot.type == RobotType.LAUNCHER) {
                    enemyLaunchers.add(robot);
                    ourTeamStrength -= 1;
                } else {
                    int health = (int) Math.ceil((double) robot.health / 30);
                    if (attackTargetHealth > health) {
                        attackTarget = robot.location;
                        attackTargetHealth = health;
                        attackTargetType = robot.type;
                    } else if (attackTargetHealth == health && attackTarget.distanceSquaredTo(rc.getLocation())
                            > robot.location.distanceSquaredTo(rc.getLocation())) {
                        attackTarget = robot.location;
                        attackTargetHealth = health;
                        attackTargetType = robot.type;
                    }
                }
//                attackTarget = targetPriority(attackTarget, robot);
                if (closestEnemy == null || rc.getLocation().distanceSquaredTo(closestEnemy.location) >
                        rc.getLocation().distanceSquaredTo(robot.location)) {
                    closestEnemy = robot;
                }
            }
        }
        friendlyLaunchers.updateIterable();
        enemyLaunchers.updateIterable();
        if (ourTeamStrength > 8 && enemyLaunchers.locs[0] != null) {
            attackTargetType = RobotType.LAUNCHER;
            attackTarget = enemyLaunchers.locs[0];
            attackTargetHealth = (int) Math.ceil( (double) MAX_HEALTH / (ourTeamStrength));
        } else if (enemyLaunchers.locs[0] != null){
            attackTargetType = RobotType.LAUNCHER;
            attackTarget = targetPriority(friendlyLaunchers, enemyLaunchers).getKey();
            attackTargetHealth = targetPriority(friendlyLaunchers, enemyLaunchers).getValue();
        }
        indicator += String.format("Strength%d", ourTeamStrength);
    }

    static void micro() throws GameActionException {
        if (attackTarget != null) {
            if (attackTarget != null && rc.canAttack(attackTarget)) {
                rc.attack(attackTarget);
            }
            if (rc.isMovementReady()) {
                // move toward enemy if sensed an enemy outside attack range
                if (rc.isActionReady() && ourTeamStrength >= 0) {
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
                    if (closestEnemy != null) {
                        if (ourTeamStrength < -1 || rc.getHealth() < closestEnemy.health){
                            indicator += String.format("run%d", closestEnemy.health-rc.getHealth());
                            kite(closestEnemy.location, 0);
                        }
                        else if (rc.getHealth() == closestEnemy.health && !rc.isActionReady())
                            kite(attackTarget, 1);
                    } else if (ourTeamStrength == 0 && attackTargetType == RobotType.LAUNCHER) {
                        // if I can back off to a location that I can still attack from, kite back
                        kite(attackTarget, 2);
                    } else if (ourTeamStrength == -1 && attackTargetType == RobotType.LAUNCHER
                            && attackTargetHealth > 2) {
                        // if I can back off to a location that I can still attack from, kite back
                        kite(attackTarget, 1);
                    }
                }
            }
        } else {
            if (rc.isMovementReady() && cachedDirection != null) {
                if ( rc.canMove(cachedDirection)) {
                    rc.move(cachedDirection);
                    indicator += String.format("CacheDir %s", cachedDirection);
                }
                cachedDirection = null;
            }
        }
    }

    private static void kite(MapLocation loc, int extraChecks) throws GameActionException {
        Direction backDir = rc.getLocation().directionTo(loc).opposite();
        Direction[] dirs = {backDir, backDir.rotateLeft(), backDir.rotateRight(),
                backDir.rotateLeft().rotateLeft(), backDir.rotateRight().rotateRight()};
        Direction result = null;
        for (Direction dir : dirs) {
            if (rc.canMove(dir) && (extraChecks<=1
                    || (extraChecks == 2 && rc.getLocation().add(dir).distanceSquaredTo(attackTarget) <= Constants.LAUNCHER_ATTACK_DIS)
            )) {
                if (result == null) result = dir;
                if (rc.getLocation().add(result).distanceSquaredTo(loc) < rc.getLocation().add(dir).distanceSquaredTo(loc)) {
                    result = dir;
                }
            }
        }
        if (result != null){
            rc.move(result);
            indicator += String.format("Kite%s", result);
            if (extraChecks == 1) cachedDirection = result.opposite();
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
        if (rc.isMovementReady() && attackTarget == null) { // macro
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
        if (rc.isActionReady() || rc.isMovementReady()){
            sense();
            micro();
        }
    }

    private static AbstractMap.SimpleEntry<MapLocation,Integer> targetPriority(FastIterableRobotInfoSet friendlyLaunchers, FastIterableRobotInfoSet enemyLaunchers) {
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
                if (minHitRatio > hitRatio){
                    targetLoc = enemyLoc;
//                    maxCanAttackFriends = canAttackFriends;
                } else if (minHitRatio == hitRatio){
                    targetLoc = rc.getLocation().distanceSquaredTo(targetLoc) <
                            rc.getLocation().distanceSquaredTo(enemyLoc)? targetLoc : enemyLoc;
//                    maxCanAttackFriends = canAttackFriends;
                }
            }
        }
//        indicator += String.format("pos%s", rc.getLocation());
//        indicator += String.format("target%s ", target.getID());
//        rc.setIndicatorLine(rc.getLocation(), target.location, 0, 0 , 0);
//        assert (!yes || (yes && target.type == RobotType.LAUNCHER)): String.format("targettype%s", target.type);
        return new AbstractMap.SimpleEntry<>(targetLoc, minHitRatio);
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

