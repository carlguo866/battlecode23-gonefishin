package launcher.bot1;

import battlecode.common.*;
import launcher.bot1.util.FastIterableRobotInfoSet;
import launcher.bot1.util.FastLocIntMap;

//import java.util.HashSet;

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
    static RobotInfo closestEnemy = null;
    static int ourTeamStrength = 2;
    // macro vars
    static RobotInfo masterLauncher = null;
    static int dis = 0;
    static int friendlyLauncherCnt = 1;
    static RobotInfo furthestFriendlyLauncher = null;

    static FastIterableRobotInfoSet friendlyLaunchers = new FastIterableRobotInfoSet();
    static FastIterableRobotInfoSet enemies = new FastIterableRobotInfoSet();

    static FastLocIntMap loc2info = new FastLocIntMap();
    private static final int HEALTH_MASK = 0x000F;
    private static final int TYPE_MASK = 0x00F0;
    private static final int TYPE_SHIFT = 4;
    private static final int IS_OUR_TEAM_BIT = 1 << 4;
    // health is stored as the number of shots to kill
    private void addInfo(RobotInfo robot) {
        loc2info.remove(robot.location);
        int val = robot.health / 30;
        if (robot.team == myTeam)
            val |= IS_OUR_TEAM_BIT;
        val |= robot.type.ordinal() << TYPE_SHIFT;
        loc2info.add(robot.location, val);
    }

    private void getInfo(MapLocation loc) {
        // just copy this into your code and use it however you
        int val = loc2info.getVal(loc);
        boolean isOurTeam = (val & IS_OUR_TEAM_BIT) != 0;
        int health = val & HEALTH_MASK; // this is the number of shots to kill
        RobotType type = RobotType.values()[(val & TYPE_MASK) >> TYPE_SHIFT];
    }

    static void sense() throws GameActionException {
        // micro vars
        attackTarget = null;
        closestEnemy = null;
//        ourTeamStrength = 2;
        // macro vars
        masterLauncher = null;
        dis = 0;
//        friendlyLauncherCnt = 1;
        furthestFriendlyLauncher = null;
        friendlyLaunchers.clear();
        enemies.clear();


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
                } else {
                    enemies.add(robot);
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
        enemies.updateIterable();
        attackTarget = targetPriority(friendlyLaunchers, enemies);
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
                    if (ourTeamStrength < 0 || rc.getHealth() <= 120) {
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
            indicator += String.format("Strength%d ", ourTeamStrength);
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
        if (rc.isMovementReady() && attackTarget == null) { // macro
            // the launcher with the smallest ID is the master, everyone follows him
            if (masterLauncher != null) {
                indicator += String.format("Following master %d at %s", masterLauncher.getID(), masterLauncher.location);
                follow(masterLauncher.location);
            } else { // I am the master
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
                } else
                if (dis >= 9 && friendlyLauncherCnt <= 3) {
                    // if there is a launcher going far away while there are few launchers,
                    // most likely it has seen something, follow him
                    indicator += String.format("Mfollow %s", furthestFriendlyLauncher.location);
                    follow(furthestFriendlyLauncher.location);
                } else {
                    if (rc.getRoundNum() <= 26) {
                        // first few turns move toward center of the map
                        moveToward(new MapLocation(mapWidth/2, mapHeight/2));
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
                        enemyHQLoc = Comm.enemyHQLocations[enemyHQID]; // in case symmetry changes...
                        indicator += String.format("M2EHQ@%s", enemyHQLoc);
                        moveToward(enemyHQLoc);
                    }
                }
            }
        }
        if (rc.isActionReady()){
            sense();
            micro();
        }
    }

    private static MapLocation targetPriority(FastIterableRobotInfoSet friendlyLaunchers, FastIterableRobotInfoSet enemies) {
        MapLocation target = null;
//        if (enemies.size() == 0){
//            return target;
//        }
        int minHitRatio = 10;
        int maxCanAttackFriends = 1;
        boolean yes = false;
        for (MapLocation enemy: enemies.locs){
            System.out.println("enemy.x"+ enemy.x);
            if (target == null) {
                target = enemy;
            }
//            else if (target.type != RobotType.LAUNCHER && enemy.type == RobotType.LAUNCHER){
//                target = enemy;
////                System.out.println("here change to launcher");
//            }else if (target.type == RobotType.LAUNCHER && enemy.type != RobotType.LAUNCHER){
//                continue;
//            }
            int canAttackFriends = 1;
            for (MapLocation friend: enemies.locs){
                if (friend.distanceSquaredTo(target) <= VISION_DIS){
                    canAttackFriends+=1;
                }
            }
//            System.out.println(String.format("loc%s", enemy));
//            int hitRatio = (int) Math.ceil((double) enemy.health / (canAttackFriends * DAMAGE) );
//            if (target == enemy) {
//                minHitRatio = hitRatio;
//            } else {
//                if (minHitRatio > (int) hitRatio){
//                    target = enemy;
//                    maxCanAttackFriends = canAttackFriends;
//                } else if (minHitRatio == canAttackFriends){
//                    target = rc.getLocation().distanceSquaredTo(target) <
//                            rc.getLocation().distanceSquaredTo(enemy)? target : enemy;
//                    maxCanAttackFriends = canAttackFriends;
//                }
//            }
        }
//        indicator += String.format("pos%s", rc.getLocation());
        indicator += String.format("tgt%s", target);
//        indicator += String.format("health%s ", target.health);
//        indicator += String.format("friends%s ", maxCanAttackFriends);
//        indicator += String.format("ratio%s ", minHitRatio);
//        indicator += String.format("target%s ", target.getID());
//        rc.setIndicatorLine(rc.getLocation(), target.location, 0, 0 , 0);

//        assert (!yes || (yes && target.type == RobotType.LAUNCHER)): String.format("targettype%s", target.type);
        return target;
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

