package bot1;

import battlecode.common.Direction;

import java.util.Random;

public class Constants {
    public static final int LAUNCHER_COST_MN = 45;
    public static final int LAUNCHER_ATTACK_DIS = 16;

    public static final int CARRIER_COST_AD = 50;
    public static final int CARRIER_ATTACK_DIS = 9;

    public static final int AMP_COST_AD = 30;
    public static final int AMP_COST_MN = 15;

    public static final int ANCHOR_COST_MN = 80;
    public static final int ANCHOR_COST_AD = 80;

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

    public static final String ONE_HUNDRED_LEN_STRING = "\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0";
    public static final String SIX_HUNDRED_LEN_STRING = ONE_HUNDRED_LEN_STRING + ONE_HUNDRED_LEN_STRING + ONE_HUNDRED_LEN_STRING + ONE_HUNDRED_LEN_STRING + ONE_HUNDRED_LEN_STRING + ONE_HUNDRED_LEN_STRING;
    public static final String MAP_LEN_STRING = SIX_HUNDRED_LEN_STRING + SIX_HUNDRED_LEN_STRING + SIX_HUNDRED_LEN_STRING + SIX_HUNDRED_LEN_STRING + SIX_HUNDRED_LEN_STRING + SIX_HUNDRED_LEN_STRING;
}
