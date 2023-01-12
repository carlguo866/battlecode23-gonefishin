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
        } else if (turnCount == 1) {

        }
    }
}
