package submit23;

import battlecode.common.*;

import java.util.Random;

public class Launcher extends Unit {
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

    static boolean isDiagonal(Direction dir) {
        return dir.dx * dir.dy != 0;
    }

    public static final int MAX_HEALTH = 200;
    public static final int DAMAGE = 20;
    public static final int ATTACK_DIS = 16;
    public static final int VISION_DIS = 20;

    private static final int ATTACKING = 1;
    private static final int HEALING = 2;
    private static final Random rng = new Random(rc.getID());

    // macro vars
    static int lastSym;
    private static int state = ATTACKING;
    private static MapLocation anchoringCarrier = null;
    private static int enemyHQID = 0;
    private static MapLocation enemyHQLoc = null;

    // micro vars
    static RobotInfo attackTarget = null;
    static RobotInfo backupTarget = null;
    static RobotInfo chaseTarget = null;

    private static final int MAX_ENEMY_CNT = 10;
    static RobotInfo[] enemyLaunchers = new RobotInfo[MAX_ENEMY_CNT];
    static int enemyLauncherCnt;
    private static final int MAX_FRIENDLY_CNT = 8;
    static RobotInfo[] friendlyLaunchers = new RobotInfo[MAX_FRIENDLY_CNT];
    static int friendlyLauncherCnt;

    static RobotInfo closestEnemy = null;
    static RobotInfo groupingTarget = null;
    static int lastLauncherAttackRound = 0;
    static int ourTeamStrength = 1;

    static MapLocation cachedEnemyLocation = null;
    static int cachedRound = 0;

    static void run () throws GameActionException {
        if (turnCount == 0) {
            // future: get from spawn queue if there are more than one roles
            // prioritize the closest enemy HQ
            lastSym = Comm.symmetry;
            enemyHQID = getClosestID(Comm.enemyHQLocations);
            enemyHQLoc = Comm.enemyHQLocations[enemyHQID];
        }
        sense();
        micro();
        macro();
        if (rc.isActionReady()){
            sense();
            micro();
        }
        // have launchers perform sym check
        if (Comm.needSymmetryReport && rc.canWriteSharedArray(0, 0)) {
            Comm.reportSym();
            Comm.commit_write();
        }
        if (!Comm.isSymmetryConfirmed) {
            MapLocation symGuessLoc = getClosestLoc(Comm.enemyHQLocations);
            if (rc.canSenseLocation(symGuessLoc)) {
                RobotInfo hq = rc.senseRobotAtLocation(symGuessLoc);
                if (hq == null || hq.type != RobotType.HEADQUARTERS || hq.team != oppTeam) {
                    Comm.eliminateSym(Comm.symmetry);
                }
            }
        }
        MapRecorder.recordSym(500);
    }

    static void sense() throws GameActionException {
        anchoringCarrier = null;
        attackTarget = null;
        closestEnemy = null;
        chaseTarget = null;
        groupingTarget = null;
        backupTarget = null;
        ourTeamStrength = 1;
        friendlyLauncherCnt = 0;
        enemyLauncherCnt = 0;
        int backupTargetDis = Integer.MAX_VALUE;
        // macro vars
        for (RobotInfo robot : rc.senseNearbyRobots()) {
            if (robot.team == myTeam) {
                if (robot.type == RobotType.LAUNCHER) {
                    if (friendlyLauncherCnt >= MAX_FRIENDLY_CNT) {
                        continue;
                    }
                    if (robot.getHealth() > rc.getHealth()
                            && (groupingTarget == null
                            || robot.getHealth() > groupingTarget.getHealth()
                            || ((robot.getHealth() == groupingTarget.getHealth())
                            && (robot.location.distanceSquaredTo(rc.getLocation()) < groupingTarget.location.distanceSquaredTo(rc.getLocation()))))) {
                        groupingTarget = robot;
                    }
                    friendlyLaunchers[friendlyLauncherCnt++] = robot;
                    ourTeamStrength += 1;
                } else if (robot.type == RobotType.CARRIER && robot.getNumAnchors(Anchor.STANDARD) != 0) {
                    anchoringCarrier = robot.location;
                }
            } else {
                if (robot.type == RobotType.HEADQUARTERS) {
                    // if I found an enemy HQ not at the position of the guessed sym, sym must be wrong
                    if (!Comm.isSymmetryConfirmed && getClosestDis(robot.location, Comm.enemyHQLocations) != 0) {
                        Comm.eliminateSym(Comm.symmetry);
                    }
                    continue;
                } else if (robot.type == RobotType.LAUNCHER || robot.type == RobotType.DESTABILIZER) {
                    if (enemyLauncherCnt >= MAX_ENEMY_CNT) {
                        continue;
                    }
                    enemyLaunchers[enemyLauncherCnt++] = robot;
                    ourTeamStrength -= 1;
                    if (robot.location.distanceSquaredTo(rc.getLocation()) > ATTACK_DIS) {
                        chaseTarget = robot;
                    }
                    if (closestEnemy == null || rc.getLocation().distanceSquaredTo(closestEnemy.location) >
                            rc.getLocation().distanceSquaredTo(robot.location)) {
                        closestEnemy = robot;
                    }
                } else {
                    int dis = rc.getLocation().distanceSquaredTo(robot.location);
                    if (dis <= ATTACK_DIS && (backupTarget == null || dis < backupTargetDis)) {
                        backupTarget = robot;
                        backupTargetDis = dis;
                    } else if (dis > ATTACK_DIS && chaseTarget == null) {
                        chaseTarget = robot;
                    }
                }
            }
        }
        attackTarget = getImmediatelyAttackableTarget();
        indicator += String.format("S%d", ourTeamStrength);
    }

