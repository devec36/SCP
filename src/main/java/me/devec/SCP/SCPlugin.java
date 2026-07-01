package me.devec.SCP;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import me.devec.SCP.Alpha_Warhead.AlphaWarheadCommand;
import me.devec.SCP.SCPs.*;
import me.devec.SCP.commands.GiveSCPCommand;
import me.devec.SCP.SCPs.Candies.CandyBowl;
import me.devec.SCP.SCPs.Candies.CandyExecutionHandler;
import me.devec.SCP.Utilities.PacketListeners.IdentityPacketListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Objects;

public final class SCPlugin extends JavaPlugin implements Listener {

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {

        PacketEvents.getAPI().init();

        getServer().getPluginManager().registerEvents(this, this);

        // Alpha Warhead
        AlphaWarheadCommand aw = new AlphaWarheadCommand(this);
        Objects.requireNonNull(getCommand("alphawarhead")).setExecutor(aw);
        getServer().getPluginManager().registerEvents(aw, this);

        // Obsidian Knife
        ObsidianKnife ok = new ObsidianKnife(this);
        getServer().getPluginManager().registerEvents(ok, this);

        IdentityPacketListener idp = new IdentityPacketListener(ok);
        PacketEvents.getAPI().getEventManager().registerListener(
                idp,
                com.github.retrooper.packetevents.event.PacketListenerPriority.NORMAL
        );

        // Candy
        CandyExecutionHandler ceh = new CandyExecutionHandler(this);
        getServer().getPluginManager().registerEvents(ceh, this);

        // Jailbird
        JailBird jb = new JailBird(this);
        getServer().getPluginManager().registerEvents(jb, this);

        // Preformance Enchancer
        PreformanceEnchancer pe = new PreformanceEnchancer(this);
        getServer().getPluginManager().registerEvents(pe, this);

        // CandyBowl
        CandyBowl cb = new CandyBowl(this);
        getServer().getPluginManager().registerEvents(cb, this);

        // Panacea
        Panacea panacea = new Panacea();
        getServer().getPluginManager().registerEvents(panacea, this);

        // SuperBall
        SuperBall sb = new SuperBall(this);
        getServer().getPluginManager().registerEvents(sb, this);

        // Cola
        Cola c = new Cola(this);
        getServer().getPluginManager().registerEvents(c,this);

        // Register command
        GiveSCPCommand giveCommand = new GiveSCPCommand(ok, cb);
        Objects.requireNonNull(this.getCommand("givescp")).setExecutor(giveCommand);
        Objects.requireNonNull(this.getCommand("givescp")).setTabCompleter(giveCommand);
    }

    @Override
    public void onDisable() {
        ObsidianKnife.globalServerCleanup();
        PacketEvents.getAPI().terminate();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent p){
        p.getPlayer().setResourcePack("https://cdn.modrinth.com/data/MHKGVuaI/versions/dTckcDXW/scp%20pack.zip?mr_download_reason=standalone","");
    }
}