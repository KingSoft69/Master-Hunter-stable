package dev.masterhunter.addon.modules.Movement.searcharea.modes;

import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.util.math.BlockPos;

import java.io.*;

import dev.masterhunter.addon.modules.Movement.searcharea.SearchAreaMode;
import dev.masterhunter.addon.modules.Movement.searcharea.SearchAreaModes;

import static dev.masterhunter.addon.util.Utils.*;
import static meteordevelopment.meteorclient.utils.player.ChatUtils.info;

public class DynamicSpiral extends SearchAreaMode
{
    private PathingDataDynamicSpiral pd;
    private boolean goingToStart = true;
    private long startTime;

    public DynamicSpiral()
    {
        super(SearchAreaModes.DynamicSpiral);
    }

    @Override
    public void onActivate()
    {
            startTime = System.nanoTime();
            goingToStart = true;
            File file = getJsonFile(super.toString());
            if (file == null || !file.exists())
            {
                int initialBlockGap = 16 * searchArea.rowGap.get();
                pd = new PathingDataDynamicSpiral(mc.player.getBlockPos(), mc.player.getBlockPos(), -90.0f, true, initialBlockGap, initialBlockGap);
            }
            else
            {
                try {
                    FileReader reader = new FileReader(file);
                    pd = GSON.fromJson(reader, PathingDataDynamicSpiral.class);
                    reader.close();
                    info("Loaded previously saved path, heading to where you left off.");
                } catch (IOException e) {
                    info("Failed to load saved path, check logs for more details. Disabling module.");
                    e.printStackTrace();
                    this.disable();
                }
            }

    }

    @Override
    public void onDeactivate()
    {
        super.onDeactivate();
        super.saveToJson(goingToStart, pd);

    }

    @Override
    public void onTick()
    {
        // autosave every 10 minutes in case of crashes
        if (System.nanoTime() - startTime > 6e11)
        {
            startTime = System.nanoTime();
            super.saveToJson(goingToStart, pd);
        }

        if (System.nanoTime() < paused)
        {
            setPressed(mc.options.forwardKey, false);
            return;
        }


        if (goingToStart)
        {

            if (Math.sqrt(mc.player.getBlockPos().getSquaredDistance(pd.currPos.getX(), mc.player.getY(), pd.currPos.getZ())) < 5)
            {
                goingToStart = false;
                mc.player.setVelocity(0, 0, 0);
            }
            else
            {
                mc.player.setYaw((float) Rotations.getYaw(pd.currPos.toCenterPos()));
                setPressed(mc.options.forwardKey, true);
            }
            return;
        }

        setPressed(mc.options.forwardKey, true);
        mc.player.setYaw(pd.yawDirection);
        
        if (pd.mainPath && Math.abs(mc.player.getX() - pd.initialPos.getX()) >= pd.currentWidth)
        {
            pd.yawDirection += 90.0f;
            pd.initialPos = new BlockPos((int)mc.player.getX(), pd.initialPos.getY(), pd.initialPos.getZ());
            // Increase width by 1/3 (multiply by 4/3)
            pd.currentWidth = (int)(pd.currentWidth * 4.0 / 3.0);
            pd.mainPath = false;
            mc.player.setVelocity(0, 0, 0);
        }
        else if (!pd.mainPath && Math.abs(mc.player.getZ() - pd.initialPos.getZ()) >= pd.currentHeight)
        {
            pd.yawDirection += 90.0f;
            pd.initialPos = new BlockPos(pd.initialPos.getX(), pd.initialPos.getY(), (int)mc.player.getZ());
            // Increase height by 1/3 (multiply by 4/3)
            pd.currentHeight = (int)(pd.currentHeight * 4.0 / 3.0);
            pd.mainPath = true;
            mc.player.setVelocity(0, 0, 0);
        }
    }

    public static class PathingDataDynamicSpiral extends PathingData
    {
        public int currentWidth;
        public int currentHeight;

        public PathingDataDynamicSpiral(BlockPos initialPos, BlockPos currPos, float yawDirection, boolean mainPath, int currentWidth, int currentHeight)
        {
            this.initialPos = initialPos;
            this.currPos = currPos;
            this.yawDirection = yawDirection;
            this.mainPath = mainPath;
            this.currentWidth = currentWidth;
            this.currentHeight = currentHeight;
        }
    }
}
