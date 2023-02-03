package bot1;

import battlecode.common.*;
import bot1.util.FastMath;

public class Launcher extends Unit {
    static boolean isDiagonal(Direction dir) {
        return dir.dx * dir.dy != 0;
    }

    public static final int MAX_HEALTH = 200;
    public static final int DAMAGE = 20;
    public static final int ATTACK_DIS = 16;
    public static final int VISION_DIS = 20;
    private static final int HEALING_CUTOFF = 121;

    private static final int ATTACKING = 1;
    private static final int HEALING = 2;
    private static final int FIGHTING_ISLAND = 3;

    // macro vars
    static int lastSym;
    private static int state = ATTACKING;
    private static MapLocation anchoringCarrier = null;
    private static int enemyHQID = 0;
    private static MapLocation enemyHQLoc = null;
    private static MapLocation homeHQLoc = null;

    // micro vars
    static RobotInfo attackTarget = null;
    static RobotInfo backupTarget = null;
    static RobotInfo chaseTarget = null;

    private static final int MAX_ENEMY_CNT = 8;
    static RobotInfo[] enemyLaunchers = new RobotInfo[MAX_ENEMY_CNT];
    static boolean[] enemyInCloud = new boolean[MAX_ENEMY_CNT];
    static int enemyLauncherCnt;
    private static final int MAX_FRIENDLY_CNT = 6;
    static RobotInfo[] friendlyLaunchers = new RobotInfo[MAX_FRIENDLY_CNT];
    static boolean[] friendInCloud = new boolean[MAX_ENEMY_CNT];
    static int friendlyLauncherCnt;

    static RobotInfo groupingTarget = null;
    static RobotInfo cachedGroupingTarget = null;
    static int cachedGroupingRound = -1000;
    static int lastLauncherAttackRound = -100;
    static int ourTeamStrength = 1;

    static MapLocation cachedEnemyLocation = null;
    static int cachedRound = 0;

    private static int closeFriendsSize = 0;


