package bot1;

import battlecode.common.*;
import bot1.util.FastIterableRobotInfoSet;

public class Launcher extends Unit {
    static class SimpleLauncherInfo {

        public MapLocation loc;
        public int hitRatio;

        public int canAttackFriends;

        public SimpleLauncherInfo(MapLocation locc, int hitRatioo, int friends){
            loc = locc;
            hitRatio = hitRatioo;
            canAttackFriends = friends;
        }
    }

    static int betterDistance(MapLocation start, MapLocation end){
        return (int) Math.max(Math.pow(Math.abs(start.x-end.x), 2), Math.pow(Math.abs(start.y-end.y), 2));
    }

    static boolean checkWall(MapLocation start, MapLocation end) throws GameActionException{
        MapLocation iter = start.add(start.directionTo(end));
        int hardstop = 0;
        while (rc.canSenseLocation(iter) && !iter.equals(end) && hardstop < 4){
            if (!rc.sensePassability(iter)) return true;
            iter = iter.add(iter.directionTo(end));
            hardstop++;
        }
        return false;
    }

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
    static RobotInfo cachedFriendlyLauncher = null;
    static int lastFriendSensedRound = -100;
    private static int groupingAttempt = 0;

    static int cachedTurn = 0;
    static int attackTargetHealth;
    static RobotType attackTargetType;
    static RobotInfo closestEnemy = null;
    static int ourTeamStrength = 1;
    static int ourTeamHealth = 0;
    static int closeFriendsSize = 0; 

    // macro vars
    static RobotInfo closestFriendlyLauncher = null;
    static int lastEnemySensedRound = -100;

    static FastIterableRobotInfoSet friendlyLaunchers = new FastIterableRobotInfoSet();
    static FastIterableRobotInfoSet enemyLaunchers = new FastIterableRobotInfoSet();

    static Direction cachedDirection = null;

    static void run () throws GameActionException {
        if (turnCount == 0) {
            // future: get from spawn queue if there are more than one roles
            // prioritize the closest enemy HQ
            enemyHQID = getClosestID(Comm.enemyHQLocations);
            enemyHQLoc = Comm.enemyHQLocations[enemyHQID];
        }
        sense(false);
        micro(false);
        macro();
        if (rc.isActionReady()){
            sense(false);
            micro(true);
        }
        // have launchers perform sym check
        if (!Comm.isSymmetryConfirmed) {
            MapLocation symGuessLoc = getClosestLoc(Comm.enemyHQLocations);
            if (rc.canSenseLocation(symGuessLoc)) {
                RobotInfo hq = rc.senseRobotAtLocation(symGuessLoc);
                if (hq == null || hq.type != RobotType.HEADQUARTERS || hq.team != oppTeam) {
                    Comm.eliminateSym(Comm.symmetry);
                }
            }
            MapRecorder.recordSym(500);
        }
    }

