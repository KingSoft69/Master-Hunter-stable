package dev.masterhunter.addon.modules.Hunting;

import dev.masterhunter.addon.Main;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.map.mods.SupportMods;
import xaero.common.minimap.waypoints.Waypoint;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MobTrackerPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> mobThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("mob-threshold")
        .description("The minimum number of mobs nearby to create a waypoint.")
        .defaultValue(5)
        .min(1)
        .sliderMin(1)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder()
        .name("scan-radius")
        .description("The radius in blocks to scan for mobs.")
        .defaultValue(32)
        .min(8)
        .sliderMin(8)
        .sliderMax(128)
        .build()
    );

    private final Set<String> waypointLocations = new HashSet<>();
    private int tickCounter = 0;
    private static final int TICK_INTERVAL = 20; // Check every second (20 ticks)

    public MobTrackerPlus() {
        super(Main.CATEGORY, "mob-tracker-plus", "Creates waypoints when a certain number of mobs are encountered in the Nether.");
    }

    @Override
    public void onActivate() {
        waypointLocations.clear();
    }

    @Override
    public void onDeactivate() {
        tickCounter = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Only work in the Nether
        if (mc.world.getRegistryKey() != World.NETHER) return;

        // Only check every TICK_INTERVAL ticks to reduce performance impact
        tickCounter++;
        if (tickCounter < TICK_INTERVAL) return;
        tickCounter = 0;

        // Get nearby mobs
        Box searchBox = new Box(
            mc.player.getX() - scanRadius.get(),
            mc.player.getY() - scanRadius.get(),
            mc.player.getZ() - scanRadius.get(),
            mc.player.getX() + scanRadius.get(),
            mc.player.getY() + scanRadius.get(),
            mc.player.getZ() + scanRadius.get()
        );

        List<Entity> entities = mc.world.getOtherEntities(mc.player, searchBox);
        
        // Count mobs (entities that implement Monster interface)
        int mobCount = 0;
        for (Entity entity : entities) {
            if (entity instanceof Monster) {
                mobCount++;
            }
        }

        // Create waypoint if threshold is met
        if (mobCount >= mobThreshold.get()) {
            int x = (int) mc.player.getX();
            int y = (int) mc.player.getY();
            int z = (int) mc.player.getZ();
            
            // Check if we already have a waypoint nearby (within 16 blocks)
            if (!hasNearbyWaypoint(x, y, z, 16)) {
                WaypointSet waypointSet = getWaypointSet();
                if (waypointSet != null) {
                    createWaypoint(waypointSet, x, y, z, mobCount);
                    String locationKey = x + "," + y + "," + z;
                    waypointLocations.add(locationKey);
                    info("Created waypoint at (highlight)%s(default), (highlight)%s(default), (highlight)%s(default) with (highlight)%s(default) mobs nearby.", x, y, z, mobCount);
                }
            }
        }
    }

    private boolean hasNearbyWaypoint(int x, int y, int z, int radius) {
        for (String location : waypointLocations) {
            String[] coords = location.split(",");
            int wx = Integer.parseInt(coords[0]);
            int wy = Integer.parseInt(coords[1]);
            int wz = Integer.parseInt(coords[2]);
            
            double distance = Math.sqrt(
                Math.pow(x - wx, 2) + 
                Math.pow(y - wy, 2) + 
                Math.pow(z - wz, 2)
            );
            
            if (distance < radius) {
                return true;
            }
        }
        return false;
    }

    private WaypointSet getWaypointSet() {
        MinimapSession minimapSession = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (minimapSession == null) return null;
        MinimapWorld currentWorld = minimapSession.getWorldManager().getCurrentWorld();
        if (currentWorld == null) return null;
        return currentWorld.getCurrentWaypointSet();
    }

    private void createWaypoint(WaypointSet waypointSet, int x, int y, int z, int mobCount) {
        String waypointName = "Mobs:" + mobCount;
        
        // Set color based on mob count
        int color = 0;
        if (mobCount < 10) color = 14; // Yellow
        else if (mobCount < 20) color = 6; // Orange
        else color = 4; // Red

        Waypoint waypoint = new Waypoint(
            x,
            y,
            z,
            waypointName,
            "M",
            color,
            0,
            false
        );

        waypointSet.add(waypoint);
        SupportMods.xaeroMinimap.requestWaypointsRefresh();
    }

    @Override
    public String getInfoString() {
        return String.valueOf(waypointLocations.size());
    }
}
