package me.devec.SCP.SCPs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class Panacea implements Listener {

    // Helper method using modern Adventure Component API
    public static ItemStack createPanaceaItem() {
        ItemStack item = new ItemStack(Material.POTION);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Set the modern item_model key
            meta.setItemModel(NamespacedKey.minecraft("panacea"));

            // Modern non-deprecated display name using Adventure Components
            meta.displayName(Component.text("SCP-500 (Panacea)", NamedTextColor.DARK_RED));

            // Modern non-deprecated lore lines
            meta.lore(List.of(
                    Component.text("''One small pill, a cure beyond reason.''", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true),
                    Component.text("Fully heals the user, purges negative", NamedTextColor.RED),
                    Component.text("effects and provides strong regeneration.", NamedTextColor.RED)
            ));

            item.setItemMeta(meta);
            PreformanceEnchancer.hidePotionEffects(item);
        }
        return item;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return;

        NamespacedKey itemModelKey = meta.getItemModel();

        if (itemModelKey != null && itemModelKey.getKey().equalsIgnoreCase("panacea")) {
            Player player = event.getPlayer();

            // 1. Restore health to full base maximum dynamically
            AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                player.setHealth(maxHealthAttr.getValue());
            }

            // 2. Loop and remove any effect classified as HARMFUL
            for (PotionEffect effect : player.getActivePotionEffects()) {
                if (effect.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL) {
                    player.removePotionEffect(effect.getType());
                }
            }

            // 3. Give heavy Regeneration IV for 10 seconds (200 ticks)
            PotionEffect heavyRegen = new PotionEffect(PotionEffectType.REGENERATION, 200, 3, true, true);
            player.addPotionEffect(heavyRegen);

            // Modern action or chat messaging via Component
            player.sendMessage(Component.text("You consume the Panacea. All ailments are cured and your body surges with life!", NamedTextColor.GREEN));
            player.sendMessage(Component.text("All anomalous effects have worn off.", NamedTextColor.GREEN));

            Cola.removeAllColaEffects(player);
            PreformanceEnchancer.removeAllPerformanceEffects(player);
        }
    }
}