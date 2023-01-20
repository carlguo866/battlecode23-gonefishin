package submit8micro.submit8;

import battlecode.common.*;
import java.util.HashSet;

public class Launcher extends Unit {
    private static final int MAX_HEALTH = 20;
    private static final int ATTACK_DIS = 16;
    private static final int VISION_DIS = 20;
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
//    static int friendlyLauncherCnt = 1;
    static RobotInfo furthestFriendlyLauncher = null;
    static Direction cachedDirection = null;

    static HashSet<RobotInfo> friendlyLaunchers = null;

    static HashSet<RobotInfo> enemies = null;

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
        friendlyLaunchers = new HashSet<>();
        enemies = new HashSet<>();
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
                    continue;}
                else {
                    enemies.add(robot);

                }
//                } else if (robot.type == RobotType.CARRIER) {
//                    ourTeamStrength -= 1;
//                } else if (robot.type == RobotType.LAUNCHER) {
//                    ourTeamStrength -= 2;
//                } else if (robot.type == RobotType.DESTABILIZER) {
//                    ourTeamStrength -= 3;
//                }

                if (closestEnemy == null || rc.getLocation().distanceSquaredTo(closestEnemy.location) >
                        rc.getLocation().distanceSquaredTo(robot.location)) {
                    closestEnemy = robot;
                }
            }
        }
        attackTarget = targetPriority(friendlyLaunchers, enemies);
