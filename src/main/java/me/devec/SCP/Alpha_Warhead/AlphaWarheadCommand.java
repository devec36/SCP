package me.devec.SCP.Alpha_Warhead;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class AlphaWarheadCommand implements CommandExecutor, Listener {

    private final Plugin plugin;
    private final NamespacedKey key;

    public AlphaWarheadCommand(Plugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "alpha_warhead");
    }

    // COMMAND
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {

        if (args.length != 1) {
            sender.sendMessage("Usage: /alphawarhead <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("Player not found.");
            return true;
        }

        if(!target.hasPermission("SCP.alphawarhead") && !target.isOp()) return true;

        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();

        meta.customName(Component.text("Alpha Warhead", TextColor.color(255, 159, 0)));
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, 1);

        item.setItemMeta(meta);

        target.getInventory().addItem(item);
        sender.sendMessage("Given Alpha Warhead to " + target.getName());

        return true;
    }

    // LISTENER
    @EventHandler
    public void onUse(PlayerInteractEvent event) {

        if (!event.getAction().toString().contains("RIGHT_CLICK_BLOCK")) return;

        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        if (!item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.INTEGER)) return;

        if(event.getClickedBlock() == null) return;
        Location loc = event.getClickedBlock().getLocation().add(0.5, 1, 0.5);

        // call your warhead
        new AlphaWarhead(loc, plugin);

        // remove one item
        item.setAmount(item.getAmount() - 1);

        event.setCancelled(true);
    }

    @EventHandler
    public void onWarheadHit(EntityDamageByEntityEvent event) {
        // We only care if the thing being hit is an Interaction entity
        if (!(event.getEntity() instanceof Interaction interaction)) return;

        // Verify it's actually one of OUR warheads using the PDC key
        NamespacedKey idKey = new NamespacedKey(plugin, "warhead_id");
        if (!interaction.getPersistentDataContainer().has(idKey, PersistentDataType.STRING)) return;

        // Find the class instance by comparing the Hitbox UUID
        AlphaWarhead targetWarhead = null;
        for (AlphaWarhead aw : AlphaWarhead.ACTIVE_WARHEADS.values()) {
            if (aw.getHitbox().getUniqueId().equals(interaction.getUniqueId())) {
                targetWarhead = aw;
                break;
            }
        }

        if (targetWarhead != null) {
            // 1. Remove from the static map immediately (Memory Safety)
            AlphaWarhead.ACTIVE_WARHEADS.remove(targetWarhead.getBaseLocation());

            // 2. Get the visual entity ID from the Interaction's metadata to remove it
            String displayUuid = interaction.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
            if (displayUuid != null) {
                org.bukkit.entity.Entity visual = org.bukkit.Bukkit.getEntity(java.util.UUID.fromString(displayUuid));
                if (visual != null) visual.remove();
            }

            // 3. Remove the interaction entity itself
            interaction.remove();

            // 4. Refund the item to the player
            if (event.getDamager() instanceof Player player) {
                ItemStack item = new ItemStack(Material.NETHER_STAR);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.customName(Component.text("Alpha Warhead", TextColor.color(255, 159, 0)));
                    // Ensure 'key' is the same NamespacedKey used in your Command class
                    meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, 1);
                    item.setItemMeta(meta);

                    // Give item (drop on floor if inventory full)
                    player.getInventory().addItem(item).forEach((i, leftover) ->
                            player.getWorld().dropItemNaturally(player.getLocation(), leftover));

                    player.sendMessage(Component.text("Alpha Warhead disassembled.", TextColor.color(255, 159, 0)));
                }
            }

            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRedstone(BlockRedstoneEvent event) {
        // Only trigger when the power level increases
        if (event.getNewCurrent() <= 0 || event.getOldCurrent() > 0) return;

        org.bukkit.block.Block powerSource = event.getBlock();

        // We check a 2-block radius. This covers:
        // 1. Redstone touching the 3x3 base.
        // 2. Redstone powering a solid block that is part of the 3x3 base.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                // We check y between -1 and 1 in case redstone is above/below the base block
                for (int y = -1; y <= 1; y++) {
                    Location checkLoc = powerSource.getLocation().add(x, y, z);

                    // Get the warhead from the map we created in the AlphaWarhead class
                    AlphaWarhead warhead = AlphaWarhead.ACTIVE_WARHEADS.get(checkLoc.getBlock().getLocation());

                    if (warhead != null) {
                        warhead.detonate();
                        return;
                    }
                }
            }
        }
    }
}