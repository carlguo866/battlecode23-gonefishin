package submit26_final.util;

import battlecode.common.MapLocation;

public class FastIterableLocSet {
    public StringBuilder keys;
    public int maxlen;
    public MapLocation[] locs;
    public int size;
    private int earliestRemoved;

    public FastIterableLocSet() {
        this(100);
    }

    public FastIterableLocSet(int len) {
        keys = new StringBuilder();
        maxlen = len;
        locs = new MapLocation[maxlen];
    }

    private String locToStr(MapLocation loc) {
        return "^" + (char)(loc.x) + (char)(loc.y);
    }

    public void add(MapLocation loc) {
        String key = locToStr(loc);
        if (keys.indexOf(key) == -1) {
            if (size == maxlen)
                return;
            keys.append(key);
            size++;
        }

    }

    public void add(int x, int y) {
        String key = "^" + (char)x + (char)y;
        if (keys.indexOf(key) == -1) {
            keys.append(key);
            size++;
        }
    }

    public void remove(MapLocation loc) {
        String key = locToStr(loc);
        int index;
        if ((index = keys.indexOf(key)) >= 0) {
            keys.delete(index, index + 3);
            size--;

            if(earliestRemoved > index)
                earliestRemoved = index;
        }
    }

    public void remove(int x, int y) {
        String key = "^" + (char)x + (char)y;
        int index;
        if ((index = keys.indexOf(key)) >= 0) {
            keys.delete(index, index + 3);
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

    public void updateIterable() {
        for (int i = earliestRemoved / 3; i < size; i++) {
            locs[i] = new MapLocation(keys.charAt(i*3+1), keys.charAt(i*3+2));
        }
        earliestRemoved = size * 3;
    }

    public void replace(String newSet) {
        keys.replace(0, keys.length(), newSet);
        size = newSet.length() / 3;
    }
}