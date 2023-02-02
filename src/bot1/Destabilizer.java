package bot1;

import battlecode.common.*;

import java.util.Map;

public class Destabilizer extends Unit {
    private static final int MAX_HEALTH = 300;
    private static final int[] ATTACK_DX = {0, 3, 0, -3, 0,  4, 2,  -2, -2};
    private static final int[] ATTACK_DY = {0, 0, 3, 0,  -3, 2, -4, -4, 4};

    private static final int ATTACKING = 1;
    private static final int HEALING = 2;
    private static final int FIGHTING_ISLAND = 3;
    private static int state = ATTACKING;

    private static int enemyHQID = 0;
    private static MapLocation enemyHQLoc = null;
    private static MapLocation homeHQLoc = null;
    private static MapLocation centerOfEnemy = null;

    private static MapLocation closestFriend = null;
    private static MapLocation closestEnemy = null;
    private static int lastEnemySeenRound = 0;

    public static void run() throws GameActionException {
        if (turnCount == 0) {
            homeHQLoc = getClosestLoc(Comm.friendlyHQLocations);
            enemyHQID = getClosestID(Comm.enemyHQLocations);
            enemyHQLoc = Comm.enemyHQLocations[enemyHQID];
        }

        sense();

        if (rc.isActionReady()) {
            if (closestEnemy != null) {
                tryAttack(closestEnemy);
            }
//            else {
//                MapLocation closestEnemyHQ = getClosestLoc(Comm.enemyHQLocations);
//                tryAttack(closestEnemyHQ);
//            }
        }
        if (rc.isMovementReady()) {
            if (closestEnemy != null) {
                kite(closestEnemy);
            } else {
                if (!tryIslandStuff()) {
                    if (closestFriend != null) {
                        follow(closestFriend);
                    } else {
                        moveToward(enemyHQLoc);
                    }
                }
            }
            sense();
        }

        MapRecorder.recordSym(500);
    }

    private static void sense() {
        int x = 0, y = 0, cnt = 0;
        if (rc.getRoundNum() - lastEnemySeenRound > 5) {
            closestEnemy = null;
        }
        RobotInfo[] robots = rc.senseNearbyRobots();
        closestFriend = null;
        int lowID = Integer.MAX_VALUE;
        for (int i = robots.length; --i >= 0;) {
            RobotInfo robot = robots[i];
            if (robot.team == oppTeam) {
                switch (robot.type) {
                    case HEADQUARTERS:
                        MapRecorder.reportEnemyHQ(robot.location);
                        break;
                    case LAUNCHER:
                    case DESTABILIZER:
                        int dis = robot.location.distanceSquaredTo(rc.getLocation());
                        if (closestEnemy == null || dis < closestEnemy.distanceSquaredTo(rc.getLocation())) {
                            closestEnemy = robot.location;
                            lastEnemySeenRound = rc.getRoundNum();
                        }
                    default:
                        x += robot.location.x;
                        y += robot.location.y;
                        cnt++;
                }
            } else if (robot.team == myTeam && robot.type == RobotType.LAUNCHER) {
                if (robot.getID() < lowID) {
                    closestFriend = robot.location;
                    lowID = robot.getID();
                }
            }
        }
        centerOfEnemy = cnt == 0? null : new MapLocation(x / cnt, y / cnt);
    }

    private static void kite(MapLocation loc) throws GameActionException {
        Direction backDir = rc.getLocation().directionTo(loc).opposite();
        Direction[] dirs = {backDir, backDir.rotateLeft(), backDir.rotateRight(),
                backDir.rotateLeft().rotateLeft(), backDir.rotateRight().rotateRight()};
        Direction bestDir = null;
        // pick a direction to kite back to be as far from enemy as possible
        int bestDis = rc.getLocation().distanceSquaredTo(loc);
        for (Direction dir : dirs) {
            if (rc.canMove(dir)) {
                int dis = rc.getLocation().add(dir).distanceSquaredTo(loc);
                if (dis > bestDis) {
                    bestDis = dis;
                    bestDir = dir;
                }
            }
        }
        if (bestDir != null){
            indicator += "kite,";
            rc.move(bestDir);
        }
    }

    private static void tryAttack(MapLocation target) throws GameActionException {
        int bestDis = Integer.MAX_VALUE;
        MapLocation bestTarget = null;
        for (int i = ATTACK_DX.length; --i >= 0;) {
            MapLocation loc = rc.getLocation().translate(ATTACK_DX[i], ATTACK_DY[i]);
            if (!rc.canDestabilize(loc)) {
                continue;
            }
            int dis = loc.distanceSquaredTo(target);
            if (dis < bestDis) {
                bestDis = dis;
                bestTarget = loc;
            }
        }
        if (bestTarget != null) {
            rc.destabilize(bestTarget);
        }
    }

    private static int lastFailedHealingTurn = -1000;
    // returns whether has moved
    private static MapLocation islandTargetLoc = null;
    private static int islandTargetIndex = 0;
    static boolean tryIslandStuff() throws GameActionException {
        // attempt to go heal if less than 100 health, capture enemy island, protect friendly island
        boolean needHeal =  (rc.getHealth() < MAX_HEALTH && state == HEALING) || rc.getHealth() < 200;
        if (state == HEALING && !needHeal) {
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
                    (occupyingTeam == myTeam && rc.senseAnchor(islandIndex).totalHealth < Anchor.STANDARD.totalHealth))) {
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
}
