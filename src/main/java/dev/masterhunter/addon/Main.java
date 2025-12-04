package dev.masterhunter.addon;

import com.mojang.logging.LogUtils;
import dev.masterhunter.addon.hud.EntityList;
import dev.masterhunter.addon.hud.TotemCount;
import dev.masterhunter.addon.hud.CrystalCount;
import dev.masterhunter.addon.hud.DubCountGUI;
import dev.masterhunter.addon.hud.ETA;
import dev.masterhunter.addon.hud.LagDetector;
import dev.masterhunter.addon.modules.Hunting.NewChunksPlus;
import dev.masterhunter.addon.modules.Hunting.NetherTerrainHunter;
import dev.masterhunter.addon.modules.Hunting.MobTrackerPlus;
import dev.masterhunter.addon.modules.Hunting.LooseItemsWaypoints;
import dev.masterhunter.addon.modules.Hunting.BrokenPortalWaypoints;
import dev.masterhunter.addon.modules.Movement.AFKVanillaFly;
import dev.masterhunter.addon.modules.Movement.AutoAdjustTimer;
import dev.masterhunter.addon.modules.Movement.ElytraRedeploy;
import dev.masterhunter.addon.modules.Movement.GrimScaffold;
import dev.masterhunter.addon.modules.Utility.GrimAirPlace;
import dev.masterhunter.addon.modules.Hunting.StashFinderPlus;
import dev.masterhunter.addon.modules.Hunting.TrailFollower;
import dev.masterhunter.addon.modules.Movement.Pitch40Util;
import dev.masterhunter.addon.modules.Movement.searcharea.SearchArea;
import dev.masterhunter.addon.modules.Render.ModItemESP;
import dev.masterhunter.addon.modules.Render.OverworldMobHighlight;
import dev.masterhunter.addon.modules.Render.PearlOwner;
import dev.masterhunter.addon.modules.Render.SignRender;
import dev.masterhunter.addon.modules.Render.VanityESP;
import dev.masterhunter.addon.modules.Utility.AntiSpam;
import dev.masterhunter.addon.modules.Utility.AutoLogPlus;
import dev.masterhunter.addon.modules.Utility.AutoShulker;
import dev.masterhunter.addon.modules.Utility.DiscordNotifications;
import dev.masterhunter.addon.modules.Utility.DubCount;
import dev.masterhunter.addon.modules.Utility.PortalMaker;
import dev.masterhunter.addon.modules.Utility.PortalAutoLog;
import dev.masterhunter.addon.modules.Utility.PlayerHistory;
import dev.masterhunter.addon.modules.Utility.ChatTracker;
import dev.masterhunter.addon.modules.Utility.MapExporter;
import dev.masterhunter.addon.modules.Utility.ElytraSwap;
import dev.masterhunter.addon.modules.Utility.LinkFilter;
import dev.masterhunter.addon.modules.Utility.CoordinateLeakLogger;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class Main extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Master Client");
    public static final HudGroup HUD_GROUP = new HudGroup("Master-Hunter");


    @Override
    public void onInitialize() {
        LOG.info("Initializing Master-Hunter");

        // Modules
         Modules.get().add(new PortalMaker());
         Modules.get().add(new PortalAutoLog());
         Modules.get().add(new DiscordNotifications());
         Modules.get().add(new StashFinderPlus());
         Modules.get().add(new MobTrackerPlus());
         Modules.get().add(new LooseItemsWaypoints());
         Modules.get().add(new NetherTerrainHunter());
         Modules.get().add(new BrokenPortalWaypoints());
         Modules.get().add(new Pitch40Util());
         Modules.get().add(new NewChunksPlus());
         Modules.get().add(new ModItemESP());
         Modules.get().add(new OverworldMobHighlight());
         Modules.get().add(new PearlOwner());
         Modules.get().add(new SignRender());
         Modules.get().add(new VanityESP());
         Modules.get().add(new SearchArea());
         Modules.get().add(new AntiSpam());
         Modules.get().add(new LinkFilter());
         Modules.get().add(new AutoLogPlus());
         Modules.get().add(new AFKVanillaFly());
         Modules.get().add(new AutoAdjustTimer());
         Modules.get().add(new AutoShulker());
         Modules.get().add(new ElytraRedeploy());
         Modules.get().add(new DubCount());
         Modules.get().add(new GrimScaffold());
         Modules.get().add(new GrimAirPlace());
         Modules.get().add(new PlayerHistory());
         Modules.get().add(new ChatTracker());
         Modules.get().add(new MapExporter());
         Modules.get().add(new ElytraSwap());
         Modules.get().add(new VanityESP());
         Modules.get().add(new CoordinateLeakLogger());
         
         // Only add modules that require Baritone if Baritone is available
         try {
             Class.forName("baritone.api.BaritoneAPI");;
             Modules.get().add(new TrailFollower());
             LOG.info("TrailFollower loaded (Baritone detected)");
         } catch (ClassNotFoundException e) {
             LOG.info("TrailFollower not loaded (Baritone not found)");
         }
         
        // Commands
        // Commands.add(new CommandExample());

        // HUD
        Hud.get().register(EntityList.INFO);
        Hud.get().register(TotemCount.INFO);
        Hud.get().register(CrystalCount.INFO);
        Hud.get().register(DubCountGUI.INFO);
        Hud.get().register(ETA.INFO);
        Hud.get().register(LagDetector.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "dev.masterhunter.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("KingSoft69", "MasterHunter");
    }
}
