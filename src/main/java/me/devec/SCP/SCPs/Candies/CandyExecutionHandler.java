package me.devec.SCP.SCPs.Candies;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CandyExecutionHandler implements Listener {

    private final Candy candyInstance;
    private final Plugin plugin;

    private final Map<UUID, PotionEffect> previousSpeedEffects = new HashMap<>();

    public CandyExecutionHandler(Plugin plugin) {
        this.plugin = plugin;
        this.candyInstance = new Candy(plugin);
    }

    @EventHandler
    public void onCandyUse(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();

        if (!meta.hasItemModel()) return;

        NamespacedKey modelKey = meta.getItemModel();
        if (modelKey == null) return;

        String modelString = modelKey.getKey().toLowerCase();

        if (!modelString.equals("pinkcandy") &&
                !modelString.equals("blackcandy")) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (previousSpeedEffects.containsKey(uuid)) {
            return;
        }

        previousSpeedEffects.put(
                uuid,
                player.getPotionEffect(PotionEffectType.SPEED)
        );

        player.addPotionEffect(
                new PotionEffect(
                        PotionEffectType.SPEED,
                        PotionEffect.INFINITE_DURATION,
                        19, // Speed III
                        false,
                        false
                )
        );

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cleanup(player);
                    cancel();
                    return;
                }

                if (!player.isHandRaised()) {
                    cleanup(player);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler
    public void onPlayerEatCandy(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        Player player = event.getPlayer();

        cleanup(player);

        if (!meta.hasItemModel()) return;

        NamespacedKey modelKey = meta.getItemModel();
        if (modelKey == null) return;

        String modelString = modelKey.getKey().toLowerCase();

        switch (modelString) {
            case "pinkcandy":
                candyInstance.pinkCandy(player);
                break;

            case "blackcandy":
                candyInstance.blackCandy(player);
                break;

            case "yellowcandy":
                candyInstance.yellowCandy(player);
                break;

            case "bluecandy":
                candyInstance.blueCandy(player);
                break;

            case "redcandy":
                candyInstance.redCandy(player);
                break;

            case "greencandy":
                candyInstance.greenCandy(player);
                break;

            case "purplecandy":
                candyInstance.purpleCandy(player);
                break;

            default:
                break;
        }
    }

    private void cleanup(Player player) {
        UUID uuid = player.getUniqueId();

        if (!previousSpeedEffects.containsKey(uuid)) {
            return;
        }

        PotionEffect oldEffect = previousSpeedEffects.remove(uuid);

        player.removePotionEffect(PotionEffectType.SPEED);

        if (oldEffect != null) {
            player.addPotionEffect(oldEffect);
        }
    }
}