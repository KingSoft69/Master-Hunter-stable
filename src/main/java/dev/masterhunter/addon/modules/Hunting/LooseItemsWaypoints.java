package dev.masterhunter.addon.modules.Hunting;

import dev.masterhunter.addon.Main;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.map.mods.SupportMods;
import xaero.common.minimap.waypoints.Waypoint;

import java.util.*;

public class LooseItemsWaypoints extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgItems = settings.createGroup("Items");

    private final Setting<Boolean> trackShulkerBoxes = sgItems.add(new BoolSetting.Builder()
        .name("track-shulker-boxes")
        .description("Track all shulker box variants.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> trackNetheriteArmor = sgItems.add(new BoolSetting.Builder()
        .name("track-netherite-armor")
        .description("Track netherite armor pieces.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> trackEnchantedGoldenApples = sgItems.add(new BoolSetting.Builder()
        .name("track-enchanted-golden-apples")
        .description("Track enchanted golden apples.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> trackFireworkRockets = sgItems.add(new BoolSetting.Builder()
        .name("track-firework-rockets")
        .description("Track firework rockets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> trackTotems = sgItems.add(new BoolSetting.Builder()
        .name("track-totems")
        .description("Track totems of undying.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> trackEnderChests = sgItems.add(new BoolSetting.Builder()
        .name("track-ender-chests")
        .description("Track ender chests.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> trackObsidian = sgItems.add(new BoolSetting.Builder()
        .name("track-obsidian")
        .description("Track obsidian blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder()
        .name("scan-radius")
        .description("The radius in blocks to scan for items.")
        .defaultValue(64)
        .min(8)
        .sliderMin(8)
        .sliderMax(256)
        .build()
    );

    private final Setting<Integer> minimumItemCount = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-item-count")
        .description("Minimum number of items to create a waypoint (for stackable items).")
        .defaultValue(1)
        .min(1)
        .sliderMin(1)
        .sliderMax(64)
        .build()
    );

    private final Setting<Integer> duplicateRadius = sgGeneral.add(new IntSetting.Builder()
        .name("duplicate-radius")
        .description("Prevent duplicate waypoints within this radius.")
        .defaultValue(16)
        .min(1)
        .sliderMin(1)
        .sliderMax(128)
        .build()
    );

    private final Setting<Boolean> sendNotifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Send notifications when items are found.")
        .defaultValue(true)
        .build()
    );

    private final Set<String> waypointLocations = new HashSet<>();
    private int tickCounter = 0;
    private static final int TICK_INTERVAL = 20; // Check every second (20 ticks)

    public LooseItemsWaypoints() {
        super(Main.CATEGORY, "loose-items-waypoints", "Creates waypoints for loose valuable items like shulker boxes, netherite armor, enchanted apples, rockets, totems, ender chests, and obsidian.");
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

        // Only check every TICK_INTERVAL ticks to reduce performance impact
        tickCounter++;
        if (tickCounter < TICK_INTERVAL) return;
        tickCounter = 0;

        // Get nearby entities
        Box searchBox = new Box(
            mc.player.getX() - scanRadius.get(),
            mc.player.getY() - scanRadius.get(),
            mc.player.getZ() - scanRadius.get(),
            mc.player.getX() + scanRadius.get(),
            mc.player.getY() + scanRadius.get(),
            mc.player.getZ() + scanRadius.get()
        );

        List<Entity> entities = mc.world.getOtherEntities(mc.player, searchBox);
        
        // Track items by type and location
        Map<String, ItemGroup> itemGroups = new HashMap<>();
        
        for (Entity entity : entities) {
            if (!(entity instanceof ItemEntity itemEntity)) continue;
            
            ItemStack stack = itemEntity.getStack();
            Item item = stack.getItem();
            
            // Check if this is a tracked item
            if (!shouldTrackItem(item)) continue;
            
            int x = (int) itemEntity.getX();
            int y = (int) itemEntity.getY();
            int z = (int) itemEntity.getZ();
            
            // Group items at similar locations
            String groupKey = (x / 4) + "," + (y / 4) + "," + (z / 4);
            
            itemGroups.computeIfAbsent(groupKey, k -> new ItemGroup(x, y, z))
                .addItem(item, stack.getCount());
        }

        // Create waypoints for item groups
        for (ItemGroup group : itemGroups.values()) {
            if (shouldCreateWaypoint(group)) {
                createWaypointForItems(group);
            }
        }
    }

    private boolean shouldCreateWaypoint(ItemGroup group) {
        // Check minimum item count
        int totalCount = group.getTotalCount();
        if (totalCount < minimumItemCount.get()) return false;

        // Check if we already have a waypoint nearby
        return !hasNearbyWaypoint(group.x, group.y, group.z, duplicateRadius.get());
    }

    private void createWaypointForItems(ItemGroup group) {
        WaypointSet waypointSet = getWaypointSet();
        if (waypointSet == null) return;

        String itemName = getItemGroupName(group);
        String locationKey = group.x + "," + group.y + "," + group.z;
        
        if (waypointLocations.contains(locationKey)) return;

        // Set color based on item rarity
        int color = getColorForItems(group);

        Waypoint waypoint = new Waypoint(
            group.x,
            group.y,
            group.z,
            itemName,
            "L",
            color,
            0,
            false
        );

        waypointSet.add(waypoint);
        SupportMods.xaeroMinimap.requestWaypointsRefresh();
        waypointLocations.add(locationKey);

        if (sendNotifications.get()) {
            info("Found loose items at (highlight)%s(default), (highlight)%s(default), (highlight)%s(default): %s", group.x, group.y, group.z, itemName);
        }
    }

    private String getItemGroupName(ItemGroup group) {
        StringBuilder name = new StringBuilder();
        List<Map.Entry<Item, Integer>> entries = new ArrayList<>(group.items.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        
        int count = 0;
        for (Map.Entry<Item, Integer> entry : entries) {
            if (count > 0) name.append(",");
            name.append(getShortItemName(entry.getKey()));
            if (entry.getValue() > 1) {
                name.append("x").append(entry.getValue());
            }
            count++;
            if (count >= 3) break; // Limit to 3 items in name
        }
        
        return name.toString();
    }

    private String getShortItemName(Item item) {
        if (item == Items.SHULKER_BOX || isShulkerBox(item)) return "Shulker";
        if (isNetheriteArmor(item)) return "Netherite";
        if (item == Items.ENCHANTED_GOLDEN_APPLE) return "GApple";
        if (item == Items.FIREWORK_ROCKET) return "Rocket";
        if (item == Items.TOTEM_OF_UNDYING) return "Totem";
        if (item == Items.ENDER_CHEST) return "EnderChest";
        if (item == Items.OBSIDIAN) return "Obsidian";
        return item.getName().getString();
    }

    private int getColorForItems(ItemGroup group) {
        // Priority colors based on most valuable items
        if (group.items.containsKey(Items.ENCHANTED_GOLDEN_APPLE)) return 14; // Gold/Yellow
        if (hasNetheriteArmor(group)) return 5; // Purple
        if (group.items.containsKey(Items.TOTEM_OF_UNDYING)) return 10; // Green
        if (group.items.containsKey(Items.ENDER_CHEST)) return 13; // Magenta
        if (group.items.containsKey(Items.OBSIDIAN)) return 8; // Dark Gray (like obsidian color)
        if (hasShulkerBox(group)) return 12; // Light Blue
        if (group.items.containsKey(Items.FIREWORK_ROCKET)) return 15; // White
        return 7; // Default gray
    }

    private boolean hasShulkerBox(ItemGroup group) {
        for (Item item : group.items.keySet()) {
            if (isShulkerBox(item)) return true;
        }
        return false;
    }

    private boolean hasNetheriteArmor(ItemGroup group) {
        for (Item item : group.items.keySet()) {
            if (isNetheriteArmor(item)) return true;
        }
        return false;
    }

    private boolean isShulkerBox(Item item) {
        return item == Items.SHULKER_BOX ||
               item == Items.WHITE_SHULKER_BOX ||
               item == Items.ORANGE_SHULKER_BOX ||
               item == Items.MAGENTA_SHULKER_BOX ||
               item == Items.LIGHT_BLUE_SHULKER_BOX ||
               item == Items.YELLOW_SHULKER_BOX ||
               item == Items.LIME_SHULKER_BOX ||
               item == Items.PINK_SHULKER_BOX ||
               item == Items.GRAY_SHULKER_BOX ||
               item == Items.LIGHT_GRAY_SHULKER_BOX ||
               item == Items.CYAN_SHULKER_BOX ||
               item == Items.PURPLE_SHULKER_BOX ||
               item == Items.BLUE_SHULKER_BOX ||
               item == Items.BROWN_SHULKER_BOX ||
               item == Items.GREEN_SHULKER_BOX ||
               item == Items.RED_SHULKER_BOX ||
               item == Items.BLACK_SHULKER_BOX;
    }

    private boolean isNetheriteArmor(Item item) {
        return item == Items.NETHERITE_HELMET ||
               item == Items.NETHERITE_CHESTPLATE ||
               item == Items.NETHERITE_LEGGINGS ||
               item == Items.NETHERITE_BOOTS;
    }

    private boolean shouldTrackItem(Item item) {
        if (trackShulkerBoxes.get() && isShulkerBox(item)) return true;
        if (trackNetheriteArmor.get() && isNetheriteArmor(item)) return true;
        if (trackEnchantedGoldenApples.get() && item == Items.ENCHANTED_GOLDEN_APPLE) return true;
        if (trackFireworkRockets.get() && item == Items.FIREWORK_ROCKET) return true;
        if (trackTotems.get() && item == Items.TOTEM_OF_UNDYING) return true;
        if (trackEnderChests.get() && item == Items.ENDER_CHEST) return true;
        if (trackObsidian.get() && item == Items.OBSIDIAN) return true;
        return false;
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

    private static class ItemGroup {
        int x, y, z;
        Map<Item, Integer> items = new HashMap<>();

        ItemGroup(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        void addItem(Item item, int count) {
            items.merge(item, count, Integer::sum);
        }

        int getTotalCount() {
            return items.values().stream().mapToInt(Integer::intValue).sum();
        }
    }
}
