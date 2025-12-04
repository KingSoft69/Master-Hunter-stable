package dev.masterhunter.addon.util;

import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownServiceException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Utils
{

    // returns -1 if fails, 200 if successful, and slot of chestplate if it had to swap (needed for mio grimdura)
    public static int firework(MinecraftClient mc, boolean elytraRequired) {

        // cant use a rocket if not wearing an elytra
        int elytraSwapSlot = -1;
        if (elytraRequired && !mc.player.getInventory().getStack(SlotUtils.ARMOR_START + 2).isOf(Items.ELYTRA))
        {
            FindItemResult itemResult = InvUtils.findInHotbar(Items.ELYTRA);
            if (!itemResult.found()) {
                return -1;
            }
            else
            {
                elytraSwapSlot = itemResult.slot();
                InvUtils.swap(itemResult.slot(), true);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                InvUtils.swapBack();
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
        }

        FindItemResult itemResult = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (!itemResult.found()) return -1;

        if (itemResult.isOffhand()) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            mc.player.swingHand(Hand.OFF_HAND);
        } else {
            InvUtils.swap(itemResult.slot(), true);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            InvUtils.swapBack();
        }
        if (elytraSwapSlot != -1)
        {
            return elytraSwapSlot;
        }
        return 200;
    }

    public static void setPressed(KeyBinding key, boolean pressed)
    {
        key.setPressed(pressed);
        Input.setKeyState(key, pressed);
    }

    public static int emptyInvSlots(MinecraftClient mc) {
        int airCount = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.AIR) {
                airCount++;
            }
        }
        return airCount;
    }

    /**
     * Returns the position in the direction of the yaw.
     * @param pos The starting position.
     * @param yaw The yaw in degrees.
     * @param distance The distance to move in the direction of the yaw.
     * @return The new position.
     */
    public static Vec3d positionInDirection(Vec3d pos, double yaw, double distance)
    {
        Vec3d offset = yawToDirection(yaw).multiply(distance);
        return pos.add(offset);
    }

    /**
     * Converts a yaw in degrees to a direction vector.
     * @param yaw The yaw in degrees.
     * @return The direction vector.
     */
    public static Vec3d yawToDirection(double yaw)
    {
        yaw = yaw * Math.PI / 180;
        double x = -Math.sin(yaw);
        double z = Math.cos(yaw);
        return new Vec3d(x, 0, z);
    }

    /**
     * Returns the distance from a point to a direction vector, not including the Y axis.
     * @param point The point to measure from.
     * @param direction The direction vector.
     * @param start The starting point of the direction vector, or null if the direction vector starts at (0, 0).
     * @return The distance from the point to the direction vector.
     */
    public static double distancePointToDirection(Vec3d point, Vec3d direction, @Nullable Vec3d start) {
        if (start == null) start = Vec3d.ZERO;

        point = point.multiply(new Vec3d(1, 0, 1));
        start = start.multiply(new Vec3d(1, 0, 1));
        direction = direction.multiply(new Vec3d(1, 0, 1));

        Vec3d directionVec = point.subtract(start);

        double projectionLength = directionVec.dotProduct(direction) / direction.lengthSquared();
        Vec3d projection = direction.multiply(projectionLength);
        Vec3d perp = directionVec.subtract(projection);
        return perp.length();
    }

    /**
     * Returns the angle rounded to the closest main 8 axis'.
     * @param yaw The yaw in degrees.
     * @return The angle on the axis.
     */
    public static double angleOnAxis(double yaw)
    {
        if (yaw < 0) yaw += 360;
        return Math.round(yaw / 45.0f) * 45;
    }

    public static Vec3d normalizedPositionOnAxis(Vec3d pos) {
        double angle = -Math.atan2(pos.x, pos.z);
        double angleDeg = Math.toDegrees(angle);

        return positionInDirection(new Vec3d(0,0,0), angleOnAxis(angleDeg), 1);
    }

    public static int totalInvCount(MinecraftClient mc, Item item) {
        if (mc.player == null) return 0;
        int itemCount = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == item) {
                itemCount += stack.getCount();
            }
        }
        return itemCount;
    }

    public static float smoothRotation(double current, double target, double rotationScaling)
    {
        double difference = angleDifference(target, current);
        return (float) (current + difference * rotationScaling);
    }

    public static double angleDifference(double target, double current)
    {
        double diff = (target - current + 180) % 360 - 180;
        return diff < -180 ? diff + 360 : diff;
    }

    public static void sendWebhook(String webhookURL, String title, String message, String pingID, String playerName)
    {
        String json = "";
        if (playerName != null && !playerName.isEmpty()) {
            json += "{\"embeds\": [{"
                + "\"title\": \""+ title +"\","
                + "\"description\": \""+ message +"\","
                + "\"color\": 15258703,"
                + "\"footer\": {"
                + "\"text\": \"From: " + playerName + "\"}"
                + "}]}";
        } else {
            json += "{\"embeds\": [{"
                + "\"title\": \""+ title +"\","
                + "\"description\": \""+ message +"\","
                + "\"color\": 15258703"
                + "}]}";
        }
        sendRequest(webhookURL, json);

        if (pingID != null)
        {
            json = "{\"content\": \"<@" + pingID + ">\"}";
            sendRequest(webhookURL, json);
        }
    }

    public static void sendWebhook(String webhookURL, String jsonObject, String pingID)
    {
        sendRequest(webhookURL, jsonObject);

        if (pingID != null)
        {
            jsonObject = "{\"content\": \"<@" + pingID + ">\"}";
            sendRequest(webhookURL, jsonObject);
        }
    }

    private static void sendRequest(String webhookURL, String json) {
        try {
            URL url = new URL(webhookURL);
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.addRequestProperty("Content-Type", "application/json");
            con.addRequestProperty("User-Agent", "Mozilla");
            con.setDoOutput(true);
            con.setRequestMethod("POST");
            OutputStream stream = con.getOutputStream();
            stream.write(json.getBytes());
            stream.flush();
            stream.close();
            con.getInputStream().close();
            con.disconnect();
        }
        catch (MalformedURLException | UnknownServiceException e)
        {
//            searchArea.logToWebhook.set(false);
//            searchArea.webhookLink.set("");
//            info("Issue with webhook link. It has been cleared, try again.");
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Upload file content to GitHub repository using GitHub REST API
     * @param owner Repository owner (username or organization)
     * @param repo Repository name
     * @param path File path in repository (e.g., "data/signs_database.json")
     * @param content File content to upload
     * @param token GitHub Personal Access Token
     * @param branch Branch name (e.g., "main")
     * @param commitMessage Commit message
     * @return true if successful, false otherwise
     */
    public static boolean uploadToGitHub(String owner, String repo, String path, String content, 
                                        String token, String branch, String commitMessage) {
        try {
            // First, try to get the current file to retrieve its SHA (needed for updates)
            String sha = getGitHubFileSHA(owner, repo, path, token, branch);
            
            // Prepare the API URL
            String apiUrl = String.format("https://api.github.com/repos/%s/%s/contents/%s", owner, repo, path);
            URL url = new URL(apiUrl);
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            
            // Set headers
            con.addRequestProperty("Authorization", "Bearer " + token);
            con.addRequestProperty("Content-Type", "application/json");
            con.addRequestProperty("Accept", "application/vnd.github+json");
            con.addRequestProperty("User-Agent", "MasterHunter-Addon");
            con.setDoOutput(true);
            con.setRequestMethod("PUT");
            
            // Encode content to base64
            String encodedContent = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
            
            // Build JSON payload
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"message\":\"").append(escapeJson(commitMessage)).append("\",");
            json.append("\"content\":\"").append(encodedContent).append("\",");
            json.append("\"branch\":\"").append(escapeJson(branch)).append("\"");
            if (sha != null) {
                json.append(",\"sha\":\"").append(sha).append("\"");
            }
            json.append("}");
            
            // Send request
            OutputStream stream = con.getOutputStream();
            stream.write(json.toString().getBytes(StandardCharsets.UTF_8));
            stream.flush();
            stream.close();
            
            // Check response
            int responseCode = con.getResponseCode();
            if (responseCode == 200 || responseCode == 201) {
                con.getInputStream().close();
                con.disconnect();
                return true;
            } else {
                // Read error response
                BufferedReader reader = new BufferedReader(new InputStreamReader(con.getErrorStream()));
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    errorResponse.append(line);
                }
                reader.close();
                con.disconnect();
                System.err.println("GitHub upload failed with code " + responseCode + ": " + errorResponse);
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Get the SHA of an existing file in GitHub repository
     * @return SHA string if file exists, null otherwise
     */
    private static String getGitHubFileSHA(String owner, String repo, String path, String token, String branch) {
        try {
            String apiUrl = String.format("https://api.github.com/repos/%s/%s/contents/%s?ref=%s", 
                                         owner, repo, path, branch);
            URL url = new URL(apiUrl);
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            
            con.addRequestProperty("Authorization", "Bearer " + token);
            con.addRequestProperty("Accept", "application/vnd.github+json");
            con.addRequestProperty("User-Agent", "MasterHunter-Addon");
            con.setRequestMethod("GET");
            
            int responseCode = con.getResponseCode();
            if (responseCode == 200) {
                // Read response
                BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                con.disconnect();
                
                // Extract SHA from JSON response (simple parsing)
                String jsonResponse = response.toString();
                int shaIndex = jsonResponse.indexOf("\"sha\":\"");
                if (shaIndex != -1) {
                    int startIndex = shaIndex + 7;
                    int endIndex = jsonResponse.indexOf("\"", startIndex);
                    return jsonResponse.substring(startIndex, endIndex);
                }
            }
            con.disconnect();
            return null;
        } catch (Exception e) {
            // File doesn't exist or other error - return null
            return null;
        }
    }
    
    /**
     * Escape special characters for JSON strings
     */
    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }
}
