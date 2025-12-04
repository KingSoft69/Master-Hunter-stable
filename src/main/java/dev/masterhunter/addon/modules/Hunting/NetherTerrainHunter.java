package dev.masterhunter.addon.modules.Hunting;

import dev.masterhunter.addon.Main;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.map.mods.SupportMods;

import java.util.*;
import java.util.concurrent.*;

/**
 * NetherTerrainHunter - A module for detecting suspicious terrain anomalies in the Nether.
 * 
 * This module analyzes chunks as they load in the Nether dimension to automatically detect
 * and highlight (via Xaero Waypoints) any terrain anomalies that could indicate player activity
 * or unnatural terrain modification.
 * 
 * Detection Types:
 * - Large unnatural holes or caverns (air pockets larger than natural)
 * - Abrupt flat surfaces unnatural for Nether terrain
 * - Large vertical air gaps or suspicious flat patches
 * - Placed blocks not normally generated in Nether (wood, glass, wool, concrete, etc.)
 * 
 * Features:
 * - Only operates in the Nether dimension
 * - Session-only waypoints (cleared on module disable or world leave)
 * - Duplicate prevention (one waypoint per chunk per session)
 * - Focus on terrain only (ignores portals/entities/structures)
 */
public class NetherTerrainHunter extends Module {
    
    // ==================== SETTING GROUPS ====================
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDetection = settings.createGroup("Detection Thresholds");
    private final SettingGroup sgNotifications = settings.createGroup("Notifications");
    
    // ==================== GENERAL SETTINGS ====================
    
