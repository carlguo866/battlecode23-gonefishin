package submit17;

import battlecode.common.Direction;

import java.util.Random;

public class Constants {
    public static final int LAUNCHER_COST_MN = 60;
    public static final int LAUNCHER_ATTACK_DIS = 16;

    public static final int CARRIER_COST_AD = 50;
    public static final int CARRIER_ATTACK_DIS = 9;

    /** Array containing all the possible movement directions. */
    public static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    /**
     * A random number generator.
     * We will use this RNG to make some random moves. The Random class is provided by the java.util.Random
     * import at the top of this file. Here, we *seed* the RNG with a constant number (6147); this makes sure
     * we get the same sequence of numbers every time this code is run. This is very useful for debugging!
     */
    static final Random rng = new Random(6147);
}
