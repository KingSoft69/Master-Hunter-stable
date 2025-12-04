package dev.masterhunter.addon.modules.Render;

import dev.masterhunter.addon.Main;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.*;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.map.mods.SupportMods;
import xaero.common.minimap.waypoints.Waypoint;

import java.util.*;

public class OverworldMobHighlight extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgNotification = settings.createGroup("Notification");
    private final SettingGroup sgWaypoint = settings.createGroup("Waypoint");

    // General settings
    private final Setting<Double> scanRadius = sgGeneral.add(new DoubleSetting.Builder()
        .name("scan-radius")
        .description("Radius in blocks to scan for overworld mobs.")
        .defaultValue(64.0)
        .min(16.0)
        .sliderRange(16.0, 256.0)
        .build()
    );

    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("Ticks between scans for overworld mobs.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 100)
        .build()
    );

    // Render settings
    private final Setting<Boolean> renderBox = sgRender.add(new BoolSetting.Builder()
        .name("render-box")
        .description("Render a box around overworld mobs.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderTracer = sgRender.add(new BoolSetting.Builder()
        .name("render-tracer")
        .description("Render a tracer line to overworld mobs.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the box is rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(renderBox::get)
        .build()
    );

    private final Setting<SettingColor> boxColor = sgRender.add(new ColorSetting.Builder()
        .name("box-color")
        .description("Color of the box around overworld mobs.")
        .defaultValue(new SettingColor(255, 0, 0, 100))
        .visible(renderBox::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Color of the box outline.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(renderBox::get)
        .build()
    );

    private final Setting<SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Color of the tracer line.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(renderTracer::get)
        .build()
    );

    // Notification settings
    private final Setting<Boolean> notifyOnDetection = sgNotification.add(new BoolSetting.Builder()
        .name("notify-on-detection")
        .description("Send a chat notification when an overworld mob is detected.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> notificationCooldown = sgNotification.add(new IntSetting.Builder()
        .name("notification-cooldown")
        .description("Cooldown in seconds between notifications for the same mob.")
        .defaultValue(30)
        .min(5)
        .sliderRange(5, 300)
        .visible(notifyOnDetection::get)
        .build()
    );

    private final Setting<Boolean> playSound = sgNotification.add(new BoolSetting.Builder()
        .name("play-sound")
        .description("Play a sound when an overworld mob is detected.")
        .defaultValue(true)
        .visible(notifyOnDetection::get)
        .build()
    );

    // Waypoint settings
    private final Setting<Boolean> createWaypoint = sgWaypoint.add(new BoolSetting.Builder()
        .name("create-waypoint")
        .description("Create a waypoint when an overworld mob is detected.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> waypointCooldown = sgWaypoint.add(new IntSetting.Builder()
        .name("waypoint-cooldown")
        .description("Cooldown in seconds between waypoint creation for the same mob.")
        .defaultValue(60)
        .min(5)
        .sliderRange(5, 600)
        .visible(createWaypoint::get)
        .build()
    );

    // Internal state
    private final Set<Entity> detectedMobs = new HashSet<>();
    private final Map<UUID, Long> notificationTimestamps = new HashMap<>();
    private final Map<UUID, Long> waypointTimestamps = new HashMap<>();
    private int tickCounter = 0;

    // List of overworld-only mob entity types (passive, neutral, and hostile)
    private static final Set<EntityType<?>> OVERWORLD_ONLY_MOBS = Set.of(
        // Passive mobs
        EntityType.COW,
        EntityType.SHEEP,
        EntityType.PIG,
        EntityType.HORSE,
        EntityType.DONKEY,
        EntityType.MULE,
        EntityType.LLAMA,
        EntityType.TRADER_LLAMA,
        EntityType.CAT,
        EntityType.PARROT,
        EntityType.RABBIT,
        EntityType.OCELOT,
        EntityType.MOOSHROOM,
        EntityType.TURTLE,
        EntityType.COD,
        EntityType.SALMON,
        EntityType.PUFFERFISH,
        EntityType.TROPICAL_FISH,
        EntityType.SQUID,
        EntityType.GLOW_SQUID,
        EntityType.AXOLOTL,
        EntityType.TADPOLE,
        EntityType.ALLAY,
        EntityType.CAMEL,
        EntityType.SNIFFER,
        EntityType.ARMADILLO,
        EntityType.BAT,
        // Neutral mobs
        EntityType.WOLF,
        EntityType.FOX,
        EntityType.PANDA,
        EntityType.POLAR_BEAR,
        EntityType.BEE,
        EntityType.DOLPHIN,
        EntityType.GOAT,
        EntityType.FROG,
        // Hostile mobs
        EntityType.ZOMBIE,
        EntityType.CREEPER,
        EntityType.SPIDER,
        EntityType.CAVE_SPIDER,
        EntityType.WITCH,
        EntityType.HUSK,
        EntityType.DROWNED,
        EntityType.STRAY,
        EntityType.PHANTOM,
        EntityType.PILLAGER,
        EntityType.VINDICATOR,
        EntityType.EVOKER,
        EntityType.RAVAGER,
        EntityType.VEX,
        EntityType.SILVERFISH,
        EntityType.GUARDIAN,
        EntityType.ELDER_GUARDIAN,
        EntityType.SLIME
    );

    // List of Nether-native mobs that should NOT be highlighted (defensive check)
    private static final Set<EntityType<?>> NETHER_NATIVE_MOBS = Set.of(
        EntityType.PIGLIN,
        EntityType.PIGLIN_BRUTE,
        EntityType.ZOMBIFIED_PIGLIN,
        EntityType.HOGLIN,
        EntityType.ZOGLIN,
        EntityType.STRIDER,
        EntityType.BLAZE,
        EntityType.GHAST,
        EntityType.MAGMA_CUBE,
        EntityType.WITHER_SKELETON,
        EntityType.ENDERMAN // Can spawn in nether naturally
    );

    public OverworldMobHighlight() {
        super(Main.CATEGORY, "overworld-mob-highlight", "Highlights overworld-only mobs in the Nether with red boxes and tracers.");
    }

    @Override
    public void onActivate() {
        detectedMobs.clear();
        notificationTimestamps.clear();
        waypointTimestamps.clear();
        tickCounter = 0;
    }

    @Override
    public void onDeactivate() {
        detectedMobs.clear();
        notificationTimestamps.clear();
        waypointTimestamps.clear();
        tickCounter = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Only work in the Nether
        if (mc.world.getRegistryKey() != World.NETHER) {
            detectedMobs.clear();
            return;
        }

        // Only scan every scanInterval ticks
        tickCounter++;
        if (tickCounter < scanInterval.get()) return;
        tickCounter = 0;

        // Scan for overworld mobs
        scanForOverworldMobs();
    }

    private void scanForOverworldMobs() {
        detectedMobs.clear();

        // Create search box
        Box searchBox = new Box(
            mc.player.getX() - scanRadius.get(),
            mc.player.getY() - scanRadius.get(),
            mc.player.getZ() - scanRadius.get(),
            mc.player.getX() + scanRadius.get(),
            mc.player.getY() + scanRadius.get(),
            mc.player.getZ() + scanRadius.get()
        );

        // Get all entities in range
        List<Entity> entities = mc.world.getOtherEntities(mc.player, searchBox);

        for (Entity entity : entities) {
            if (isOverworldOnlyMob(entity)) {
                detectedMobs.add(entity);
                notifyIfNeeded(entity);
                createWaypointIfNeeded(entity);
            }
        }
    }

    private boolean isOverworldOnlyMob(Entity entity) {
        EntityType<?> entityType = entity.getType();
        // Must be in the overworld list AND not in the nether native list (defensive check)
        return OVERWORLD_ONLY_MOBS.contains(entityType) && !NETHER_NATIVE_MOBS.contains(entityType);
    }

    private void notifyIfNeeded(Entity entity) {
        if (!notifyOnDetection.get()) return;

        UUID entityId = entity.getUuid();
        long currentTime = System.currentTimeMillis();

        // Check if we've notified about this mob recently
        if (notificationTimestamps.containsKey(entityId)) {
            long lastNotification = notificationTimestamps.get(entityId);
            long cooldownMs = notificationCooldown.get() * 1000L;

            if (currentTime - lastNotification < cooldownMs) {
                return; // Still in cooldown
            }
        }

        // Send notification
        String mobName = entity.getType().getName().getString();
        double distance = mc.player.distanceTo(entity);
        
        info("Overworld mob detected: (highlight)%s(default) at (highlight)%.1f(default) blocks away!", mobName, distance);

        // Play sound if enabled
        if (playSound.get() && mc.player != null) {
            mc.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 1.0f);
        }

        // Update timestamp
        notificationTimestamps.put(entityId, currentTime);
    }

    private void createWaypointIfNeeded(Entity entity) {
        if (!createWaypoint.get()) return;

        UUID entityId = entity.getUuid();
        long currentTime = System.currentTimeMillis();

        // Check if we've created a waypoint for this mob recently
        if (waypointTimestamps.containsKey(entityId)) {
            long lastWaypoint = waypointTimestamps.get(entityId);
            long cooldownMs = waypointCooldown.get() * 1000L;

            if (currentTime - lastWaypoint < cooldownMs) {
                return; // Still in cooldown
            }
        }

        // Get waypoint set
        WaypointSet waypointSet = getWaypointSet();
        if (waypointSet == null) return;

        // Create waypoint at mob location
        int x = (int) entity.getX();
        int y = (int) entity.getY();
        int z = (int) entity.getZ();
        String mobName = entity.getType().getName().getString();
        String waypointName = "Overworld: " + mobName;

        Waypoint waypoint = new Waypoint(
            x,
            y,
            z,
            waypointName,
            "O",
            4, // Red color
            0,
            false
        );

        waypointSet.add(waypoint);
        SupportMods.xaeroMinimap.requestWaypointsRefresh();

        // Update timestamp
        waypointTimestamps.put(entityId, currentTime);
        
        info("Created waypoint for (highlight)%s(default) at (highlight)%s(default), (highlight)%s(default), (highlight)%s(default)", mobName, x, y, z);
    }

    private WaypointSet getWaypointSet() {
        MinimapSession minimapSession = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (minimapSession == null) return null;
        MinimapWorld currentWorld = minimapSession.getWorldManager().getCurrentWorld();
        if (currentWorld == null) return null;
        return currentWorld.getCurrentWaypointSet();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;
        if (mc.world.getRegistryKey() != World.NETHER) return;
        if (detectedMobs.isEmpty()) return;

        Vec3d playerPos = mc.player.getPos();

        for (Entity entity : detectedMobs) {
            if (entity == null || !entity.isAlive()) continue;

            Box box = entity.getBoundingBox();
            Vec3d entityPos = entity.getPos();

            // Render box around mob
            if (renderBox.get()) {
                event.renderer.box(
                    box.minX, box.minY, box.minZ,
                    box.maxX, box.maxY, box.maxZ,
                    boxColor.get(), lineColor.get(), shapeMode.get(),
                    0
                );
            }

            // Render tracer line from player to mob
            if (renderTracer.get()) {
                // Convert SettingColor to Color
                Color tracerColorObj = new Color(
                    tracerColor.get().r,
                    tracerColor.get().g,
                    tracerColor.get().b,
                    tracerColor.get().a
                );

                event.renderer.line(
                    playerPos.x, playerPos.y + mc.player.getEyeHeight(mc.player.getPose()), playerPos.z,
                    entityPos.x, entityPos.y + entity.getHeight() / 2, entityPos.z,
                    tracerColorObj
                );
            }
        }
    }

    @Override
    public String getInfoString() {
        return String.valueOf(detectedMobs.size());
    }
}