    static void macro() throws GameActionException {
        // only macro move in even turns
        if (!rc.isMovementReady() || rc.getRoundNum() % 2 == 0) return;
        if (Comm.needSymmetryReport && rc.getRoundNum() > 150 && rc.getID() % 8 == 0) {
            moveToward(getClosestLoc(Comm.friendlyHQLocations));
            indicator += "gotsym";
            return;
        }
        tryIslandStuff();
        if (anchoringCarrier != null) { // try escorting anchoring carrier
            moveToward(anchoringCarrier);
            indicator += "escort,";
        }
        if (!rc.isMovementReady()) return;
        if (lastSym != Comm.symmetry) {
            // recaculate the closest HQ when sym changes
            lastSym = Comm.symmetry;
            enemyHQID = getClosestID(Comm.enemyHQLocations);
        }
        // avoid the enemy HQ radius
        MapLocation closestEnemyHQ = getClosestLoc(Comm.enemyHQLocations);
        if (rc.getLocation().distanceSquaredTo(closestEnemyHQ) <= 9) {
            tryMoveDir(rc.getLocation().directionTo(closestEnemyHQ).opposite());
        }
        // if I am next to enemy HQ and hasn't seen anything, go to the next HQ
        if (rc.getLocation().distanceSquaredTo(enemyHQLoc) <= 16) {
            for (int i = enemyHQID + 1; i <= enemyHQID + 4; i++) {
                if (Comm.enemyHQLocations[i % 4] != null) {
                    enemyHQID = i % 4;
                    break;
                }
            }
        }
        enemyHQLoc = Comm.enemyHQLocations[enemyHQID];
        moveToward(enemyHQLoc);
    }

    private static int lastFailedHealingTurn = -1000;
    static void tryIslandStuff() throws GameActionException {
        // attempt to go heal if less than 100 health, capture enemy island, protect friendly island
        boolean needHeal = rc.getRoundNum() - lastFailedHealingTurn > 100 &&
                ((rc.getHealth() < MAX_HEALTH && state == HEALING) || rc.getHealth() < 100);
        if (!needHeal) {
            state = ATTACKING;
        }
        int friendlyIslandIndex = needHeal? Comm.getClosestFriendlyIslandIndex() : 0; // only calc if need heal
        int[] islandIndexes = rc.senseNearbyIslands();
        for (int i = islandIndexes.length; --i >=0; ) {
            int islandIndex = islandIndexes[i];
            Team occupyingTeam = rc.senseTeamOccupyingIsland(islandIndex);
            if (needHeal && islandIndex == friendlyIslandIndex) {
                if (occupyingTeam != myTeam) {
                    lastFailedHealingTurn = rc.getRoundNum();
                    state = ATTACKING;
                    System.out.printf("failed healing on island %d\n", friendlyIslandIndex);
                } else {
                    state = HEALING;
                    MapLocation locs[] = rc.senseNearbyIslandLocations(islandIndex);
                    moveToward(locs[rng.nextInt(locs.length)]);
                    return;
                }
            }
            if (occupyingTeam == oppTeam || (occupyingTeam == myTeam && rc.senseAnchor(islandIndex).totalHealth < Anchor.STANDARD.totalHealth)) {
                MapLocation locs[] = rc.senseNearbyIslandLocations(islandIndex);
                moveToward(locs[rng.nextInt(locs.length)]);
                return;
            }
        }
        if (rc.isMovementReady() && friendlyIslandIndex != 0) {
            indicator += "goheal,";
            moveToward(Comm.getIslandLocation(friendlyIslandIndex));
        }
    }