    static void run () throws GameActionException {
        if (turnCount == 0) {
            // future: get from spawn queue if there are more than one roles
            // prioritize the closest enemy HQ
            lastSym = Comm.symmetry;
            homeHQLoc = getClosestLoc(Comm.friendlyHQLocations);
            enemyHQID = getClosestID(homeHQLoc, Comm.enemyHQLocations);
            enemyHQLoc = Comm.enemyHQLocations[enemyHQID];
        }
        sense();
        micro();
        indicator += String.format("size%d", closeFriendsSize);
        macro();
        if (rc.isActionReady()){
            sense();
            micro();
        }
        if (rc.isActionReady()) {
            tryGuessAttack();
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
        if (groupingTarget != null){
            cachedGroupingTarget = groupingTarget;
            cachedGroupingRound = rc.getRoundNum();
        }
    }

    static void sense() throws GameActionException {
        anchoringCarrier = null;
        attackTarget = null;
        chaseTarget = null;
        groupingTarget = null;
        backupTarget = null;
        ourTeamStrength = 1;
        friendlyLauncherCnt = 0;
        enemyLauncherCnt = 0;
        closeFriendsSize = 0;
        int backupTargetDis = Integer.MAX_VALUE;
        // macro vars
        for (RobotInfo robot : rc.senseNearbyRobots()) {
            if (robot.team == myTeam) {
                if (robot.type == RobotType.LAUNCHER) {
                    if (friendlyLauncherCnt >= MAX_FRIENDLY_CNT) {
                        continue;
                    }
                    if (groupingTarget == null
                            || robot.getHealth() > groupingTarget.getHealth()
                            || (robot.getHealth() == groupingTarget.getHealth() && robot.location.distanceSquaredTo(enemyHQLoc) < groupingTarget.location.distanceSquaredTo(enemyHQLoc))
                        ) {
                        groupingTarget = robot;
                    }
                    friendInCloud[friendlyLauncherCnt] = rc.senseCloud(robot.location);
                    friendlyLaunchers[friendlyLauncherCnt++] = robot;
                    ourTeamStrength += 1;
                    if (robot.location.distanceSquaredTo(rc.getLocation()) <= 8){
                        closeFriendsSize++;
                    }
                } else if (robot.type == RobotType.CARRIER && robot.getNumAnchors(Anchor.STANDARD) != 0) {
                    anchoringCarrier = robot.location;
                }
            } else {
                if (robot.type == RobotType.HEADQUARTERS) {
                    // if I found an enemy HQ not at the position of the guessed sym, sym must be wrong
                    if (!Comm.isSymmetryConfirmed && getClosestDis(robot.location, Comm.enemyHQLocations) != 0) {
                        Comm.eliminateSym(Comm.symmetry);
                    }
                    MapRecorder.reportEnemyHQ(robot.location); // this is fairly optimized and unexpensive
                    continue;
                } else if (robot.type == RobotType.LAUNCHER || robot.type == RobotType.DESTABILIZER) {
                    if (enemyLauncherCnt >= MAX_ENEMY_CNT) {
                        continue;
                    }
                    enemyInCloud[enemyLauncherCnt] = rc.senseCloud(robot.location);
                    enemyLaunchers[enemyLauncherCnt++] = robot;
                    ourTeamStrength -= 1;
                    if (robot.location.distanceSquaredTo(rc.getLocation()) > ATTACK_DIS) {
                        chaseTarget = robot;
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
        // Cow: only macro move in even turns
        // try return immediately after a move command, so if we stuck we won't overrun turns
        if (!rc.isMovementReady())
            return;
        if (Comm.needSymmetryReport && rc.getRoundNum() > 150 && rc.getID() % 8 == 0) {
            moveToward(getClosestLoc(Comm.friendlyHQLocations));
            indicator += "gotsym";
            return;
        }
        if (tryIslandStuff())
            return;
//        if (anchoringCarrier != null) { // try escorting anchoring carrier
//            follow(anchoringCarrier);
//            indicator += "escort,";
//            return;
//        }
        if (lastSym != Comm.symmetry) {
            // recaculate the closest HQ when sym changes
            lastSym = Comm.symmetry;
            enemyHQID = getClosestID(Comm.enemyHQLocations);
        }
        // avoid the enemy HQ radius
        MapLocation closestEnemyHQ = getClosestLoc(Comm.enemyHQLocations);
        if (rc.getLocation().distanceSquaredTo(closestEnemyHQ) <= 9) {
            tryMoveDir(rc.getLocation().directionTo(closestEnemyHQ).opposite());
            return;
        }

        if (closeFriendsSize < 3 && (rc.getRoundNum() - lastLauncherAttackRound) < 10) {
            if (rc.isMovementReady() && groupingTarget != null ) {
                indicator += "group,";
                if (!rc.getLocation().isAdjacentTo(groupingTarget.location)) {
                    follow(groupingTarget.location);
                } else if (rc.getHealth() < groupingTarget.health) { // allowing healthier target to move away first
                    indicator += "stop";
                    rc.setIndicatorDot(rc.getLocation(), 0, 255, 0);
                    return;
                }
                rc.setIndicatorLine(rc.getLocation(), groupingTarget.location, 0, 255, 0);
            } else if (rc.isMovementReady()
                    && groupingTarget == null
                    && cachedGroupingTarget != null
                    && rc.getRoundNum() - cachedGroupingRound < 6
                    && !rc.getLocation().isAdjacentTo(cachedGroupingTarget.location)){
                indicator += String.format("cacheGroup%s,",cachedGroupingTarget.location);
                follow(cachedGroupingTarget.location);
                rc.setIndicatorLine(rc.getLocation(), cachedGroupingTarget.location, 0, 255, 0);
            }
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
    // returns whether has moved
    private static MapLocation islandTargetLoc = null;
    private static int islandTargetIndex = 0;
    static boolean tryIslandStuff() throws GameActionException {
        // attempt to go heal if less than 100 health, capture enemy island, protect friendly island
        boolean needHeal =  (rc.getHealth() < MAX_HEALTH && state == HEALING) || rc.getHealth() < HEALING_CUTOFF;
        if (state == HEALING && !needHeal && (ourTeamStrength >= 3 || rc.getRoundNum() - lastLauncherAttackRound > 50)) {
            islandTargetIndex = 0;
            islandTargetLoc = null;
            state = ATTACKING;
        }
        if (state == ATTACKING && needHeal && rc.getRoundNum() - lastFailedHealingTurn > 100) {
            islandTargetIndex = Comm.getClosestFriendlyIslandIndex();
            if (islandTargetIndex != 0) {
                islandTargetLoc = Comm.getIslandLocation(islandTargetIndex);
                state = HEALING;
            }
        }
        int[] islandIndexes = rc.senseNearbyIslands();
        for (int i = islandIndexes.length; --i >=0; ) {
            int islandIndex = islandIndexes[i];
            Team occupyingTeam = rc.senseTeamOccupyingIsland(islandIndex);
            if (islandIndex == islandTargetIndex) {
                // see if we need to transition out of current state
                if (state == HEALING && occupyingTeam != myTeam) {
                    lastFailedHealingTurn = rc.getRoundNum();
                    islandTargetLoc = null;
                    islandTargetIndex = 0;
                    state = ATTACKING;
                    System.out.printf("failed healing on island %d\n", islandIndex);
                }
                if (state == FIGHTING_ISLAND && (occupyingTeam == Team.NEUTRAL ||
                        (occupyingTeam == myTeam && rc.senseAnchor(islandIndex).totalHealth == Anchor.STANDARD.totalHealth))) {
                    islandTargetLoc = null;
                    islandTargetIndex = 0;
                    state = ATTACKING;
                }
            }
            if (state != HEALING && (occupyingTeam == oppTeam ||
                    (occupyingTeam == myTeam
                            && rc.senseAnchor(islandIndex).totalHealth < Anchor.STANDARD.totalHealth))) {
                MapLocation locs[] = rc.senseNearbyIslandLocations(islandIndex);
                islandTargetLoc = locs[rc.getID() % locs.length];
                islandTargetIndex = islandIndex;
                state = FIGHTING_ISLAND;
                break;
            }
        }
        if (rc.isMovementReady() && islandTargetLoc != null) {
            indicator += "goisland,";
            moveToward(islandTargetLoc);
            return true;
        }
        return false;
    }

    static void micro() throws GameActionException {
//        indicator += String.format("a%s,b%s,c%s,ca%s,m%b",
//                attackTarget == null? "" : attackTarget.location,
//                backupTarget == null? "" : backupTarget.location,
//                chaseTarget == null? "" : chaseTarget.location,
//                cachedEnemyLocation == null? "" : cachedEnemyLocation,
//                rc.isMovementReady());
        RobotInfo target = attackTarget == null? backupTarget : attackTarget;
        if (target != null) {
            if (target == attackTarget) {
                lastLauncherAttackRound = rc.getRoundNum();
            }
            Unit.disableTurnDirRound = rc.getRoundNum() + 40;
            RobotInfo deadTarget = null;
            if (rc.canAttack(target.location)) {
                cachedRound = rc.getRoundNum();
                if (target.health <= DAMAGE) {
                    deadTarget = target;
                }
                rc.attack(target.location);
            }
            // find the closest guy alive, focus on launchers first, cache him and kite back
            int minDis = Integer.MAX_VALUE;
            cachedEnemyLocation = null;
            for (int i = enemyLauncherCnt; --i >= 0;) {
                RobotInfo enemy = enemyLaunchers[i];
                int dis = enemy.location.distanceSquaredTo(rc.getLocation());
                if (enemy != deadTarget && dis < minDis) {
                    cachedEnemyLocation = enemy.location;
                    minDis = dis;
                }
            }
            if (cachedEnemyLocation == null && backupTarget != null && backupTarget != deadTarget) {
                cachedEnemyLocation = backupTarget.location;
            }
            if (cachedEnemyLocation != null && rc.isMovementReady()) {
                kite(cachedEnemyLocation);
            }
        }
        if (Comm.getClosestFriendlyIslandIndex() != 0 && rc.getHealth() < HEALING_CUTOFF) {
            // go back to heal if possible, no chasing
            return;
        }
        if (rc.isMovementReady() && rc.isActionReady()) {
            if (chaseTarget != null) {
                cachedEnemyLocation = chaseTarget.location;
                cachedRound = rc.getRoundNum();
                if (rc.getHealth() > chaseTarget.health || ourTeamStrength > 2 || chaseTarget.type != RobotType.LAUNCHER) {
                    chase(chaseTarget.location);
                } else { // we are at disadvantage, pull back
                    kite(chaseTarget.location);
                }
            } else if (cachedEnemyLocation != null && rc.getRoundNum() - cachedRound <= 2) {
                chase(cachedEnemyLocation);
            }
        }

    }

    private static void chase(MapLocation location) throws GameActionException{
        Direction forwardDir = rc.getLocation().directionTo(location);
        Direction[] dirs = {forwardDir, forwardDir.rotateLeft(), forwardDir.rotateRight(),
                forwardDir.rotateLeft().rotateLeft(), forwardDir.rotateRight().rotateRight()};
        Direction bestDir = null;
        int minCanSee = Integer.MAX_VALUE;
        boolean bestHasCloud = false;
        // pick a direction to chase to minimize the number of enemy launchers that can see us
        for (Direction dir : dirs) {
            if (rc.canMove(dir) && rc.getLocation().add(dir).distanceSquaredTo(location) <= ATTACK_DIS) {
                int canSee = 0;
                boolean hasCloud = rc.senseCloud(rc.getLocation().add(dir));
                for (int i = enemyLauncherCnt; --i >= 0;){
                    int newDis = rc.getLocation().add(dir).distanceSquaredTo(enemyLaunchers[i].location);
                    if (newDis <= 4 || (newDis <= VISION_DIS && !hasCloud && !enemyInCloud[i])) {
                        canSee++;
                    }
                }
                if (minCanSee > canSee) {
                    bestDir = dir;
                    minCanSee = canSee;
                    bestHasCloud = hasCloud;
                } else if (minCanSee == canSee && bestHasCloud && !hasCloud) {
                    // we then prefer to chase into a grid without cloud, TODO more testing on this
                    bestDir = dir;
                    bestHasCloud = false;
                } else if (minCanSee == canSee && bestHasCloud == hasCloud && isDiagonal(bestDir) && !isDiagonal(dir)) {
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
        Direction[] dirs = {Direction.CENTER, backDir, backDir.rotateLeft(), backDir.rotateRight(),
                backDir.rotateLeft().rotateLeft(), backDir.rotateRight().rotateRight()};
        Direction bestDir = null;
        int minCanSee = Integer.MAX_VALUE;
        boolean bestHasCloud = false;
        // pick a direction to kite back to minimize the number of enemy launchers that can see us
        for (Direction dir : dirs) {
            if (rc.canMove(dir)) {
                int canSee = 0;
                boolean hasCloud = rc.senseCloud(rc.getLocation().add(dir));
                for (int i = enemyLauncherCnt; --i >= 0;){
                    int newDis = rc.getLocation().add(dir).distanceSquaredTo(enemyLaunchers[i].location);
                    if (newDis <= 4 || (newDis <= VISION_DIS && !hasCloud && !enemyInCloud[i])) {
                        canSee++;
                    }
                }
                if (minCanSee > canSee) {
                    bestDir = dir;
                    minCanSee = canSee;
                    bestHasCloud = hasCloud;
                } else if (minCanSee == canSee && bestHasCloud && !hasCloud) { // we then prefer to kite into a grid without cloud
                    bestDir = dir;
                    bestHasCloud = false;
                } else if (minCanSee == canSee && bestHasCloud == hasCloud && isDiagonal(bestDir) && !isDiagonal(dir)) {
                    // from Cow: we prefer non-diagonal moves to preserve formation
                    bestDir = dir;
                }
            }
        }
        if (bestDir != null && bestDir != Direction.CENTER){
            indicator += "kite,";
            rc.move(bestDir);
        }
    }

    private static final int[] GUESS_ATTACK_DX = {-4, -3, -3, -3, -3, -3, -2, -2, -2, -2, -2, -2, -1, -1, -1, -1, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 4};
    private static final int[] GUESS_ATTACK_DY = {0, -2, -1, 0, 1, 2, -3, -2, -1, 1, 2, 3, -3, -2, 2, 3, -4, -3, 3, 4, -3, -2, 2, 3, -3, -2, -1, 1, 2, 3, -2, -1, 0, 1, 2, 0};
    private static void tryGuessAttack() throws GameActionException {
        // costs 800 bytecode
        // first try to attack cached target and its adjacent squares
        if (cachedEnemyLocation != null
                && rc.getRoundNum() - cachedRound <= 8) {
            Direction targetDir = rc.getLocation().directionTo(cachedEnemyLocation);
            for (int i = 9; --i >= 0;) {
                MapLocation loc = null;
                switch (i) {
                    case 8: loc = cachedEnemyLocation; break;
                    case 7: loc = cachedEnemyLocation.add(targetDir); break;
                    case 6: loc = cachedEnemyLocation.add(targetDir.opposite()); break;
                    case 5: loc = cachedEnemyLocation.add(targetDir.rotateLeft()); break;
                    case 4: loc = cachedEnemyLocation.add(targetDir.rotateRight()); break;
                    case 3: loc = cachedEnemyLocation.add(targetDir.opposite().rotateRight()); break;
                    case 2: loc = cachedEnemyLocation.add(targetDir.opposite().rotateLeft()); break;
                    case 1: loc = cachedEnemyLocation.add(targetDir.rotateRight().rotateRight()); break;
                    case 0: loc = cachedEnemyLocation.add(targetDir.rotateLeft().rotateLeft()); break;
                }
                if (!rc.canSenseLocation(loc) && rc.canAttack(loc)) {
                    char val = MapRecorder.vals[loc.x * mapHeight + loc.y];
                    if ((val & MapRecorder.SEEN_BIT) != 0
                            && (val & MapRecorder.PASSIABLE_BIT) == 0
                            && val != MapRecorder.WITHIN_HQ_RANGE) {
                        continue; // ignore recorded walls
                    }
                    indicator += String.format("guesscache%s,", loc);
                    rc.attack(loc);
                    return;
                }
            }
        }

        MapLocation guessTarget = null;
        if (rc.senseCloud(rc.getLocation())) { // I am in a cloud, try attack a tile I can't see
            MapLocation closestEnemyHQ = getClosestLoc(Comm.enemyHQLocations);
            int disToTarget = Integer.MAX_VALUE;
            int curX = rc.getLocation().x, curY = rc.getLocation().y;
            for (int i = 10; --i >= 0;) {
                int j = FastMath.rand256() % GUESS_ATTACK_DX.length;
                MapLocation loc = new MapLocation(curX + GUESS_ATTACK_DX[j], curY + GUESS_ATTACK_DY[j]);
                if (!rc.onTheMap(loc))
                    continue;
                char val = MapRecorder.vals[loc.x * mapHeight + loc.y];
                if ((val & MapRecorder.SEEN_BIT) != 0
                        && (val & MapRecorder.PASSIABLE_BIT) == 0
                        && val != MapRecorder.WITHIN_HQ_RANGE) {
                    continue; // ignore recorded walls
                }
                int dis = loc.distanceSquaredTo(closestEnemyHQ);
                if (dis != 0 && dis < disToTarget) {
                    guessTarget = loc;
                    disToTarget = dis;
                }
            }
            if (guessTarget != null && rc.canAttack(guessTarget)) {
                indicator += String.format("guess%s,", guessTarget);
                rc.attack(guessTarget);
                return;
            }
        }

        MapLocation[] clouds = rc.senseNearbyCloudLocations();
        if (rc.isActionReady() && clouds.length > 0) {
            MapLocation closestEnemyHQ = getClosestLoc(Comm.enemyHQLocations);
            int disToTarget = Integer.MAX_VALUE;
            for (int i = 10; --i >= 0;) {
                int j = FastMath.rand256() % clouds.length;
                MapLocation loc = clouds[j];
                int dis = closestEnemyHQ.distanceSquaredTo(loc);
                if (dis != 0 && dis < disToTarget) {
                    disToTarget = dis;
                    guessTarget = loc;
                }
            }
            if (guessTarget != null && rc.canAttack(guessTarget)) {
                indicator += String.format("guess%s,", guessTarget);
                rc.attack(guessTarget);
            }
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
                int friendEnemyDis = friendlyLaunchers[friend_i].location.distanceSquaredTo(enemy.location);
                if (friendEnemyDis <= 4 || (friendEnemyDis <= ATTACK_DIS && !enemyInCloud[enemy_i] && !friendInCloud[friend_i])) {
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

