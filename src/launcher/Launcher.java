package launcher;

import battlecode.common.*;
import launcher.util.FastIterableRobotInfoSet;
import launcher.util.SimpleLauncherInfo;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Random;

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

    static SimpleLauncherInfo cachedAttackTarget = null;
    static int cachedTurn = 0;
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
                    if (friendlyLaunchers.size < 6)
                        friendlyLaunchers.add(robot);
//                    if (robot.location.distanceSquaredTo(rc.getLocation()) <= 10){
                    ourTeamStrength += 1;
//                    }
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
            SimpleLauncherInfo info = targetPriority(friendlyLaunchers, enemyLaunchers);
            attackTarget = info.loc;
            attackTargetHealth = info.hitRatio;
        }
        if (attackTarget != null) {
            Direction backDir = rc.getLocation().directionTo(attackTarget).opposite();
            for (MapLocation friend: friendlyLaunchers.locs) {
                Direction friendDir = (rc.getLocation().directionTo(friend));
                if ((friendDir == backDir ||
                    friendDir == backDir.rotateLeft() ||
                    friendDir == backDir.rotateRight()) &&
                    rc.getLocation().distanceSquaredTo(friend) >= 10)
                    ourTeamStrength -=1;
            }
        }
        indicator += String.format("Strength%d", ourTeamStrength);
    }

    static void micro(boolean scdTime) throws GameActionException {
        if (attackTarget != null) {
            if (attackTarget != null && rc.canAttack(attackTarget)) {
                rc.attack(attackTarget);
            }
            if (rc.isMovementReady()) {
                // move toward enemy if sensed an enemy outside attack range
                if (rc.isActionReady() && (ourTeamStrength >= 0 ||
                    (ourTeamStrength==0 && rc.getHealth() > closestEnemy.getHealth()))) {
                    Direction forwardDir = rc.getLocation().directionTo(attackTarget);
                    Direction[] dirs = {forwardDir, forwardDir.rotateLeft(), forwardDir.rotateRight(),
                            forwardDir.rotateLeft().rotateLeft(), forwardDir.rotateRight().rotateRight()};
                    for (Direction dir : dirs) {
                        if (rc.getLocation().add(dir).distanceSquaredTo(attackTarget) <= Constants.LAUNCHER_ATTACK_DIS
                                && rc.canMove(dir) && ( rc.senseCloud(rc.getLocation()) ||
                                !rc.senseCloud(rc.getLocation().add(dir)))) {
                            rc.move(dir);
                            if (rc.canAttack(attackTarget)) {
                                rc.attack(attackTarget);
                            }
                            break;
                        }
                    }
                } else {
                    // if at disadvantage pull back
                    if (closestEnemy != null) {
                        if (ourTeamStrength < -1 || rc.getHealth() < closestEnemy.health){
                            indicator += String.format("run%d", closestEnemy.health-rc.getHealth());
                            kite(closestEnemy.location, 0);
                        } else if (rc.getHealth() == closestEnemy.health && !rc.isActionReady()) {
                            System.out.println("scenario1111111");
                            kite(closestEnemy.location, 1);
                        } else if (ourTeamStrength == -1) {
                            // if I can back off to a location that I can still attack from, kite back
                            kite(closestEnemy.location, 1);
                        }
//                        }  else if (ourTeamStrength == 0 && attackTargetType == RobotType.LAUNCHER) {
//                            System.out.println("scenario22222");
//                            // if I can back off to a location that I can still attack from, kite back
//                            kite(attackTarget, 2);
//                        }
//
                    }
                }
            }
            if (scdTime) {
                cachedAttackTarget = new SimpleLauncherInfo(attackTarget, attackTargetHealth-1, 0);
                cachedTurn = 0;
            }
        } else {
            if (rc.isMovementReady() && cachedDirection != null) {
                if (rc.canMove(cachedDirection)) {
                    rc.move(cachedDirection);
                    System.out.println(String.format("CacheDir %s", cachedDirection));
                }
                cachedDirection = null;
            }
//            if (rc.isMovementReady() && cachedAttackTarget != null) {
//                Direction dir = rc.getLocation().directionTo(cachedAttackTarget.loc);
//                if (rc.canMove(dir)) {
//                    rc.move(dir);
//                    System.out.println(String.format("CacheDir %s", dir));
//                }
//            }
            if (scdTime
                && cachedAttackTarget != null
                && rc.isActionReady()
                && rc.canAttack(cachedAttackTarget.loc)) {
                rc.attack(cachedAttackTarget.loc);
            }
            MapLocation[] clouds = rc.senseNearbyCloudLocations();
            if (scdTime && clouds.length != 0) {
                MapLocation randomCloudLoc = clouds[(int) (Math.random() * clouds.length)];
                if (rc.isActionReady() && rc.canAttack(randomCloudLoc)) {
                    rc.attack(randomCloudLoc);
                }
            }
        }
        if (scdTime) {
            cachedTurn += 1;
            if (cachedTurn > 2) cachedAttackTarget = null;
        }
    }

    private static void kite(MapLocation loc, int extraChecks) throws GameActionException {
        Direction backDir = rc.getLocation().directionTo(loc).opposite();
        Direction[] dirs = {backDir, backDir.rotateLeft(), backDir.rotateRight(),
                backDir.rotateLeft().rotateLeft(), backDir.rotateRight().rotateRight()};
        Direction result = null;
        for (Direction dir : dirs) {
            if (rc.canMove(dir) && (extraChecks<=1
                || (extraChecks == 2 && rc.getLocation().add(dir).distanceSquaredTo(attackTarget)
                    <= Constants.LAUNCHER_ATTACK_DIS))) {
                if (result == null) result = dir;
                if ((extraChecks<=1 && rc.getLocation().add(result).distanceSquaredTo(loc)
                        < rc.getLocation().add(dir).distanceSquaredTo(loc)) ||
                    (extraChecks==2 && rc.getLocation().add(result).distanceSquaredTo(loc)
                                > rc.getLocation().add(dir).distanceSquaredTo(loc))) {
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
        micro(false);
        indicator += String.format("actCool%d", rc.getActionCooldownTurns());
        indicator += String.format("moveCool%d", rc.getMovementCooldownTurns());
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
            micro(true);
        }
    }

    private static SimpleLauncherInfo targetPriority(FastIterableRobotInfoSet friendlyLaunchers, FastIterableRobotInfoSet enemyLaunchers) {
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
        return new SimpleLauncherInfo(targetLoc, minHitRatio, maxCanAttackFriends);
    }


}