    static void micro() throws GameActionException {
//        indicator += String.format("a%s,b%s,c%s,ca%s,m%b",
//                attackTarget == null? "" : attackTarget.location,
//                backupTarget == null? "" : backupTarget.location,
//                chaseTarget == null? "" : chaseTarget.location,
//                cachedEnemyLocation == null? "" : cachedEnemyLocation,
//                rc.isMovementReady());
        indicator += String.format("gt%s", groupingTarget == null? null : groupingTarget.location);
        if (attackTarget != null) {
            lastLauncherAttackRound = rc.getRoundNum();
            if (rc.canAttack(attackTarget.location)) {
                cachedEnemyLocation = attackTarget.location;
                cachedRound = rc.getRoundNum();
                rc.attack(attackTarget.location);
            }
            kite(closestEnemy.location);
        } else if (backupTarget != null) {
            if (rc.canAttack(backupTarget.location)) {
                cachedEnemyLocation = backupTarget.location;
                cachedRound = rc.getRoundNum();
                rc.attack(backupTarget.location);
            }
            kite(backupTarget.location);
        }
        if (rc.isMovementReady() && rc.isActionReady()) {
            if (chaseTarget != null && rc.getHealth() > chaseTarget.health) {
                chase(chaseTarget.location);
            }
            if (cachedEnemyLocation != null && rc.getRoundNum() - cachedRound <= 2) {
                chase(cachedEnemyLocation);
            }
        }
        if (rc.isMovementReady()
                && rc.getRoundNum() - lastLauncherAttackRound <= 10
                && groupingTarget != null
                && !rc.getLocation().isAdjacentTo(groupingTarget.location)) {
            indicator += "group,";
            moveToward(groupingTarget.location);
        }
    }

    private static void chase(MapLocation location) throws GameActionException{
        Direction forwardDir = rc.getLocation().directionTo(location);
        Direction[] dirs = {forwardDir, forwardDir.rotateLeft(), forwardDir.rotateRight(),
                forwardDir.rotateLeft().rotateLeft(), forwardDir.rotateRight().rotateRight()};
        Direction bestDir = null;
        int minCanSee = Integer.MAX_VALUE;
        // pick a direction to chase to minimize the number of enemy launchers that can see us
        for (Direction dir : dirs) {
            if (rc.canMove(dir) && rc.getLocation().add(dir).distanceSquaredTo(location) <= ATTACK_DIS) {
                int canSee = 0;
                for (int i = enemyLauncherCnt; --i >= 0;){
                    if (rc.getLocation().add(dir).distanceSquaredTo(enemyLaunchers[i].location) <= VISION_DIS) {
                        canSee++;
                    }
                }
                if (minCanSee > canSee) {
                    bestDir = dir;
                    minCanSee = canSee;
                } else if (minCanSee == canSee && isDiagonal(bestDir) && !isDiagonal(dir)) {
                    // from Cow: we prefer non-diagonal moves to preserve formation
                    bestDir = dir;
                }
            }
        }
        if (bestDir != null) {
            indicator += String.format("chase%s,", location);
            rc.move(bestDir);
        } else {
            indicator += "failchase,";
        }
    }
    private static void kite(MapLocation location) throws GameActionException {
        Direction backDir = rc.getLocation().directionTo(location).opposite();
        Direction[] dirs = {backDir, backDir.rotateLeft(), backDir.rotateRight(),
                backDir.rotateLeft().rotateLeft(), backDir.rotateRight().rotateRight()};
        Direction bestDir = null;
        int minCanSee = Integer.MAX_VALUE;
        // pick a direction to kite back to minimize the number of enemy launchers that can see us
        for (Direction dir : dirs) {
            if (rc.canMove(dir)) {
                int canSee = 0;
                for (int i = enemyLauncherCnt; --i >= 0;){
                    if (rc.getLocation().add(dir).distanceSquaredTo(enemyLaunchers[i].location) <= VISION_DIS) {
                        canSee++;
                    }
                }
                if (minCanSee > canSee) {
                    bestDir = dir;
                    minCanSee = canSee;
                } else if (minCanSee == canSee && isDiagonal(bestDir) && !isDiagonal(dir)) {
                    // from Cow: we prefer non-diagonal moves to preserve formation
                    bestDir = dir;
                }
            }
        }
        if (bestDir != null){
            indicator += "kite,";
            rc.move(bestDir);
        }
    }

    private static RobotInfo getImmediatelyAttackableTarget() {
        int minHitReqired = Integer.MAX_VALUE;
        RobotInfo rv = null;
        int minDis = Integer.MAX_VALUE;
        for (int enemy_i = enemyLauncherCnt; --enemy_i >= 0;) {
            RobotInfo enemy = enemyLaunchers[enemy_i];
            int dis = enemy.location.distanceSquaredTo(rc.getLocation());
            if (dis > ATTACK_DIS) {
                continue;
            }
            if (enemy.getHealth() <= DAMAGE) {
                return enemy;
            }
            int canAttackFriendCnt = 1;
            for (int friend_i = friendlyLauncherCnt; --friend_i >= 0;) {
                if (friendlyLaunchers[friend_i].location.distanceSquaredTo(enemy.location) <= ATTACK_DIS) {
                    canAttackFriendCnt++;
                }
            }
            int hitRequired = (enemy.getHealth() + canAttackFriendCnt * DAMAGE - 1) / DAMAGE / canAttackFriendCnt;
            if (hitRequired < minHitReqired) {
                minHitReqired = hitRequired;
                rv = enemy;
                minDis = enemy.location.distanceSquaredTo(rc.getLocation());
            } else if (hitRequired == minHitReqired) {
                if (dis < minDis) {
                    rv = enemy;
                    minDis = dis;
                }
            }
        }
        return rv;
    }


}

