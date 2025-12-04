package dev.masterhunter.addon.modules.Hunting;

import dev.masterhunter.addon.Main;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.map.mods.SupportMods;

import java.util.HashSet;
import java.util.Set;

public class BrokenPortalWaypoints extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder()
        .name("scan-radius")
        .description("The radius in blocks to scan for portal frames.")
        .defaultValue(32)
        .min(8)
        .sliderMin(8)
        .sliderMax(128)
        .build()
    );

    private final Setting<Integer> duplicateRadius = sgGeneral.add(new IntSetting.Builder()
        .name("duplicate-radius")
        .description("Minimum distance in blocks before creating another waypoint.")
        .defaultValue(16)
        .min(4)
        .sliderMin(4)
        .sliderMax(64)
        .build()
    );

    private final Setting<Boolean> detectUnlit = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-unlit")
        .description("Detect portal frames that are complete but not lit.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectIncomplete = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-incomplete")
        .description("Detect portal frames missing one or more obsidian blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxMissingBlocks = sgGeneral.add(new IntSetting.Builder()
        .name("max-missing-blocks")
        .description("Maximum number of missing obsidian blocks to still consider it a portal frame.")
        .defaultValue(2)
        .min(1)
        .sliderMin(1)
        .sliderMax(5)
        .visible(detectIncomplete::get)
        .build()
    );

    private final Setting<Boolean> sendNotifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Send notifications when broken portals are found.")
        .defaultValue(true)
        .build()
    );

    private final Set<String> waypointLocations = new HashSet<>();
    private final Set<String> checkedLocations = new HashSet<>();
    private int tickCounter = 0;
    private static final int TICK_INTERVAL = 40; // Check every 2 seconds (40 ticks)
    private static final int CLEANUP_INTERVAL = 2400; // Clear checked locations every 2 minutes
    private int cleanupCounter = 0;

    public BrokenPortalWaypoints() {
        super(Main.CATEGORY, "broken-portal-waypoints", "Creates waypoints for portals that aren't lit or are missing blocks.");
    }

    @Override
    public void onActivate() {
        waypointLocations.clear();
        checkedLocations.clear();
        tickCounter = 0;
        cleanupCounter = 0;
    }

    @Override
    public void onDeactivate() {
        tickCounter = 0;
        cleanupCounter = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Periodic cleanup to allow re-scanning areas
        cleanupCounter++;
        if (cleanupCounter >= CLEANUP_INTERVAL) {
            checkedLocations.clear();
            cleanupCounter = 0;
        }

        // Only check every TICK_INTERVAL ticks to reduce performance impact
        tickCounter++;
        if (tickCounter < TICK_INTERVAL) return;
        tickCounter = 0;

        scanForBrokenPortals();
    }

    private void scanForBrokenPortals() {
        BlockPos playerPos = mc.player.getBlockPos();
        int radius = scanRadius.get();

        // Scan for obsidian blocks and check if they form portal frames
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = playerPos.add(x, y, z);
                    
                    // Skip already checked locations
                    String locationKey = checkPos.getX() + "," + checkPos.getY() + "," + checkPos.getZ();
                    if (checkedLocations.contains(locationKey)) continue;
                    
                    // Check if this is an obsidian block
                    if (mc.world.getBlockState(checkPos).getBlock() != Blocks.OBSIDIAN) continue;
                    
                    // Try to find a portal frame starting from this obsidian
                    checkPortalFrame(checkPos);
                }
            }
        }
    }

    private void checkPortalFrame(BlockPos obsidianPos) {
        // Check for portal frames in both orientations (X-axis and Z-axis)
        checkPortalFrameOrientation(obsidianPos, Direction.NORTH);
        checkPortalFrameOrientation(obsidianPos, Direction.EAST);
    }

    private void checkPortalFrameOrientation(BlockPos obsidianPos, Direction facing) {
        // A standard nether portal frame:
        // - 4 blocks wide (2 obsidian corners + 2 bottom obsidian)
        // - 5 blocks tall (2 obsidian top + 2 bottom + 3 air spaces)
        // - Total obsidian: 10 blocks (minimum portal)
        // 
        // Layout (looking at X-Z plane with Y up):
        //   O O      <- Top corners at Y+4
        //   . .      <- Air at Y+3
        //   . .      <- Air at Y+2
        //   . .      <- Air at Y+1
        //   O O      <- Bottom corners at Y+0
        
        Direction right = facing.rotateYClockwise();
        
        // Try to find the bottom-left corner of a portal frame
        // Check multiple possible positions relative to this obsidian block
        for (int yOffset = 0; yOffset >= -4; yOffset--) {
            for (int hOffset = 0; hOffset >= -3; hOffset--) {
                BlockPos basePos = obsidianPos.offset(right, hOffset).up(yOffset);
                PortalFrameResult result = checkPortalAtPosition(basePos, facing);
                
                if (result != null) {
                    processPortalResult(result, basePos);
                    return;
                }
            }
        }
    }

    private PortalFrameResult checkPortalAtPosition(BlockPos bottomLeft, Direction facing) {
        Direction right = facing.rotateYClockwise();
        
        // Expected obsidian positions for a standard portal (10 blocks):
        // Bottom: 2 blocks
        // Left pillar: 3 blocks  
        // Right pillar: 3 blocks
        // Top: 2 blocks
        
        int obsidianCount = 0;
        int missingCount = 0;
        boolean hasPortalBlocks = false;
        
        // Check bottom row (2 blocks at Y=0)
        for (int i = 0; i < 2; i++) {
            BlockPos pos = bottomLeft.offset(right, i + 1);
            if (isObsidian(pos)) obsidianCount++;
            else missingCount++;
        }
        
        // Check left pillar (3 blocks at Y=1,2,3 at left edge)
        for (int y = 1; y <= 3; y++) {
            BlockPos pos = bottomLeft.up(y);
            if (isObsidian(pos)) obsidianCount++;
            else missingCount++;
        }
        
        // Check right pillar (3 blocks at Y=1,2,3 at right edge)  
        for (int y = 1; y <= 3; y++) {
            BlockPos pos = bottomLeft.offset(right, 3).up(y);
            if (isObsidian(pos)) obsidianCount++;
            else missingCount++;
        }
        
        // Check top row (2 blocks at Y=4)
        for (int i = 0; i < 2; i++) {
            BlockPos pos = bottomLeft.offset(right, i + 1).up(4);
            if (isObsidian(pos)) obsidianCount++;
            else missingCount++;
        }
        
        // Check for portal blocks in the interior (should be air or portal)
        for (int y = 1; y <= 3; y++) {
            for (int h = 1; h <= 2; h++) {
                BlockPos pos = bottomLeft.offset(right, h).up(y);
                BlockState state = mc.world.getBlockState(pos);
                if (state.getBlock() == Blocks.NETHER_PORTAL) {
                    hasPortalBlocks = true;
                }
            }
        }
        
        // Determine if this is a valid broken/unlit portal
        int maxMissing = maxMissingBlocks.get();
        
        // Need at least 8 obsidian to be recognizable as a portal frame
        if (obsidianCount < 8) return null;
        
        // Check for unlit portal (all 10 obsidian, no portal blocks)
        if (detectUnlit.get() && obsidianCount == 10 && !hasPortalBlocks) {
            return new PortalFrameResult(bottomLeft, PortalType.UNLIT, 0);
        }
        
        // Check for incomplete portal (missing some obsidian, no portal blocks)
        if (detectIncomplete.get() && missingCount > 0 && missingCount <= maxMissing && !hasPortalBlocks) {
            return new PortalFrameResult(bottomLeft, PortalType.INCOMPLETE, missingCount);
        }
        
        return null;
    }

    private boolean isObsidian(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock() == Blocks.OBSIDIAN;
    }

    private void processPortalResult(PortalFrameResult result, BlockPos basePos) {
        // Create a unique key for this portal frame to avoid duplicates
        String frameKey = basePos.getX() + "," + basePos.getY() + "," + basePos.getZ();
        checkedLocations.add(frameKey);
        
        // Check if we already have a waypoint nearby
        int centerX = basePos.getX() + 1;
        int centerY = basePos.getY() + 2;
        int centerZ = basePos.getZ();
        
        if (hasNearbyWaypoint(centerX, centerY, centerZ, duplicateRadius.get())) {
            return;
        }
        
        // Create waypoint
        WaypointSet waypointSet = getWaypointSet();
        if (waypointSet == null) return;
        
        String waypointName;
        int color;
        String symbol;
        
        if (result.type == PortalType.UNLIT) {
            waypointName = "Unlit Portal";
            color = 5; // Purple - for unlit portals
            symbol = "P";
        } else {
            waypointName = "Broken Portal -" + result.missingBlocks;
            color = 6; // Orange - for incomplete portals
            symbol = "B";
        }
        
        Waypoint waypoint = new Waypoint(
            centerX,
            centerY,
            centerZ,
            waypointName,
            symbol,
            color,
            0,
            false
        );
        
        waypointSet.add(waypoint);
        SupportMods.xaeroMinimap.requestWaypointsRefresh();
        
        String locationKey = centerX + "," + centerY + "," + centerZ;
        waypointLocations.add(locationKey);
        
        if (sendNotifications.get()) {
            if (result.type == PortalType.UNLIT) {
                info("Found unlit portal at (highlight)%s(default), (highlight)%s(default), (highlight)%s(default)", centerX, centerY, centerZ);
            } else {
                info("Found broken portal (missing %s blocks) at (highlight)%s(default), (highlight)%s(default), (highlight)%s(default)", result.missingBlocks, centerX, centerY, centerZ);
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

    @Override
    public String getInfoString() {
        return String.valueOf(waypointLocations.size());
    }

    private enum PortalType {
        UNLIT,
        INCOMPLETE
    }

    private record PortalFrameResult(BlockPos position, PortalType type, int missingBlocks) {}
}