    static void sense(boolean isSecondTime) throws GameActionException {
        attackTarget = null;
        closestEnemy = null;
        ourTeamStrength = 1;
        closeFriendsSize = 0;
        // macro vars
        int dis = 0;
        closestFriendlyLauncher = null;
        ourTeamHealth = (int) Math.ceil((double) rc.getHealth() / DAMAGE) ;
        friendlyLaunchers.clear();
        enemyLaunchers.clear();
        attackTargetHealth = (int) Math.ceil((double) 250 / DAMAGE);
        for (RobotInfo robot : rc.senseNearbyRobots()) {
            if (robot.team == myTeam) {
                if (robot.type == RobotType.LAUNCHER) {
                    if (friendlyLaunchers.size < 6)
                        friendlyLaunchers.add(robot);
                        if (betterDistance(rc.getLocation(), robot.getLocation()) <= 4 ||
                                (checkWall(rc.getLocation(), robot.getLocation())
                                        && betterDistance(rc.getLocation(), robot.getLocation()) <= 9)){
    //                        System.out.println(String.format("robotid%d,%d,%s,%s", robot.getID(),
    //                                betterDistance(rc.getLocation(), robot.getLocation()), rc.getLocation(), robot.location));
                            closeFriendsSize+=1;
                            ourTeamHealth += (int) Math.ceil((double) robot.getHealth() / DAMAGE);
                        }
                    ourTeamStrength += 1;

                    if (closestFriendlyLauncher == null) {
                        closestFriendlyLauncher = robot;
                        dis = betterDistance(rc.getLocation(), robot.getLocation());
                    }
                    int newDis = betterDistance(rc.getLocation(), robot.getLocation());
                    if (newDis < dis) {
                        dis = newDis;
                        closestFriendlyLauncher = robot;
                    }
                    lastFriendSensedRound = rc.getRoundNum();
                }
            } else {
                if (robot.type == RobotType.HEADQUARTERS) {
                    // if I found an enemy HQ not at the position of the guessed sym, sym must be wrong
                    if (!Comm.isSymmetryConfirmed && getClosestDis(robot.location, Comm.enemyHQLocations) != 0) {
                        Comm.eliminateSym(Comm.symmetry);
                    }
                    continue;
                } else if (robot.type == RobotType.LAUNCHER) {
                    groupingAttempt = 0;
                    enemyLaunchers.add(robot);
                    ourTeamStrength -= 1;
                    ourTeamHealth -= (int) Math.ceil((double) robot.getHealth() / DAMAGE);
                    lastEnemySensedRound = rc.getRoundNum();
                } else {
                    int health = (int) Math.ceil((double) robot.health / DAMAGE);
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
        if (friendlyLaunchers.size != 0) {
            cachedFriendlyLauncher = closestFriendlyLauncher;
        }
        indicator += String.format("S%d", ourTeamStrength);
    }

    static void macro() throws GameActionException{
        if (!rc.isMovementReady()) {
            return;
        }
        if (Comm.isSymmetryConfirmed && Comm.needSymmetryReport && rc.getID() % 4 == 0) {
            if (rc.canWriteSharedArray(0, 0)) {
                Comm.reportSym();
                Comm.commit_write();
            } else if (rc.getID() % 4 == 0) { // only have a quarter of launchers go back to report
                moveToward(getClosestLoc(Comm.friendlyHQLocations));
                indicator += "gotsym";
                return;
            }
        }
        indicator += String.format("size%d", closeFriendsSize);
//        if (closestFriendlyLauncher != null) {
//            rc.setIndicatorLine(rc.getLocation(), closestFriendlyLauncher.getLocation(), 0 ,0,0);
//        }
        if ((attackTarget == null || rc.getRoundNum() - lastEnemySensedRound < 3) && closeFriendsSize < 2
            && groupingAttempt < 10) {
            if (closestFriendlyLauncher != null && betterDistance(rc.getLocation(), closestFriendlyLauncher.location) >= 9) {
                moveToward(closestFriendlyLauncher.location);
                groupingAttempt+=1;
                return;
            } else if (closeFriendsSize == 0 && cachedFriendlyLauncher != null) {
                moveToward(cachedFriendlyLauncher.location);
                groupingAttempt+=1;
                return;
            }
        }
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
            if (rc.getLocation().distanceSquaredTo(enemyHQLoc) <= 16) {
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

    static void micro(boolean isSecondTime) throws GameActionException {
        if (attackTarget != null) {
            if (rc.canAttack(attackTarget)) {
                rc.attack(attackTarget);
            }
            if (rc.isMovementReady()) {
                // move toward enemy if sensed an enemy outside attack range
                if (rc.isActionReady() && (ourTeamStrength >= 0 ||
                        (ourTeamStrength==0 && rc.getHealth() > closestEnemy.getHealth()))) {
                    chase(attackTarget);
                } else {
                    // if at disadvantage pull back
                    if (closestEnemy != null) {
                        if (ourTeamStrength < -1 || rc.getHealth() < closestEnemy.health
                            || (ourTeamStrength == 0 && ourTeamHealth < 0)){
                            indicator += String.format("run%d", closestEnemy.health-rc.getHealth());
                            //run
                            kite(closestEnemy.location, 0);
                        } else if (rc.getHealth() == closestEnemy.health && !rc.isActionReady()) {
                            //cached move outside of vision
                            kite(closestEnemy.location, 1);
                        } else if (ourTeamStrength == -1) {
                            //cached move outside of vision
                            kite(closestEnemy.location, 1);
                        }
                            //run and attack; shown unuseful
//                        }  else if (ourTeamStrength == 0 && attackTargetType == RobotType.LAUNCHER) {
//                            System.out.println("scenario22222");
//                            // if I can back off to a location that I can still attack from, kite back
//                            kite(attackTarget, 2);
//                        }
                    }
                }
            }
            if (isSecondTime) {
                cachedAttackTarget = new SimpleLauncherInfo(attackTarget, attackTargetHealth-1, 0);
                cachedTurn = 0;
            }
        } else {
            if (rc.isMovementReady() && cachedDirection != null) {
                if (rc.canMove(cachedDirection)) {
                    rc.move(cachedDirection);
                }
                cachedDirection = null;
            }
            // maybe useful
//            if (rc.isMovementReady() && cachedAttackTarget != null) {
//                Direction dir = rc.getLocation().directionTo(cachedAttackTarget.loc);
//                if (rc.canMove(dir)) {
//                    rc.move(dir);
//                    System.out.println(String.format("CacheDir %s", dir));
//                }
//            }
            if (isSecondTime
                    && cachedAttackTarget != null
                    && rc.isActionReady()
                    && rc.canAttack(cachedAttackTarget.loc)) {
                rc.attack(cachedAttackTarget.loc);
            }
            MapLocation[] clouds = rc.senseNearbyCloudLocations();
            if (isSecondTime && clouds.length != 0) {
                MapLocation randomCloudLoc = clouds[(int) (Math.random() * clouds.length)];
                if (rc.isActionReady() && rc.canAttack(randomCloudLoc)) {
                    rc.attack(randomCloudLoc);
                }
            }
        }
        if (isSecondTime) {
            cachedTurn += 1;
            if (cachedTurn > 2) cachedAttackTarget = null;
            if (rc.getRoundNum() - lastFriendSensedRound > 2) cachedFriendlyLauncher = null;
        }
    }
    private static void chase(MapLocation loc) throws GameActionException{
        Direction forwardDir = rc.getLocation().directionTo(attackTarget);
        Direction[] dirs = {forwardDir, forwardDir.rotateLeft(), forwardDir.rotateRight(),
                forwardDir.rotateLeft().rotateLeft(), forwardDir.rotateRight().rotateRight()};
        Direction result = null;
        for (Direction dir : dirs) {
            if (rc.getLocation().add(dir).distanceSquaredTo(attackTarget) <= ATTACK_DIS
                    && rc.canMove(dir)) {
                // extraCheck == 0: run; extraCheck==1 cache and run out of vision;
                // extraCheck == 2: go to a loc that can still attack
                if (result == null) result = dir;
                if ((rc.getLocation().add(result).distanceSquaredTo(loc)
                                < rc.getLocation().add(dir).distanceSquaredTo(loc))) {
                    result = dir;
                }
            }
        }
        if (result != null){
            rc.move(result);
            if (rc.canAttack(attackTarget)) {
                rc.attack(attackTarget);
            }
//            indicator += String.format("Chase%s", result);
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
                // extraCheck == 0: run; extraCheck==1 cache and run out of vision;
                // extraCheck == 2: go to a loc that can still attack
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
//            indicator += String.format("Kite%s", result);
            if (extraChecks == 1) cachedDirection = result.opposite();
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

            int canAttackFriends = 1;
            for (MapLocation friendLoc: friendlyLaunchers.locs){
                if (friendLoc == null) break;
                if (friendLoc.distanceSquaredTo(targetLoc) <= VISION_DIS){
                    canAttackFriends+=1;
                }
            }
            int hitRatio = (int) Math.ceil((double) enemyHealth / (canAttackFriends));
            if (targetLoc == enemyLoc) {
                minHitRatio = hitRatio;
            } else {
                if (minHitRatio > hitRatio){
                    targetLoc = enemyLoc;
                    maxCanAttackFriends = canAttackFriends;
                } else if (minHitRatio == hitRatio){
                    targetLoc = rc.getLocation().distanceSquaredTo(targetLoc) <
                            rc.getLocation().distanceSquaredTo(enemyLoc)? targetLoc : enemyLoc;
                    maxCanAttackFriends = canAttackFriends;
                }
            }
        }
        return new SimpleLauncherInfo(targetLoc, minHitRatio, maxCanAttackFriends);
    }


}

