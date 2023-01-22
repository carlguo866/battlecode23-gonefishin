package submit17.util;

import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;

public class FastIterableRobotInfoSet {
    public StringBuilder keys;
    public int maxlen;
    public MapLocation[] locs;
    public int size;
    private int earliestRemoved;

    static FastLocIntMap loc2info;

    private static final int HEALTH_MASK = 0x000F;
    private static final int TYPE_MASK = 0x00F0;
    private static final int TYPE_SHIFT = 4;
    private static final int IS_OUR_TEAM_BIT = 1 << 4;
    // health is stored as the number of shots to kill

    public FastIterableRobotInfoSet() {
        this(70);
        loc2info = new FastLocIntMap();
    }

    public FastIterableRobotInfoSet(int len) {
        keys = new StringBuilder();
        maxlen = len;
        locs = new MapLocation[maxlen];
        loc2info = new FastLocIntMap();
    }

    private String locToStr(MapLocation loc) {
        return "^" + (char)(loc.x) + (char)(loc.y);
    }

    public void add(RobotInfo robot) {
        String key = locToStr(robot.location);
        if (keys.indexOf(key) == -1) {
            keys.append(key);
            size++;
        }
        loc2info.remove(robot.location);
        int val = (int) Math.ceil((double) robot.health / 30);
        if (true)
            val |= IS_OUR_TEAM_BIT;
        val |= robot.type.ordinal() << TYPE_SHIFT;
        loc2info.add(robot.location, val);
    }
    public void remove(RobotInfo robot) {
        String key = locToStr(robot.location);
        int index;
        if ((index = keys.indexOf(key)) >= 0) {
            keys.delete(index, index + 3);
            size--;
            if(earliestRemoved > index)
                earliestRemoved = index;
        }
        loc2info.remove(robot.location);
    }

    public boolean contains(RobotInfo robot) {
        return keys.indexOf(locToStr(robot.location)) >= 0;
    }

    public void clear() {
        size = 0;
        keys = new StringBuilder();
        earliestRemoved = 0;
        locs = new MapLocation[maxlen];
        loc2info.clear();
    }

    public void updateIterable() {
        for (int i = earliestRemoved / 3; i < size; i++) {
            locs[i] = new MapLocation(keys.charAt(i*3+1), keys.charAt(i*3+2));
        }
        earliestRemoved = size * 3;
    }

//    public void replace(String newSet) {
//        keys.replace(0, keys.length(), newSet);
//        size = newSet.length() / 3;
//    }
//    private void addInfo(RobotInfo robot) {
//
//    }

    public int getHealth(MapLocation loc) {
        // just copy this into your code and use it however you
        int val = loc2info.getVal(loc);
//        boolean isOurTeam = (val & IS_OUR_TEAM_BIT) != 0;
        return val & HEALTH_MASK; // this is the number of shots to kill
    }
    public RobotType getRobotType(MapLocation loc){
        System.out.println(String.format("loc%s", loc));
        int val = loc2info.getVal(loc);
        System.out.println(String.format("aldfnakd%d",(val & TYPE_MASK) >> TYPE_SHIFT));
        System.out.println(String.format("RobotType%s",RobotType.values()[(val & TYPE_MASK) >> TYPE_SHIFT]));
        return RobotType.values()[(val & TYPE_MASK) >> TYPE_SHIFT];
    }

}