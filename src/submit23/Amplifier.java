package submit23;

public class Amplifier extends Unit {
    /*
    public static final int MASTER_ID = 4;
    public static int ampid = MASTER_ID;

    private static int strength;
    private static MapLocation closestEnemy;
    private static MapLocation centerOfFriend;
    private static MapLocation currentLoc;
    private static MapLocation anchorTargetLoc;
    private static int enemyHQID = 0;
    private static MapLocation enemyHQLoc = null;
    private static boolean onTheWay = true; // meaning the amp is just spawned, won't send any command

    private static int anchoringRound = 0;
    private static int anchoringIsland = 0;

    public static void run() throws GameActionException {
        if (turnCount == 0) {
            enemyHQID = getClosestID(Comm.enemyHQLocations);
            enemyHQLoc = Comm.enemyHQLocations[enemyHQID];
            // find the closest HQ that doesn't have a amp, if all HQs have amp, I am master
            for (int i = Comm.numHQ; --i >= 0;) {
                if (Comm.getTargetLocation(i) == null && (
                        ampid == MASTER_ID ||
                        Comm.friendlyHQLocations[i].distanceSquaredTo(rc.getLocation())
                                < Comm.friendlyHQLocations[ampid].distanceSquaredTo(rc.getLocation()))) {
                    ampid = i;
                }
            }
        }

        sense();
        indicator += String.format("amp%d,strength%d,", ampid, strength);

        if (rc.isMovementReady()) {
            if (closestEnemy == null && centerOfFriend == null) {
                moveToward(enemyHQLoc);
            } else {
                Direction bestMoveDir = null;
                int bestScore = getScore(currentLoc);
                for (int i = 8; --i >= 0;) {
                    Direction dir = Constants.directions[i];
                    if (!rc.canMove(dir)) {
                        continue;
                    }
                    int score = getScore(currentLoc.add(dir));
                    if (score > bestScore) {
                        bestScore = score;
                        bestMoveDir = dir;
                    }
                }
                if (bestMoveDir != null) {
                    rc.move(bestMoveDir);
                    currentLoc = rc.getLocation();
                }
            }
        }

        if (closestEnemy != null) {
            onTheWay = false;
        }

        int [] islandIndexes = rc.senseNearbyIslands();
        for (int i = islandIndexes.length; --i >=0; ) {
            int islandIndex = islandIndexes[i];
            MapLocation islandLoc = Comm.getIslandLocation(islandIndex);
            if (islandLoc == null) {
                islandLoc = getClosestLoc(rc.senseNearbyIslandLocations(islandIndex));
            }
            Team occupyingTeam = rc.senseTeamOccupyingIsland(islandIndex);
            int islandState = Comm.ISLAND_NEUTRAL;
            if (occupyingTeam == myTeam) {
                islandState = Comm.ISLAND_FRIENDLY;
            } else if (occupyingTeam == oppTeam) {
                islandState = Comm.ISLAND_ENEMY;
            }
            // decide whether to report this island for anchoring
            if (islandState == Comm.ISLAND_NEUTRAL
                    && !onTheWay
                    && strength >= 600
                    && anchoringIsland == 0
                    && Comm.getAnchorTarget() == 0
                    && rc.getRoundNum() - anchoringRound > 70
                    && rc.getRobotCount() / Comm.numHQ > 15 && Comm.getCarrierCnt() / Comm.numHQ > 5
                    && getClosestDis(centerOfFriend, rc.senseNearbyIslandLocations(islandIndex)) <= 9) {
                System.out.printf("anchoring %d\n", islandIndex);
                anchoringIsland = islandIndex;
                anchoringRound = rc.getRoundNum();
                anchorTargetLoc = islandLoc;
                Comm.setAnchorTarget(anchoringIsland);
            }
            if (anchoringIsland == islandIndex && islandState == Comm.ISLAND_ENEMY) {
                Comm.setAnchorTarget(0);
                anchoringIsland = 0;
                System.out.printf("give up island %d due to enemy\n", anchoringIsland);
            }
            Comm.reportIsland(islandLoc, islandIndex, islandState);
        }

        if (anchoringIsland != 0 && Comm.getIslandStatus(anchoringIsland) == Comm.ISLAND_FRIENDLY) {
            System.out.printf("%d anchored\n", anchoringIsland);
            anchoringIsland = 0;
            anchorTargetLoc = null;
        }

        if (anchoringIsland != 0 && closestEnemy != null && closestEnemy.distanceSquaredTo(anchorTargetLoc) <= 16) {
            System.out.printf("give up island %d due to enemy\n", anchoringIsland);
            anchorTargetLoc = null;
            anchoringIsland = 0;
            Comm.setAnchorTarget(0);
        }

        indicator += String.format("target %s,anchor %d,Comm target %s", anchorTargetLoc, anchoringIsland, Comm.getAnchorTarget());
        Comm.amplifierUpdate(ampid, null);
        Comm.commit_write();
        MapRecorder.recordSym(500);
    }

    private static int getScore(MapLocation loc) {
        int score = 0;
        if (closestEnemy != null) {
            int dis2e = loc.distanceSquaredTo(closestEnemy);
            score += dis2e;
            if (dis2e <= 16) {
                score -= 1000;
            } else if (dis2e <= 20) {
                score -= 100;
            }
        }
        if (centerOfFriend != null) {
            score -= loc.distanceSquaredTo(centerOfFriend);
        }
        if (anchorTargetLoc != null) {
            score -= loc.distanceSquaredTo(anchorTargetLoc) * 3;
        }
        return score;
    }

    private static void sense() {
        currentLoc = rc.getLocation();
        strength = 0;
        closestEnemy = null;
        centerOfFriend = null;
        int x = 0, y = 0, cnt = 0;
        RobotInfo[] robots = rc.senseNearbyRobots();
        for (int i = robots.length; --i >= 0;) {
            RobotInfo robot = robots[i];
            if (robot.team == oppTeam) {
                if (robot.type == RobotType.LAUNCHER) {
                    strength -= robot.health;
                    if (closestEnemy == null ||
                            robot.location.distanceSquaredTo(currentLoc) < closestEnemy.distanceSquaredTo(currentLoc)) {
                        closestEnemy = robot.location;
                    }
                }
            } else {
                if (robot.type == RobotType.LAUNCHER) {
                    strength += robot.health;
                    x += robot.location.x;
                    y += robot.location.y;
                    cnt++;
                }
            }
        }
        centerOfFriend = cnt == 0? null : new MapLocation(x / cnt, y / cnt);
    }
     */
}
