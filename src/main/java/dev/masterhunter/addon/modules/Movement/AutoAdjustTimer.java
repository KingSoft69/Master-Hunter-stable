package dev.masterhunter.addon.modules.Movement;

import dev.masterhunter.addon.Main;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

public class AutoAdjustTimer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> onlyWhenTraveling = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-traveling")
        .description("Only adjust timer when traveling at high speed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> travelSpeedThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("travel-speed-threshold")
        .description("Minimum speed (km/h) to consider as traveling.")
        .defaultValue(10.0)
        .min(1.0)
        .sliderRange(1.0, 100.0)
        .visible(onlyWhenTraveling::get)
        .build()
    );

    private final Setting<Double> minSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-speed")
        .description("Minimum timer multiplier when many chunks are unloaded.")
        .defaultValue(0.4)
        .min(0.1)
        .sliderRange(0.1, 1.0)
        .build()
    );

    private final Setting<Double> maxSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-speed")
        .description("Maximum timer multiplier when all chunks are loaded.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderRange(0.1, 2.0)
        .build()
    );

    private final Setting<Integer> checkRadius = sgGeneral.add(new IntSetting.Builder()
        .name("check-radius")
        .description("Radius of chunks to check around the player.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<Integer> unloadedThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("unloaded-threshold")
        .description("Number of unloaded chunks before starting to slow down.")
        .defaultValue(6)
        .min(1)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Double> adjustSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("adjust-speed")
        .description("How fast the timer adjusts to target speed.")
        .defaultValue(0.15)
        .min(0.01)
        .sliderRange(0.01, 1.0)
        .build()
    );

    private final Setting<Integer> checkInterval = sgGeneral.add(new IntSetting.Builder()
        .name("check-interval")
        .description("How often to check chunk loading status (in ticks).")
        .defaultValue(5)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private double targetSpeed = 1.0;
    private double currentAutoSpeed = 1.0;
    private int tickCounter = 0;
    private int lastUnloadedCount = 0;
    private Vec3d lastPlayerPos = null;
    private double currentSpeed = 0;

    public AutoAdjustTimer() {
        super(Main.CATEGORY, "auto-adjust-timer", "Automatically adjusts Timer module speed based on chunk loading status.");
    }

    @Override
    public void onActivate() {
        targetSpeed = 1.0;
        currentAutoSpeed = 1.0;
        tickCounter = 0;
        lastUnloadedCount = 0;
        lastPlayerPos = null;
        currentSpeed = 0;
    }

    @Override
    public void onDeactivate() {
        // Reset timer override to OFF (1.0) when module is disabled
        Timer timer = Modules.get().get(Timer.class);
        if (timer != null) {
            timer.setOverride(Timer.OFF);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate() || mc.player == null || mc.world == null) return;

        // Calculate current travel speed
        if (lastPlayerPos != null) {
            Vec3d currentPos = mc.player.getPos();
            double distanceTraveled = currentPos.subtract(lastPlayerPos).multiply(1, 0, 1).length();
            double speedBPS = distanceTraveled * 20.0; // Blocks per second
            currentSpeed = speedBPS * 3.6; // Convert to km/h
        }
        lastPlayerPos = mc.player.getPos();

        tickCounter++;
        if (tickCounter < checkInterval.get()) return;
        tickCounter = 0;

        Timer timer = Modules.get().get(Timer.class);
        if (timer == null || !timer.isActive()) return;

        // If only adjusting while traveling and we're not traveling fast enough
        if (onlyWhenTraveling.get() && currentSpeed < travelSpeedThreshold.get()) {
            targetSpeed = 1.0;
            currentAutoSpeed = 1.0;
            setTimerMultiplier(timer, 1.0);
            lastUnloadedCount = 0;
            return;
        }

        // Count unloaded chunks around the player
        int unloadedChunks = countUnloadedChunks();

        // Calculate target speed based on unloaded chunks
        if (unloadedChunks > unloadedThreshold.get()) {
            double severity = Math.min(1.0, (double) unloadedChunks / (unloadedThreshold.get() * 2.0));
            targetSpeed = minSpeed.get() + (maxSpeed.get() - minSpeed.get()) * (1.0 - severity);
        } else {
            targetSpeed = maxSpeed.get();
        }

        // Smoothly adjust current speed towards target
        double diff = targetSpeed - currentAutoSpeed;
        if (Math.abs(diff) > 0.01) {
            currentAutoSpeed += diff * adjustSpeed.get();
            currentAutoSpeed = Math.max(minSpeed.get(), Math.min(maxSpeed.get(), currentAutoSpeed));
            setTimerMultiplier(timer, currentAutoSpeed);
        }

        lastUnloadedCount = unloadedChunks;
    }

    private int countUnloadedChunks() {
        if (mc.player == null || mc.world == null) return 0;
        
        ClientChunkManager chunkManager = mc.world.getChunkManager();
        ChunkPos playerChunk = mc.player.getChunkPos();
        int radius = checkRadius.get();
        int count = 0;
        
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Chunk chunk = chunkManager.getChunk(playerChunk.x + x, playerChunk.z + z, ChunkStatus.FULL, false);
                if (chunk == null) count++;
            }
        }
        
        return count;
    }

    private void setTimerMultiplier(Timer timer, double value) {
        // Use the Timer's setOverride method to control the timer speed
        timer.setOverride(value);
    }

    @Override
    public String getInfoString() {
        return String.format("%.2fx | %d unloaded", currentAutoSpeed, lastUnloadedCount);
    }

    // Getters for potential HUD integration
    public boolean isAutoAdjustEnabled() {
        return isActive();
    }

    public double getCurrentAutoSpeed() {
        return currentAutoSpeed;
    }

    public int getLastUnloadedCount() {
        return lastUnloadedCount;
    }

    public double getCurrentTravelSpeed() {
        return currentSpeed;
    }
}
