package dev.masterhunter.addon.modules.Utility;

import dev.masterhunter.addon.Main;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class PortalAutoLog extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> detectionRange = sgGeneral.add(new IntSetting.Builder()
            .name("detection-range")
            .description("Distance in blocks to detect portals.")
            .defaultValue(2)
            .min(1)
            .sliderMax(10)
            .build());

    private final Setting<Boolean> toggleAutoReconnect = sgGeneral.add(new BoolSetting.Builder()
            .name("toggle-auto-reconnect")
            .description("Turns off auto reconnect when disconnecting.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> toggleOff = sgGeneral.add(new BoolSetting.Builder()
            .name("toggle-off")
            .description("Disables Portal Auto Log after usage.")
            .defaultValue(true)
            .build());

    public PortalAutoLog() {
        super(Main.CATEGORY, "portal-auto-log", "Disconnects you when you are within range of a portal.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc == null || mc.world == null || mc.player == null)
            return;

        checkPortalProximity();
    }

    private void checkPortalProximity() {
        BlockPos playerPos = mc.player.getBlockPos();
        int range = detectionRange.get();

        // Check all blocks within the specified range
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos checkPos = playerPos.add(x, y, z);
                    
                    // Check if the block is a nether portal block
                    if (mc.world.getBlockState(checkPos).getBlock() == Blocks.NETHER_PORTAL) {
                        double distance = Math.sqrt(x * x + y * y + z * z);
                        disconnect(Text.of("[PortalAutoLog] Nether portal detected within " 
                                + String.format("%.1f", distance) + " blocks."));
                        return;
                    }
                }
            }
        }
    }

    private void disconnect(Text text) {
        if (mc.getNetworkHandler() == null)
            return;
        
        mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(text));

        if (toggleOff.get())
            toggle();

        if (toggleAutoReconnect.get() && Modules.get().isActive(AutoReconnect.class))
            Modules.get().get(AutoReconnect.class).toggle();
    }
}
