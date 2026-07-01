package me.devec.SCP.SCPs;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PreformanceEnchancer implements Listener {

    private final JavaPlugin plugin;

    private static NamespacedKey scpKey;
    private static NamespacedKey scpTimerKey;
    private static NamespacedKey speedModifierKey;
    private static NamespacedKey attackModifierKey;
    private static NamespacedKey miningModifierKey;
    private static NamespacedKey swimModifierKey;

    private final Map<UUID, Map<UUID, Long>> aggressivePlayers = new HashMap<>();
    private final Map<UUID, Integer> lastDangerStacks = new HashMap<>();

    // 5 minutes total running time (300,000 Milliseconds)
    private static final long SCP_DURATION_MS = 300000L;

    public PreformanceEnchancer(JavaPlugin plugin) {
        this.plugin = plugin;
        scpKey = new NamespacedKey(plugin, "scp_1853_active");
        scpTimerKey = new NamespacedKey(plugin, "scp_1853_timer");
        speedModifierKey = new NamespacedKey(plugin, "scp_1853_speed");
        attackModifierKey = new NamespacedKey(plugin, "scp_1853_attack");
        miningModifierKey = new NamespacedKey(plugin, "scp_1853_mining");
        swimModifierKey = new NamespacedKey(plugin, "scp_1853_swim");

        startTrackingTask();
    }

    public static NamespacedKey getScpKey() {
        return scpKey;
    }

    public static ItemStack createItem() {
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("SCP-1853 (Performance Enhancer)")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(List.of(
                    Component.text("+ gives the player buffs when their life is in danger").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                    Component.text("- drains hunger.").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                    Component.text("- can't be used with any swiftness enhancements").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                    Component.text("Lasts 5 minutes before completely leaving the body.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true)
            ));

            meta.setColor(Color.fromRGB(57, 255, 20));
            meta.setItemModel(NamespacedKey.minecraft("preformanceenchancer"));
            potion.setItemMeta(meta);
            hidePotionEffects(potion);
        }
        return potion;
    }

    public static void removeAllPerformanceEffects(Player player) {
        removeAllPerformanceEffects(player, false);
    }

    public static void removeAllPerformanceEffects(Player player, boolean sendNotification) {
        if (scpKey == null) return;

        player.getPersistentDataContainer().remove(scpKey);
        player.getPersistentDataContainer().remove(scpTimerKey);

        if (sendNotification) {
            player.sendMessage(Component.text("The effects of the Performance Enhancer have completely worn off.", NamedTextColor.RED, TextDecoration.ITALIC));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.5f);

        AttributeInstance attackAttr = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackAttr != null) attackAttr.removeModifier(attackModifierKey);

        AttributeInstance miningAttr = player.getAttribute(Attribute.MINING_EFFICIENCY);
        if (miningAttr != null) miningAttr.removeModifier(miningModifierKey);

        AttributeInstance speedAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.removeModifier(speedModifierKey);

        AttributeInstance swimAttr = player.getAttribute(Attribute.WATER_MOVEMENT_EFFICIENCY);
        if (swimAttr != null) swimAttr.removeModifier(swimModifierKey);
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item.getType() == Material.POTION && item.hasItemMeta()) {
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            if (meta != null && meta.hasItemModel()) {
                NamespacedKey modelKey = meta.getItemModel();
                if (modelKey != null && modelKey.equals(NamespacedKey.minecraft("preformanceenchancer"))) {
                    Player player = event.getPlayer();

                    NamespacedKey colaKey = new NamespacedKey(plugin, "scp_207_active");
                    if (player.getPersistentDataContainer().has(colaKey, PersistentDataType.BOOLEAN)) {
                        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
                            player.sendMessage(Component.text("The structural stimulants create a violent internal detonation!", NamedTextColor.DARK_RED, TextDecoration.BOLD));

                            // Real mechanical explosion (flash, sound, and knockback without world terrain griefing)
                            Location loc = player.getLocation().add(0, 1, 0);
                            loc.getWorld().createExplosion(loc, 3.0f, false, false);

                            removeAllPerformanceEffects(player);
                            Cola.removeAllColaEffects(player);

                            player.damage(100.0);
                            return;
                        }
                    }

                    applyScp1853(player);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (player.getPersistentDataContainer().has(scpKey, PersistentDataType.BOOLEAN)) {
            long timeLeftMs = player.getPersistentDataContainer().getOrDefault(scpTimerKey, PersistentDataType.LONG, 0L);

            if (timeLeftMs <= 0) {
                removeAllPerformanceEffects(player);
                lastDangerStacks.remove(player.getUniqueId());
            }
            // Removed fixed hardcoded baseline attack modifier here to prevent persistent bypass bugs
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (player.getPersistentDataContainer().has(scpKey, PersistentDataType.BOOLEAN)) {
            removeAllPerformanceEffects(player);
            lastDangerStacks.remove(player.getUniqueId());
            aggressivePlayers.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim) {
            if (victim.getPersistentDataContainer().has(scpKey, PersistentDataType.BOOLEAN)) {

                ItemStack activeItem = victim.getActiveItem();
                if (victim.isHandRaised() && activeItem != null && activeItem.getType().toString().contains("SHIELD")) {
                    event.setCancelled(true);
                    victim.playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
                }

                if (!event.isCancelled()) {
                    Player attacker = null;

                    if (event.getDamager() instanceof Player directPlayer) {
                        attacker = directPlayer;
                    }
                    else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
                        attacker = shooter;
                    }

                    if (attacker != null && attacker != victim) {
                        aggressivePlayers.computeIfAbsent(victim.getUniqueId(), k -> new HashMap<>())
                                .put(attacker.getUniqueId(), System.currentTimeMillis() + 6000L);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPotionApply(EntityPotionEffectEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (!player.getPersistentDataContainer().has(scpKey, PersistentDataType.BOOLEAN)) return;

            if (event.getNewEffect() != null && event.getNewEffect().getType().equals(PotionEffectType.SPEED)) {
                player.sendMessage(Component.text("The chemical components clash violently in your veins!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 300, 2));
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_HURT, 1.0f, 0.5f);
            }
        }
    }

    private void applyScp1853(Player player) {
        player.getPersistentDataContainer().set(scpKey, PersistentDataType.BOOLEAN, true);
        player.getPersistentDataContainer().set(scpTimerKey, PersistentDataType.LONG, SCP_DURATION_MS);

        player.sendMessage(Component.text("You feel an intense, competitive hyper-focus take over your body.", NamedTextColor.GREEN, TextDecoration.ITALIC));
        player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 1.5f);
        // Removed flat +100 attack speed injection so modifications only update dynamically
    }

    private void startTrackingTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.getPersistentDataContainer().has(scpKey, PersistentDataType.BOOLEAN)) continue;

                if (player.isDead()) {
                    removeAllPerformanceEffects(player);
                    lastDangerStacks.remove(player.getUniqueId());
                    continue;
                }

                long timeLeft = player.getPersistentDataContainer().getOrDefault(scpTimerKey, PersistentDataType.LONG, 0L);
                timeLeft -= 1000L;

                if (timeLeft <= 0) {
                    removeAllPerformanceEffects(player, true);
                    lastDangerStacks.remove(player.getUniqueId());
                    continue;
                } else {
                    player.getPersistentDataContainer().set(scpTimerKey, PersistentDataType.LONG, timeLeft);
                }

                double threatScore = 0.0;
                UUID playerUUID = player.getUniqueId();

                AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
                double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
                double missingHealth = maxHealth - player.getHealth();
                threatScore += (missingHealth / 4.0);

                Map<UUID, Long> attackers = aggressivePlayers.getOrDefault(playerUUID, new HashMap<>());
                attackers.entrySet().removeIf(entry -> now > entry.getValue());

                for (Entity entity : player.getNearbyEntities(64, 64, 64)) {
                    if (entity instanceof Player nearbyPlayer) {
                        if (attackers.containsKey(nearbyPlayer.getUniqueId())) {
                            threatScore += 2.0;
                        }
                    }
                    else if (entity instanceof Enemy enemy) {
                        if (enemy instanceof Mob mob && mob.getTarget() == player) {
                            threatScore += getThreatWeight(entity.getType());
                        } else if (entity instanceof Boss) {
                            threatScore += getThreatWeight(entity.getType());
                        }
                    }
                    else if (entity.getType() == EntityType.ENDER_DRAGON || entity.getType() == EntityType.WITHER) {
                        threatScore += 5.0;
                    }
                }

                int dangerStacks = (int) Math.min(Math.floor(threatScore), 5);

                int previousStacks = lastDangerStacks.getOrDefault(playerUUID, 0);
                if (dangerStacks != previousStacks) {
                    lastDangerStacks.put(playerUUID, dangerStacks);
                }

                float exhaustionRate = (dangerStacks == 0) ? 0.05f : 1.0f + (dangerStacks * 0.5f);
                player.setExhaustion(player.getExhaustion() + exhaustionRate);

                if (maxHealthAttr != null && dangerStacks > 0 && player.getHealth() > 0 && player.getHealth() < maxHealthAttr.getValue()) {
                    double healAmount = dangerStacks * 0.2;
                    double missingAmount = maxHealthAttr.getValue() - player.getHealth();
                    double secureHeal = Math.min(healAmount, missingAmount);

                    if (secureHeal > 0) {
                        player.heal(secureHeal);
                    }
                }

                AttributeInstance speedAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
                AttributeInstance swimAttr = player.getAttribute(Attribute.WATER_MOVEMENT_EFFICIENCY);
                AttributeInstance miningAttr = player.getAttribute(Attribute.MINING_EFFICIENCY);
                AttributeInstance attackAttr = player.getAttribute(Attribute.ATTACK_SPEED);

                if (speedAttr != null) speedAttr.removeModifier(speedModifierKey);
                if (swimAttr != null) swimAttr.removeModifier(swimModifierKey);
                if (miningAttr != null) miningAttr.removeModifier(miningModifierKey);
                if (attackAttr != null) attackAttr.removeModifier(attackModifierKey);

                if (dangerStacks > 0) {
                    double speedBonus = dangerStacks * 0.015;
                    if (speedAttr != null) {
                        speedAttr.addModifier(new AttributeModifier(speedModifierKey, speedBonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
                    }

                    double swimBonus = dangerStacks * 0.08;
                    if (swimAttr != null) {
                        swimAttr.addModifier(new AttributeModifier(swimModifierKey, swimBonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
                    }

                    if (miningAttr != null) {
                        double miningBonus = dangerStacks * 1.0;
                        miningAttr.addModifier(new AttributeModifier(miningModifierKey, miningBonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
                    }

                    // Dynamically scales attack speed scaling up linearly (+1.5 per stack), then removes cooldown (+100.0) ONLY at Danger 5
                    if (attackAttr != null) {
                        double attackBonus = (dangerStacks == 5) ? 100.0 : dangerStacks * 1.5;
                        attackAttr.addModifier(new AttributeModifier(attackModifierKey, attackBonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
                    }

                    if (Bukkit.getCurrentTick() % 40 == 0) {
                        player.playSound(player.getLocation(), Sound.BLOCK_CONDUIT_AMBIENT, 0.6f, 1.8f);
                    }
                }
            }
        }, 20L, 20L);
    }

    private double getThreatWeight(EntityType type) {
        return switch (type) {
            case WARDEN, WITHER, ENDER_DRAGON -> 5.0;
            case ELDER_GUARDIAN, RAVAGER, PIGLIN_BRUTE, EVOKER, VINDICATOR -> 1.5;
            case BREEZE, CREEPER, BLAZE, GHAST, PHANTOM, WITHER_SKELETON, SHULKER, PILLAGER, GUARDIAN -> 1.0;
            case ENDERMAN -> 0.8;
            case SKELETON, STRAY, BOGGED, WITCH, CAVE_SPIDER, VEX -> 0.5;
            case ZOMBIE, HUSK, DROWNED, PIGLIN, ZOMBIFIED_PIGLIN, SLIME, MAGMA_CUBE, HOGLIN, ZOGLIN -> 0.4;
            default -> 0.3;
        };
    }

    public static void hidePotionEffects(ItemStack item) {
        TooltipDisplay existingDisplay = item.getData(DataComponentTypes.TOOLTIP_DISPLAY);
        TooltipDisplay.Builder builder = TooltipDisplay.tooltipDisplay();

        if (existingDisplay != null) {
            builder.hiddenComponents(existingDisplay.hiddenComponents());
        }

        builder.addHiddenComponents(DataComponentTypes.POTION_CONTENTS);
        builder.addHiddenComponents(DataComponentTypes.POTION_DURATION_SCALE);

        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, builder.build());
    }
}