    /**
     * Enable/disable detection of placed blocks that don't naturally generate in the Nether
     */
    private final Setting<Boolean> detectPlacedBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-placed-blocks")
        .description("Detect blocks that don't naturally generate in the Nether (wood, glass, wool, etc.).")
        .defaultValue(true)
        .build()
    );
    
    /**
     * Enable/disable detection of large unnatural air pockets
     */
    private final Setting<Boolean> detectAirAnomalies = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-air-anomalies")
        .description("Detect large unnatural air pockets, holes, or caverns.")
        .defaultValue(true)
        .build()
    );
    
    /**
     * Enable/disable detection of flat surfaces unnatural for Nether
     */
    private final Setting<Boolean> detectFlatSurfaces = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-flat-surfaces")
        .description("Detect abrupt flat surfaces that are unnatural for Nether terrain.")
        .defaultValue(true)
        .build()
    );
    
    // ==================== DETECTION THRESHOLD SETTINGS ====================
    
    /**
     * Minimum number of placed blocks to trigger detection
     */
    private final Setting<Integer> placedBlockThreshold = sgDetection.add(new IntSetting.Builder()
        .name("placed-block-threshold")
        .description("Minimum number of unnatural blocks in a chunk to trigger detection.")
        .defaultValue(3)
        .min(1)
        .sliderMin(1)
        .sliderMax(20)
        .visible(detectPlacedBlocks::get)
        .build()
    );
    
    /**
     * Minimum size of air pocket to trigger detection (blocks)
     */
    private final Setting<Integer> airPocketThreshold = sgDetection.add(new IntSetting.Builder()
        .name("air-pocket-threshold")
        .description("Minimum size of unnatural air pocket to trigger detection (number of air blocks in column).")
        .defaultValue(20)
        .min(5)
        .sliderMin(5)
        .sliderMax(50)
        .visible(detectAirAnomalies::get)
        .build()
    );
    
    /**
     * Minimum flat area size to trigger detection
     */
    private final Setting<Integer> flatSurfaceThreshold = sgDetection.add(new IntSetting.Builder()
        .name("flat-surface-threshold")
        .description("Minimum number of consecutive flat blocks to trigger detection.")
        .defaultValue(16)
        .min(8)
        .sliderMin(8)
        .sliderMax(64)
        .visible(detectFlatSurfaces::get)
        .build()
    );
    
    // ==================== NOTIFICATION SETTINGS ====================
    
    /**
     * Send chat notifications when anomalies are detected
     */
    private final Setting<Boolean> sendChatNotifications = sgNotifications.add(new BoolSetting.Builder()
        .name("chat-notifications")
        .description("Send chat messages when suspicious terrain is detected.")
        .defaultValue(true)
        .build()
    );
    
    /**
     * Send toast notifications when anomalies are detected
     */
    private final Setting<Boolean> sendToastNotifications = sgNotifications.add(new BoolSetting.Builder()
        .name("toast-notifications")
        .description("Show toast popups when suspicious terrain is detected.")
        .defaultValue(false)
        .build()
    );
    
    // ==================== INTERNAL STATE ====================
    
    /**
     * Set of chunk positions that have already been analyzed this session.
     * Used to prevent duplicate waypoints per chunk.
     */
    private final Set<ChunkPos> analyzedChunks = Collections.synchronizedSet(new HashSet<>());
    
    /**
     * Set of waypoint location keys for this session.
     * Used for cleanup when module is deactivated.
     */
    private final Set<String> sessionWaypoints = Collections.synchronizedSet(new HashSet<>());
    
    /**
     * Executor for async chunk analysis to avoid blocking the main thread.
     * Uses daemon threads so it doesn't prevent JVM shutdown.
     * Note: Following existing pattern in NewChunksPlus - the executor is static
     * and shared across module activations to avoid thread pool recreation overhead.
     */
    private static final ExecutorService analysisExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true); // Daemon threads won't prevent JVM shutdown
        t.setName("NetherTerrainHunter-Worker");
        return t;
    });
    
    /**
     * Waypoint color for suspicious terrain (Magenta/Pink - stands out in Nether)
     */
    private static final int WAYPOINT_COLOR = 13; // Magenta color in Xaero's palette
    
    /**
     * Y level of lava sea in the Nether (natural lava lakes below this level)
     */
    private static final int NETHER_LAVA_SEA_LEVEL = 35;
    
    /**
     * Y level of natural Nether ceiling (netherrack ceiling typically starts here)
     */
    private static final int NETHER_CEILING_LEVEL = 115;
    
    // ==================== BLOCK SETS FOR DETECTION ====================
    
    /**
     * Set of blocks that do NOT naturally generate in the Nether.
     * Finding these blocks in significant quantities indicates player activity.
     */
    private static final Set<Block> UNNATURAL_NETHER_BLOCKS = new HashSet<>();
    static {
        // Wood blocks (all variants)
        UNNATURAL_NETHER_BLOCKS.add(Blocks.OAK_PLANKS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.SPRUCE_PLANKS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BIRCH_PLANKS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.JUNGLE_PLANKS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.ACACIA_PLANKS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.DARK_OAK_PLANKS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.MANGROVE_PLANKS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.CHERRY_PLANKS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BAMBOO_PLANKS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.OAK_LOG);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.SPRUCE_LOG);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BIRCH_LOG);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.JUNGLE_LOG);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.ACACIA_LOG);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.DARK_OAK_LOG);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.MANGROVE_LOG);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.CHERRY_LOG);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BAMBOO_BLOCK);
        
        // Glass blocks (all variants)
        UNNATURAL_NETHER_BLOCKS.add(Blocks.GLASS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.WHITE_STAINED_GLASS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.ORANGE_STAINED_GLASS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.MAGENTA_STAINED_GLASS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.LIGHT_BLUE_STAINED_GLASS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.YELLOW_STAINED_GLASS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.LIME_STAINED_GLASS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.PINK_STAINED_GLASS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.GRAY_STAINED_GLASS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.LIGHT_GRAY_STAINED_GLASS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.CYAN_STAINED_GLASS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.PURPLE_STAINED_GLASS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BLUE_STAINED_GLASS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BROWN_STAINED_GLASS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.GREEN_STAINED_GLASS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.RED_STAINED_GLASS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BLACK_STAINED_GLASS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.TINTED_GLASS);
        
        // Wool blocks (all colors)
        UNNATURAL_NETHER_BLOCKS.add(Blocks.WHITE_WOOL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.ORANGE_WOOL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.MAGENTA_WOOL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.LIGHT_BLUE_WOOL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.YELLOW_WOOL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.LIME_WOOL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.PINK_WOOL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.GRAY_WOOL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.LIGHT_GRAY_WOOL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.CYAN_WOOL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.PURPLE_WOOL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BLUE_WOOL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BROWN_WOOL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.GREEN_WOOL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.RED_WOOL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BLACK_WOOL);
        
        // Concrete blocks (all colors)
        UNNATURAL_NETHER_BLOCKS.add(Blocks.WHITE_CONCRETE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.ORANGE_CONCRETE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.MAGENTA_CONCRETE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.LIGHT_BLUE_CONCRETE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.YELLOW_CONCRETE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.LIME_CONCRETE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.PINK_CONCRETE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.GRAY_CONCRETE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.LIGHT_GRAY_CONCRETE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.CYAN_CONCRETE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.PURPLE_CONCRETE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BLUE_CONCRETE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BROWN_CONCRETE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.GREEN_CONCRETE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.RED_CONCRETE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BLACK_CONCRETE);
        
        // Functional/Utility blocks
        UNNATURAL_NETHER_BLOCKS.add(Blocks.CRAFTING_TABLE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.FURNACE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BLAST_FURNACE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.SMOKER);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.ANVIL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.CHIPPED_ANVIL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.DAMAGED_ANVIL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.ENCHANTING_TABLE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BREWING_STAND);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BEACON);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.CONDUIT);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.LECTERN);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.CARTOGRAPHY_TABLE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.FLETCHING_TABLE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.SMITHING_TABLE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.GRINDSTONE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.STONECUTTER);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.LOOM);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.COMPOSTER);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BEE_NEST);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BEEHIVE);
        
        // Storage blocks
        UNNATURAL_NETHER_BLOCKS.add(Blocks.CHEST);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.TRAPPED_CHEST);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.ENDER_CHEST);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BARREL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.SHULKER_BOX);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.WHITE_SHULKER_BOX);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.ORANGE_SHULKER_BOX);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.MAGENTA_SHULKER_BOX);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.LIGHT_BLUE_SHULKER_BOX);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.YELLOW_SHULKER_BOX);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.LIME_SHULKER_BOX);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.PINK_SHULKER_BOX);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.GRAY_SHULKER_BOX);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.LIGHT_GRAY_SHULKER_BOX);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.CYAN_SHULKER_BOX);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.PURPLE_SHULKER_BOX);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BLUE_SHULKER_BOX);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BROWN_SHULKER_BOX);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.GREEN_SHULKER_BOX);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.RED_SHULKER_BOX);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BLACK_SHULKER_BOX);
        
        // Bed blocks (all colors) - very suspicious in Nether!
        UNNATURAL_NETHER_BLOCKS.add(Blocks.WHITE_BED);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.ORANGE_BED);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.MAGENTA_BED);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.LIGHT_BLUE_BED);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.YELLOW_BED);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.LIME_BED);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.PINK_BED);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.GRAY_BED);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.LIGHT_GRAY_BED);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.CYAN_BED);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.PURPLE_BED);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BLUE_BED);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BROWN_BED);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.GREEN_BED);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.RED_BED);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BLACK_BED);
        
        // Other suspicious blocks
        UNNATURAL_NETHER_BLOCKS.add(Blocks.DIAMOND_BLOCK);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.EMERALD_BLOCK);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.IRON_BLOCK);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.GOLD_BLOCK);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.NETHERITE_BLOCK);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.LAPIS_BLOCK);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.REDSTONE_BLOCK);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.COPPER_BLOCK);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.RAW_IRON_BLOCK);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.RAW_COPPER_BLOCK);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.RAW_GOLD_BLOCK);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BOOKSHELF);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.CHISELED_BOOKSHELF);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.SPONGE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.WET_SPONGE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.SLIME_BLOCK);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.HONEY_BLOCK);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.DRIED_KELP_BLOCK);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.MOSS_BLOCK);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.HAY_BLOCK);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.PUMPKIN);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.CARVED_PUMPKIN);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.JACK_O_LANTERN);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.MELON);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.TORCH);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.WALL_TORCH);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.LANTERN);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.SOUL_LANTERN);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.END_ROD);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.SEA_LANTERN);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.REDSTONE_LAMP);
        
        // Rails (commonly used in Nether highways but indicates player paths)
        UNNATURAL_NETHER_BLOCKS.add(Blocks.RAIL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.POWERED_RAIL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.DETECTOR_RAIL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.ACTIVATOR_RAIL);
        
        // Redstone components
        UNNATURAL_NETHER_BLOCKS.add(Blocks.HOPPER);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.DROPPER);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.DISPENSER);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.OBSERVER);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.PISTON);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.STICKY_PISTON);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.REPEATER);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.COMPARATOR);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.DAYLIGHT_DETECTOR);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.TARGET);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.LEVER);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.TRIPWIRE_HOOK);
        
        // Signs and banners
        UNNATURAL_NETHER_BLOCKS.add(Blocks.OAK_SIGN);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.SPRUCE_SIGN);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BIRCH_SIGN);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.JUNGLE_SIGN);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.ACACIA_SIGN);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.DARK_OAK_SIGN);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.MANGROVE_SIGN);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.CHERRY_SIGN);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BAMBOO_SIGN);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.CRIMSON_SIGN);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.WARPED_SIGN);
        
        // Terracotta/Glazed Terracotta (all colors)
        UNNATURAL_NETHER_BLOCKS.add(Blocks.TERRACOTTA);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.WHITE_TERRACOTTA);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.ORANGE_TERRACOTTA);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.MAGENTA_TERRACOTTA);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.LIGHT_BLUE_TERRACOTTA);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.YELLOW_TERRACOTTA);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.LIME_TERRACOTTA);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.PINK_TERRACOTTA);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.GRAY_TERRACOTTA);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.LIGHT_GRAY_TERRACOTTA);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.CYAN_TERRACOTTA);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.PURPLE_TERRACOTTA);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BLUE_TERRACOTTA);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BROWN_TERRACOTTA);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.GREEN_TERRACOTTA);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.RED_TERRACOTTA);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BLACK_TERRACOTTA);
        
        // Stone variants not found in Nether
        UNNATURAL_NETHER_BLOCKS.add(Blocks.STONE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.COBBLESTONE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.STONE_BRICKS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.MOSSY_STONE_BRICKS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.CRACKED_STONE_BRICKS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.CHISELED_STONE_BRICKS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.MOSSY_COBBLESTONE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.SMOOTH_STONE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.GRANITE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.POLISHED_GRANITE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.DIORITE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.POLISHED_DIORITE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.ANDESITE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.POLISHED_ANDESITE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.DEEPSLATE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.COBBLED_DEEPSLATE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.DEEPSLATE_BRICKS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.DEEPSLATE_TILES);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.CALCITE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.TUFF);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.DRIPSTONE_BLOCK);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BRICKS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.PRISMARINE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.PRISMARINE_BRICKS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.DARK_PRISMARINE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.END_STONE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.END_STONE_BRICKS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.PURPUR_BLOCK);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.PURPUR_PILLAR);
        
        // Dirt/Grass variants
        UNNATURAL_NETHER_BLOCKS.add(Blocks.DIRT);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.GRASS_BLOCK);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.COARSE_DIRT);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.PODZOL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.MYCELIUM);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.ROOTED_DIRT);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.MUD);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.PACKED_MUD);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.MUD_BRICKS);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.CLAY);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.SAND);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.RED_SAND);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.GRAVEL);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.SANDSTONE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.RED_SANDSTONE);
        
        // Ice variants
        UNNATURAL_NETHER_BLOCKS.add(Blocks.ICE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.PACKED_ICE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.BLUE_ICE);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.SNOW_BLOCK);
        UNNATURAL_NETHER_BLOCKS.add(Blocks.POWDER_SNOW);
        
        // Water - very suspicious in Nether!
        UNNATURAL_NETHER_BLOCKS.add(Blocks.WATER);
    }
    
    // ==================== CONSTRUCTOR ====================
    
    public NetherTerrainHunter() {
        super(Main.CATEGORY, "nether-terrain-hunter", 
            "Detects suspicious terrain anomalies in the Nether that may indicate player activity or bases.");
    }
    
    // ==================== MODULE LIFECYCLE ====================
    
    /**
     * Called when the module is activated.
     * Clears any previous session data.
     */
    @Override
    public void onActivate() {
        analyzedChunks.clear();
        sessionWaypoints.clear();
    }
    
    /**
     * Called when the module is deactivated.
     * Clears all session waypoints to ensure clean state.
     */
    @Override
    public void onDeactivate() {
        clearSessionWaypoints();
        analyzedChunks.clear();
        sessionWaypoints.clear();
    }
    
    // ==================== GUI WIDGET ====================
    
    /**
     * Provides a GUI widget with a button to manually clear all session waypoints.
     */
    @Override
    public WWidget getWidget(GuiTheme theme) {
        WTable table = theme.table();
        
        WButton clearWaypoints = table.add(theme.button("Clear Session Waypoints")).expandX().minWidth(100).widget();
        clearWaypoints.action = () -> {
            clearSessionWaypoints();
            analyzedChunks.clear();
            sessionWaypoints.clear();
            info("Cleared all session waypoints and reset chunk tracking.");
        };
        
        table.row();
        return table;
    }
    
    // ==================== EVENT HANDLERS ====================
    
    /**
     * Handle game leave event to clear session data.
     */
    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        clearSessionWaypoints();
        analyzedChunks.clear();
        sessionWaypoints.clear();
    }
    
    /**
     * Main packet handler for detecting chunk loads.
     * Analyzes each newly loaded chunk for terrain anomalies.
     */
    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        // Only process chunk data packets
        if (!(event.packet instanceof ChunkDataS2CPacket packet)) return;
        
        // Ensure we have a valid world and player
        if (mc.world == null || mc.player == null) return;
        
        // REQUIREMENT 1: Only operate in the Nether dimension
        if (mc.world.getRegistryKey() != World.NETHER) return;
        
        ChunkPos chunkPos = new ChunkPos(packet.getChunkX(), packet.getChunkZ());
        
        // REQUIREMENT 5: Prevent duplicate waypoint spam - don't analyze same chunk twice
        if (analyzedChunks.contains(chunkPos)) return;
        
        // Mark chunk as analyzed immediately to prevent race conditions
        analyzedChunks.add(chunkPos);
        
        // Get the chunk from the world
        WorldChunk chunk = mc.world.getChunk(packet.getChunkX(), packet.getChunkZ());
        if (chunk == null || chunk.isEmpty()) return;
        
        // Analyze chunk asynchronously to avoid blocking the main thread
        CompletableFuture.runAsync(() -> analyzeChunk(chunk, chunkPos), analysisExecutor);
    }
    
    // ==================== CHUNK ANALYSIS ====================
    
    /**
     * Analyzes a chunk for terrain anomalies.
     * This method runs asynchronously.
     * 
     * @param chunk The chunk to analyze
     * @param chunkPos The position of the chunk
     */
    private void analyzeChunk(WorldChunk chunk, ChunkPos chunkPos) {
        try {
            // Track what anomalies we find
            List<String> anomaliesFound = new ArrayList<>();
            BlockPos anomalyPosition = null;
            
            // REQUIREMENT 6: Focus only on terrain - we analyze blocks, not entities/structures
            
            // Check for placed blocks (unnatural blocks in Nether)
            if (detectPlacedBlocks.get()) {
                BlockPos placedBlockPos = checkForPlacedBlocks(chunk);
                if (placedBlockPos != null) {
                    anomaliesFound.add("Placed Blocks");
                    anomalyPosition = placedBlockPos;
                }
            }
            
            // Check for unnatural air pockets/holes
            if (detectAirAnomalies.get()) {
                BlockPos airAnomalyPos = checkForAirAnomalies(chunk);
                if (airAnomalyPos != null) {
                    anomaliesFound.add("Air Anomaly");
                    if (anomalyPosition == null) anomalyPosition = airAnomalyPos;
                }
            }
            
            // Check for unnatural flat surfaces
            if (detectFlatSurfaces.get()) {
                BlockPos flatSurfacePos = checkForFlatSurfaces(chunk);
                if (flatSurfacePos != null) {
                    anomaliesFound.add("Flat Surface");
                    if (anomalyPosition == null) anomalyPosition = flatSurfacePos;
                }
            }
            
            // If any anomalies were found, create a waypoint
            if (!anomaliesFound.isEmpty() && anomalyPosition != null) {
                createAnomalyWaypoint(chunkPos, anomalyPosition, anomaliesFound);
            }
            
        } catch (ConcurrentModificationException | ArrayIndexOutOfBoundsException e) {
            // These can occur during chunk loading/unloading - safe to ignore
        } catch (Exception e) {
            // Log unexpected errors at debug level to aid troubleshooting
            Main.LOG.debug("NetherTerrainHunter: Error analyzing chunk {}: {}", chunkPos, e.getMessage());
        }
    }
    
    /**
     * Checks for blocks that don't naturally generate in the Nether.
     * 
     * @param chunk The chunk to check
     * @return Position of the first unnatural block found, or null if threshold not met
     */
    private BlockPos checkForPlacedBlocks(WorldChunk chunk) {
        int unnaturalBlockCount = 0;
        BlockPos firstUnnaturalBlock = null;
        
        ChunkSection[] sections = chunk.getSectionArray();
        int startY = chunk.getBottomY();
        
        // Iterate through all chunk sections
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            if (section == null || section.isEmpty()) continue;
            
            int sectionY = startY + (sectionIndex * 16);
            
            // Iterate through all blocks in the section
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        Block block = state.getBlock();
                        
                        // Check if this block is unnatural for Nether
                        if (UNNATURAL_NETHER_BLOCKS.contains(block)) {
                            unnaturalBlockCount++;
                            
                            // Store position of first unnatural block
                            if (firstUnnaturalBlock == null) {
                                firstUnnaturalBlock = new BlockPos(
                                    chunk.getPos().getStartX() + x,
                                    sectionY + y,
                                    chunk.getPos().getStartZ() + z
                                );
                            }
                            
                            // Early exit if threshold met
                            if (unnaturalBlockCount >= placedBlockThreshold.get()) {
                                return firstUnnaturalBlock;
                            }
                        }
                    }
                }
            }
        }
        
        return null; // Threshold not met
    }
    
    /**
     * Checks for large unnatural air pockets or holes in the Nether terrain.
     * Natural Nether caves tend to be irregular; large uniform air pockets are suspicious.
     * 
     * @param chunk The chunk to check
     * @return Position of suspicious air pocket, or null if none found
     */
    private BlockPos checkForAirAnomalies(WorldChunk chunk) {
        // Check for columns with unusually large air gaps
        // Natural Nether terrain has netherrack ceiling, so large air pockets in middle are suspicious
        
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int consecutiveAir = 0;
                int maxConsecutiveAir = 0;
                int airStartY = 0;
                
                // Scan from bottom to top of Nether (y=0 to y=127 typically)
                for (int y = 0; y < 128; y++) {
                    BlockPos pos = new BlockPos(
                        chunk.getPos().getStartX() + x,
                        y,
                        chunk.getPos().getStartZ() + z
                    );
                    
                    BlockState state = chunk.getBlockState(pos);
                    
                    if (state.isAir()) {
                        if (consecutiveAir == 0) {
                            airStartY = y;
                        }
                        consecutiveAir++;
                    } else {
                        // Check if we found a large air pocket that's NOT at the natural lava sea level
                        // and NOT at the ceiling
                        if (consecutiveAir > maxConsecutiveAir && 
                            airStartY > NETHER_LAVA_SEA_LEVEL && // Above lava sea level
                            airStartY < NETHER_CEILING_LEVEL) { // Below natural ceiling
                            maxConsecutiveAir = consecutiveAir;
                        }
                        consecutiveAir = 0;
                    }
                }
                
                // Check threshold
                if (maxConsecutiveAir >= airPocketThreshold.get()) {
                    return new BlockPos(
                        chunk.getPos().getStartX() + x,
                        airStartY + (maxConsecutiveAir / 2),
                        chunk.getPos().getStartZ() + z
                    );
                }
            }
        }
        
        return null;
    }
    
    /**
     * Checks for unnaturally flat surfaces in the Nether terrain.
     * Natural Nether terrain is rough; flat areas suggest player modification.
     * 
     * @param chunk The chunk to check
     * @return Position of flat surface, or null if none found
     */
    private BlockPos checkForFlatSurfaces(WorldChunk chunk) {
        // Check for horizontal flat surfaces at various Y levels
        // Count blocks at the same Y level that have air above them
        
        for (int y = 32; y < 120; y++) {
            int flatBlockCount = 0;
            BlockPos firstFlatBlock = null;
            
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockPos pos = new BlockPos(
                        chunk.getPos().getStartX() + x,
                        y,
                        chunk.getPos().getStartZ() + z
                    );
                    BlockPos abovePos = pos.up();
                    
                    BlockState state = chunk.getBlockState(pos);
                    BlockState aboveState = chunk.getBlockState(abovePos);
                    
                    // Check if this is a solid block with air above (potential floor)
                    if (!state.isAir() && aboveState.isAir()) {
                        flatBlockCount++;
                        if (firstFlatBlock == null) {
                            firstFlatBlock = pos;
                        }
                    }
                }
            }
            
            // If a large portion of the slice is flat, it's suspicious
            if (flatBlockCount >= flatSurfaceThreshold.get()) {
                return firstFlatBlock;
            }
        }
        
        return null;
    }
    
    // ==================== WAYPOINT MANAGEMENT ====================
    
    /**
     * Creates a waypoint for a detected anomaly.
     * 
     * @param chunkPos The chunk position
     * @param anomalyPos The specific position of the anomaly
     * @param anomalyTypes List of anomaly types detected
     */
    private void createAnomalyWaypoint(ChunkPos chunkPos, BlockPos anomalyPos, List<String> anomalyTypes) {
        // Execute on main thread to ensure thread safety with waypoint system
        mc.execute(() -> {
            WaypointSet waypointSet = getWaypointSet();
            if (waypointSet == null) return;
            
            // Generate waypoint name with anomaly types
            String waypointName = "Suspicious: " + String.join(", ", anomalyTypes);
            
            // Use center of chunk for X/Z, use anomaly Y
            int x = chunkPos.getCenterX();
            int y = anomalyPos.getY();
            int z = chunkPos.getCenterZ();
            
            // Create the waypoint
            Waypoint waypoint = new Waypoint(
                x,
                y,
                z,
                waypointName,
                "T", // T for Terrain
                WAYPOINT_COLOR,
                0,
                false
            );
            
            waypointSet.add(waypoint);
            SupportMods.xaeroMinimap.requestWaypointsRefresh();
            
            // Track this waypoint for session cleanup
            String locationKey = x + "," + y + "," + z;
            sessionWaypoints.add(locationKey);
            
            // Send notifications if enabled
            if (sendChatNotifications.get()) {
                info("Suspicious terrain detected at (highlight)%d(default), (highlight)%d(default), (highlight)%d(default): %s", 
                    x, y, z, String.join(", ", anomalyTypes));
            }
            
            if (sendToastNotifications.get()) {
                mc.getToastManager().add(new meteordevelopment.meteorclient.utils.render.MeteorToast(
                    net.minecraft.item.Items.NETHERRACK, 
                    "Suspicious Terrain!", 
                    "Found at " + x + ", " + z
                ));
            }
        });
    }
    
    /**
     * Gets the current waypoint set from Xaero's minimap.
     * 
     * @return The current WaypointSet, or null if unavailable
     */
    private WaypointSet getWaypointSet() {
        MinimapSession minimapSession = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (minimapSession == null) return null;
        MinimapWorld currentWorld = minimapSession.getWorldManager().getCurrentWorld();
        if (currentWorld == null) return null;
        return currentWorld.getCurrentWaypointSet();
    }
    
    /**
     * Clears all waypoints created during this session.
     * Called when module is deactivated or player leaves the world.
     */
    private void clearSessionWaypoints() {
        WaypointSet waypointSet = getWaypointSet();
        if (waypointSet == null) return;
        
        // Create a copy to avoid concurrent modification
        Set<String> waypointsToRemove = new HashSet<>(sessionWaypoints);
        
        for (String locationKey : waypointsToRemove) {
            String[] coords = locationKey.split(",");
            if (coords.length != 3) continue;
            
            try {
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                int z = Integer.parseInt(coords[2]);
                
                // Find and remove the waypoint
                Waypoint toRemove = null;
                for (Waypoint wp : waypointSet.getWaypoints()) {
                    if (wp.getX() == x && wp.getY() == y && wp.getZ() == z) {
                        toRemove = wp;
                        break;
                    }
                }
                
                if (toRemove != null) {
                    waypointSet.remove(toRemove);
                }
            } catch (NumberFormatException ignored) {
                // Skip invalid entries
            }
        }
        
        SupportMods.xaeroMinimap.requestWaypointsRefresh();
    }
    
    /**
     * Returns info string showing number of waypoints created this session.
     */
    @Override
    public String getInfoString() {
        return String.valueOf(sessionWaypoints.size());
    }
}
