package launcher.util;

import battlecode.common.MapLocation;

public class SimpleLauncherInfo {

    public MapLocation loc;
    public int hitRatio;

    public int canAttackFriends;

    public SimpleLauncherInfo(MapLocation locc, int hitRatioo, int friends){
        loc = locc;
        hitRatio = hitRatioo;
        canAttackFriends = friends;
    }
}
