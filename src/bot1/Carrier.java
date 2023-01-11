package bot1;

import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;

import battlecode.common.*;
import static bot1.Constants.*;

public class Carrier extends RobotPlayer {
    /**
     * Run a single turn for a Carrier.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    static void run () throws GameActionException {
        if (rc.getAnchor() != null) {
            // If I have an anchor singularly focus on getting it to the first island I see
            int[] islands = rc.senseNearbyIslands();
            Set<MapLocation> islandLocs = new HashSet<>();
            for (int id : islands) {
                MapLocation[] thisIslandLocs = rc.senseNearbyIslandLocations(id);
                islandLocs.addAll(Arrays.asList(thisIslandLocs));
            }
            if (islandLocs.size() > 0) {
                MapLocation islandLocation = islandLocs.iterator().next();
                rc.setIndicatorString("Moving my anchor towards " + islandLocation);
                while (!rc.getLocation().equals(islandLocation)) {
                    Direction dir = rc.getLocation().directionTo(islandLocation);
                    if (rc.canMove(dir)) {
                        rc.move(dir);
                    }
                }
                if (rc.canPlaceAnchor()) {
                    rc.setIndicatorString("Huzzah, placed anchor!");
                    rc.placeAnchor();
                }
            }
        }
        // Try to gather from squares around us.
        MapLocation me = rc.getLocation();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                MapLocation wellLocation = new MapLocation(me.x + dx, me.y + dy);
                if (rc.canCollectResource(wellLocation, -1)) {
                    if (rng.nextBoolean()) {
                        rc.collectResource(wellLocation, -1);
                        rc.setIndicatorString("Collecting, now have, AD:" +
                                rc.getResourceAmount(ResourceType.ADAMANTIUM) +
                                " MN: " + rc.getResourceAmount(ResourceType.MANA) +
                                " EX: " + rc.getResourceAmount(ResourceType.ELIXIR));
                    }
                }
            }
        }
        // Occasionally try out the carriers attack
        if (rng.nextInt(20) == 1) {
            RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            if (enemyRobots.length > 0) {
                if (rc.canAttack(enemyRobots[0].location)) {
                    rc.attack(enemyRobots[0].location);
                }
            }
        }

        // If we can see a well, move towards it
        WellInfo[] wells = rc.senseNearbyWells();
        if (wells.length > 1 && rng.nextInt(3) == 1) {
            WellInfo well_one = wells[1];
            Direction dir = me.directionTo(well_one.getMapLocation());
            if (rc.canMove(dir))
                rc.move(dir);
        }
        // Also try to move randomly.
        Direction dir = directions[rng.nextInt(directions.length)];
        if (rc.canMove(dir)) {
            rc.move(dir);
        }
    }
}
