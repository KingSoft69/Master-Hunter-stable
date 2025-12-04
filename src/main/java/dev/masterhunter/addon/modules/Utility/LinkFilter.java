package dev.masterhunter.addon.modules.Utility;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import dev.masterhunter.addon.Main;

import java.util.regex.Pattern;

public class LinkFilter extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> filterDiscordLinks = sgGeneral.add(new BoolSetting.Builder()
        .name("Filter Discord Links")
        .description("Hides messages containing discord.gg or .gg links")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> filterWebsiteLinks = sgGeneral.add(new BoolSetting.Builder()
        .name("Filter Website Links")
        .description("Hides messages containing website URLs (http://, https://, www., .com, .net, etc.)")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> filterIpAddresses = sgGeneral.add(new BoolSetting.Builder()
        .name("Filter IP Addresses")
        .description("Hides messages containing IP addresses with ports")
        .defaultValue(false)
        .build()
    );

    // Regex patterns for link detection
    private static final Pattern DISCORD_LINK_PATTERN = Pattern.compile(
        "(?i)(discord\\.gg|discordapp\\.com\\/invite)\\/[a-zA-Z0-9]+|\\.gg\\/[a-zA-Z0-9]+",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern WEBSITE_LINK_PATTERN = Pattern.compile(
        "(?i)(https?:\\/\\/|www\\.)[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&\\/\\/=]*)|" +
        "(?<![a-zA-Z0-9])([a-zA-Z0-9][-a-zA-Z0-9]{0,62}\\.)+(?:com|net|org|edu|gov|mil|co|io|me|tv|gg|xyz|info|biz|online|site|tech|store|app|dev|cloud|ai|live|pro|vip|top|fun|world|life|space|website|us|uk|de|fr|ca|au|jp|cn|in|ru|br|mx|es|it|nl|pl|se|no|dk|fi|be|ch|at|nz|kr|tw|hk|sg|my|th|vn|id|ph|pk|bd|tr|ua|za|ar|cl|pe|ve|co|ec|bo|py|uy|cr|gt|hn|pa|ni|sv|do|cu|jm|ht|tt|bs|bb|gd|lc|vc|ag|dm|kn|an|aw|bz|sr|gf|gp|mq|pm|bl|mf|sx|cw|bq|tc|vg|ky|bm|ms|ai|fk|gi|im|je|gg|fo|gl|pm|re|yt|tf|wf|pf|nc|vu|fj|sb|pg|ki|nr|tv|to|ws|sm|fm|mh|pw|as|gu|mp|um|vi|pr)(?![a-zA-Z0-9])",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern IP_ADDRESS_PATTERN = Pattern.compile(
        "(?<![0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?::[0-9]{1,5})?(?![0-9])"
    );

    public LinkFilter() {
        super(Main.CATEGORY, "Link-Filter", "Prevents chat messages containing Discord invites or website links from being displayed");
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        String content = event.getMessage().getString();
        
        // Check for Discord links
        if (filterDiscordLinks.get() && DISCORD_LINK_PATTERN.matcher(content).find()) {
            event.cancel();
            return;
        }
        
        // Check for website links
        if (filterWebsiteLinks.get() && WEBSITE_LINK_PATTERN.matcher(content).find()) {
            event.cancel();
            return;
        }
        
        // Check for IP addresses with ports
        if (filterIpAddresses.get() && IP_ADDRESS_PATTERN.matcher(content).find()) {
            event.cancel();
            return;
        }
    }
}
