// https://github.com/BSreenivas0713/Battlecode2022/blob/main/src/MPBasic/fast/FastLocIntMap.java
package submit25.util;

import battlecode.common.MapLocation;

public class FastLocIntMap {
    public StringBuilder keys;
    public int size;
    private int earliestRemoved;

    public FastLocIntMap() {
        keys = new StringBuilder();
    }

    private String locToStr(MapLocation loc) {
        return "^" + (char)(loc.x) + (char)(loc.y);
    }

    public void add(MapLocation loc, int val) {
        String key = locToStr(loc);
        if (keys.indexOf(key) == -1) {
            keys.append(key + (char)(val + 0x100));
            size++;
        }
    }

    public void add(int x, int y, int val) {
        String key = "^" + (char)x + (char)y;
        if (keys.indexOf(key) == -1) {
            keys.append(key + (char)(val + 0x100));
            size++;
        }
    }

    public void remove(MapLocation loc) {
        String key = locToStr(loc);
        int index;
        if ((index = keys.indexOf(key)) >= 0) {
            keys.delete(index, index + 4);
            size--;

            if(earliestRemoved > index)
                earliestRemoved = index;
        }
    }

    public void remove(int x, int y) {
        String key = "^" + (char)x + (char)y;
        int index;
        if ((index = keys.indexOf(key)) >= 0) {
            keys.delete(index, index + 4);
            size--;

            if(earliestRemoved > index)
                earliestRemoved = index;
        }
    }

    public boolean contains(MapLocation loc) {
        return keys.indexOf(locToStr(loc)) >= 0;
    }

    public boolean contains(int x, int y) {
        return keys.indexOf("^" + (char)x + (char)y) >= 0;
    }

    public void clear() {
        size = 0;
        keys = new StringBuilder();
        earliestRemoved = 0;
    }

    public int getVal(MapLocation loc) {
        String key = locToStr(loc);
        int idx = keys.indexOf(key);
        if (idx != -1) {
            return (int)keys.charAt(idx + 3) - 0x100;
        }

        return -1;
    }

    public MapLocation[] getKeys() {
        MapLocation[] locs = new MapLocation[size];
        for(int i = 1; i < keys.length(); i += 4) {
            locs[i/4] = new MapLocation((int)keys.charAt(i), (int)keys.charAt(i+1));
        }
        return locs;
    }

    public int[] getInts() {
        int[] ints = new int[size];
        for(int i = 3; i < keys.length(); i += 4) {
            ints[i/4] = (int)keys.charAt(i) - 0x100;
        }
        return ints;
    }
}