//        indicator += String.format("target%s", attackTarget);
    }

    static void micro() throws GameActionException {
        if (attackTarget != null) {
            int attackCooldown = rc.getActionCooldownTurns();
//            indicator += String.format("attackcool%d", attackCooldown);
            int moveCooldown = rc.getMovementCooldownTurns();
//            indicator += String.format("movecool%d", moveCooldown);
            if (attackTarget != null && rc.canAttack(attackTarget.location)) {
                rc.attack(attackTarget.location);
            }
            if (rc.isMovementReady()) {
                // move toward enemy if sensed an enemy outside attack range
                if (rc.isActionReady()) {
                    // this is never going to trigger?
//                    indicator += String.format("seen but not attack");
                    Direction forwardDir = rc.getLocation().directionTo(attackTarget.location);
                    Direction[] dirs = {forwardDir, forwardDir.rotateLeft(), forwardDir.rotateRight(),
                            forwardDir.rotateLeft().rotateLeft(), forwardDir.rotateRight().rotateRight()};
                    for (Direction dir : dirs) {
                        if (rc.getLocation().distanceSquaredTo(attackTarget.location) > ATTACK_DIS &&
                                rc.getLocation().add(dir).distanceSquaredTo(attackTarget.location) <= ATTACK_DIS
                                && rc.canMove(dir)) {
                            rc.move(dir);
                            if (rc.canAttack(attackTarget.location)) {
                                rc.attack(attackTarget.location);
                            }
                        }
                    }
                } else {
                    // if at disadvantage or more healthy launcher in sight, pull back
//                    if () {// ourTeamStrength < 0 ||
//
//                    } else {
                    // if I can back off to a location that I can still attack from, kite back

//                    cachedDirection =  rc.getLocation().directionTo(closestEnemy.location);
//                    Direction backDir = cachedDirection.opposite();
//                    Direction[] dirs = {backDir, backDir.rotateLeft(), backDir.rotateRight(),
//                            backDir.rotateLeft().rotateLeft(), backDir.rotateRight().rotateRight()};
//                    for (Direction dir : dirs) {
//                        if (rc.canMove(dir)) {
//                            rc.move(dir);
//                            break;
//                        }
//                    }
                    if (rc.getHealth() <= 12) {
                        Direction backDir = rc.getLocation().directionTo(closestEnemy.location).opposite();
                        Direction[] dirs = {backDir, backDir.rotateLeft(), backDir.rotateRight(),
                                backDir.rotateLeft().rotateLeft(), backDir.rotateRight().rotateRight()};
                        for (Direction dir : dirs) {
                            if (rc.canMove(dir)) {
                                rc.move(dir);
                                break;
                            }
                        }
                    }

                    if (attackTarget.type == RobotType.LAUNCHER) {
                        kite(attackTarget);
//                        Direction backDir = rc.getLocation().directionTo(attackTarget.location).opposite();
//                        Direction[] dirs = {backDir, backDir.rotateLeft(), backDir.rotateRight(),
//                                backDir.rotateLeft().rotateLeft(), backDir.rotateRight().rotateRight()};
//                        for (Direction dir : dirs) {
//                            if (rc.canMove(dir)) {
//                                //rc.getLocation().add(dir).distanceSquaredTo(closestEnemy.location) <= ATTACK_DIS
//                                //                                &&
//                                cachedDirection = dir.opposite();
//                                rc.move(dir);
//                                indicator += String.format("Kiting%s", dir);
//                                break;
//                            }
//                        }
//
                    }
                }
            }
            indicator += String.format("Micro strength %d", ourTeamStrength);
        } else {
//            if (rc.isMovementReady() && cachedDirection != null) {
////                    Direction[] dirs = {cachedDirection, cachedDirection.rotateRight(), cachedDirection.rotateLeft(),
////                            cachedDirection.rotateRight().rotateRight(), cachedDirection.rotateLeft().rotateLeft()};
////                    for (Direction dir : dirs) {
//                if ( rc.canMove(cachedDirection)) {
//                    //rc.getLocation().add(dir).distanceSquaredTo(closestEnemy.location) <= ATTACK_DIS
//                    //                                &&
//                    rc.move(cachedDirection);
//                    indicator += String.format("Cached Direction2 %s", cachedDirection);
//                }
////                    }
//                cachedDirection = null;
//            }
        }
    }

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
        if (rc.getRoundNum() <= 6) {
            // first two rounds just wait for the other two to join and move together
            return;
        }

        sense();
        micro();
        if (attackTarget == null) { // macro
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
                } else if (dis >= 4 && friendlyLaunchers.size() <= 3) {
                    // if there is a launcher going far away while there are few launchers,
                    // most likely it has seen something, follow him
                    indicator += String.format("Mfollow %s", furthestFriendlyLauncher.location);
                    follow(furthestFriendlyLauncher.location);
//                } else {
                } else {
                    if (rc.getRoundNum() <= 26) {
                        // first few turns move toward center of the map
                        moveToward(new MapLocation(rc.getMapWidth()/2, rc.getMapHeight()/2));
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
                        indicator += String.format("M2EHQ@%s", enemyHQLoc);
                        moveToward(enemyHQLoc);
                    }
                }
            }
        }
        sense();
        micro();
    }
    private static Direction kite(RobotInfo target) throws GameActionException {
        Direction backDir = rc.getLocation().directionTo(target.location).opposite();
        Direction[] dirs = {backDir, backDir.rotateLeft(), backDir.rotateRight(),
                backDir.rotateLeft().rotateLeft(), backDir.rotateRight().rotateRight()};
        Direction result = null;
        for (Direction dir : dirs) {
            if (rc.canMove(dir)) {
                if (result == null) result = dir;
//                if (rc.getLocation().add(result).distanceSquaredTo(target.location) > ATTACK_DIS
//                    && rc.getLocation().add(dir).distanceSquaredTo(target.location) <= ATTACK_DIS) {
//                    result = dir;
//                }
                if (rc.getLocation().add(result).distanceSquaredTo(target.location) < rc.getLocation().add(dir).distanceSquaredTo(target.location)) {
                    result = dir;
                }
                indicator += String.format("Kiting%s", dir);
            }
        }
        rc.move(result);
        cachedDirection = result.opposite();
        return result;
    }
    private static RobotInfo targetPriority(HashSet<RobotInfo> friendlyLaunchers, HashSet<RobotInfo> enemies) {
//        if (!rc.canActLocation(r2.location))
//            return r1; very questionable
        RobotInfo target = null;
        int maxCanAttackFriends = 0;
        for (RobotInfo enemy: enemies){
            if (target == null) {
                target = enemy;
            }else if (target.type != RobotType.LAUNCHER && enemy.type == RobotType.LAUNCHER){
                target = enemy;
            }

            if (target.type == RobotType.LAUNCHER && enemy.type != RobotType.LAUNCHER){
                continue;
            }

            int canAttackFriends = 0;
//            int canSeeFriends = 0;
            for (RobotInfo friend: friendlyLaunchers){
                if (friend.location.distanceSquaredTo(target.location) <= ATTACK_DIS){
                    canAttackFriends+=1;
                }
//                if (friend.location.distanceSquaredTo(target.location) <= VISION_DIS){
//                    canSeeFriends+=1;
//                }
            }
            if (target == enemy) {
                maxCanAttackFriends = canAttackFriends;
            } else {
                if (maxCanAttackFriends < canAttackFriends){
                    target = enemy;
                } else if (maxCanAttackFriends == canAttackFriends){
                    target = target.health < enemy.health? target : enemy;
                }
            }
        }
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

