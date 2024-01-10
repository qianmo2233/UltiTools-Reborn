package com.ultikits.plugins.services;

import com.ultikits.plugins.BasicFunctions;
import com.ultikits.plugins.data.WarpData;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.entities.common.WorldLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.List;

public class WarpService {

    public static Location toLocation(WorldLocation worldLocation) {
        return new Location(Bukkit.getWorld(worldLocation.getWorld()), worldLocation.getX(), worldLocation.getY(), worldLocation.getZ(), worldLocation.getYaw(), worldLocation.getPitch());
    }

    public void addWarp(String name, Location location) {
        WarpData warpData = new WarpData();
        warpData.setName(name);
        WorldLocation worldLocation = new WorldLocation(location);
        warpData.setLocation(worldLocation);
        BasicFunctions.getInstance().getDataOperator(WarpData.class).insert(warpData);
    }

    public void removeWarp(String name) {
        BasicFunctions.getInstance().getDataOperator(WarpData.class).del(
                WhereCondition.builder().column("name").value(name).build()
        );
    }

    public Location getWarpLocation(String name) {
        List<WarpData> warpData = BasicFunctions.getInstance().getDataOperator(WarpData.class).getAll(
                WhereCondition.builder().column("name").value(name).build()
        );
        if (warpData.isEmpty()) {
            return null;
        }
        return toLocation(warpData.get(0).getLocation());
    }

    public List<WarpData> getAllWarps() {
        return BasicFunctions.getInstance().getDataOperator(WarpData.class).getAll();
    }
}