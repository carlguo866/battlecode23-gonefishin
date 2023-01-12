package bot1;

import battlecode.common.*;
import static bot1.Constants.*;

public class Headquarter extends Unit {
    public static void setup() throws GameActionException {
        Comm.HQInit(rc.getLocation(), rc.getID());
    }

    public static void run() throws GameActionException {
    }
}
