package bot1;

import battlecode.common.*;
import static bot1.Constants.*;

public class Headquarter extends Unit {
    public static void run() throws GameActionException {
        if (turnCount == 0) {
            // first turn all HQ report, do nothing
            Comm.HQInit(rc.getLocation(), rc.getID());
            for (WellInfo well : rc.senseNearbyWells()) {
                Comm.reportWells(well);
            }
        } else if (turnCount >= 1) {
            assert Comm.closestWells[ResourceType.MANA.resourceID] != null;
            Direction direction = rc.getLocation().directionTo(Comm.closestWells[ResourceType.MANA.resourceID]);
            MapLocation location = rc.getLocation().add(direction);
            if(rc.canBuildRobot(RobotType.CARRIER, location)) {
                rc.buildRobot(RobotType.CARRIER, location);
                Comm.setSpawnQ(rc.getID(), location.x, location.y, Carrier.MINE_MN);
            }

            // launcher built on opposite side, fix later
            location = rc.getLocation().add(direction.opposite());
            if(rc.canBuildRobot(RobotType.LAUNCHER, location)) {
                rc.buildRobot(RobotType.LAUNCHER, location);
                Comm.setSpawnQ(rc.getID() + 1, location.x, location.y, Carrier.MINE_MN);
            }
        }
    }
}
