package me.devec.SCP;

import me.devec.SCP.Alpha_Warhead.AlphaWarheadCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class SCPlugin extends JavaPlugin {

    @Override
    public void onEnable() {

        // Alpha Warhead
        AlphaWarheadCommand aw = new AlphaWarheadCommand(this);
        Objects.requireNonNull(getCommand("alphawarhead")).setExecutor(aw);
        getServer().getPluginManager().registerEvents(aw, this);

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
