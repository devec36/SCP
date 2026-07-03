package me.devec.SCP.SCPs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class JailBird implements Listener {

    private final Plugin plugin;
    private final Map<UUID, BukkitTask> activeCharges = new HashMap<>();
    private final Map<UUID, BukkitTask> activeDashes = new HashMap<>();

    // Tracks stored offhand items (Shields) while a player is charging
    private final Map<UUID, ItemStack> storedOffhands = new HashMap<>();

    // Custom internal cooldown tracker
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_DURATION = 1500L; // 1.5 seconds

    // Modern Paper Item Model keys (1.21.2+)
    private static final NamespacedKey MODEL_KEY = NamespacedKey.minecraft("jailbird");
    private static final NamespacedKey BROKEN_MODEL_KEY = NamespacedKey.minecraft("jailbirdbroken");

    public JailBird(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Spawns the JailBird weapon with a full durability bar and the default model.
     */
    public static ItemStack spawnJailBird() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("SCP 2536-2 (Jailbird)").color(NamedTextColor.AQUA));
            meta.lore(List.of(
                    Component.text("''WHAT?!''",NamedTextColor.GRAY),
                    Component.text("Right click to charge dash ability.",NamedTextColor.AQUA),
                    Component.text("Inspired by SCP: Secret Laboratory",NamedTextColor.AQUA)
            ));
            meta.setItemModel(MODEL_KEY);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Utility to check if an item is any variant of the JailBird.
     */
    private boolean isJailBird(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasItemModel()) return false;

        NamespacedKey model = meta.getItemModel();
        return MODEL_KEY.equals(model) || BROKEN_MODEL_KEY.equals(model);
    }

    /**
     * Helper utility to safely modify a player's step-height attribute.
     */
    private void setStepHeight(Player player, double height) {
        AttributeInstance stepAttribute = player.getAttribute(Attribute.STEP_HEIGHT);
        if (stepAttribute != null) {
            stepAttribute.setBaseValue(height);
        }
    }

    /**
     * Safety handler: If a player logs off, clean up their values immediately.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if(!(Objects.requireNonNull(player.getAttribute(Attribute.STEP_HEIGHT)).getBaseValue() == 2)) return;
        // Reset step height back to the vanilla standard (0.6 blocks)
        setStepHeight(player, 0.6);
        restoreOffhand(player);
    }

    /**
     * Handles Left Click: Calculates durability penalty based on swing charge.
     */
    @EventHandler
    public void onWeaponHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!isJailBird(weapon)) return;

        UUID uuid = player.getUniqueId();

        // 1. Calculate the cooldown percentage (0.0 to 1.0)
        float cooldown = player.getAttackCooldown();

        // 2. Prevent the hit entirely if they swing way too early (e.g., < 20% charge)
        // This feels better than cancelling the whole thing for minor timing errors.
        if (cooldown < 0.2F) {
            event.setCancelled(true);
            return;
        }

        if (event.getEntity() instanceof LivingEntity target) {
            if (target.getAbsorptionAmount() > 0) {
                target.setAbsorptionAmount(0);
                player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
            }

            if (target instanceof Player targetPlayer && targetPlayer.isBlocking()) {
                targetPlayer.setCooldown(Material.SHIELD, 100);
                targetPlayer.completeUsingActiveItem();
                targetPlayer.playSound(targetPlayer.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0f, 1.0f);
            }
        }

        // If they are dashing, we skip the durability penalty
        if (activeDashes.containsKey(uuid)) {
            return;
        }

        // 3. Dynamic Durability Penalty:
        // If cooldown is 0.5 (half-charged), damage is 50. If 1.0, damage is 100.
        int basePenalty = 100;
        int scaledPenalty = (int) (basePenalty * cooldown);

        handleDurability(player, weapon, scaledPenalty, false);
    }

    /**
     * Handles Right Click: Charging the weapon.
     */
    @EventHandler
    public void onWeaponRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!isJailBird(weapon)) return;

        UUID uuid = player.getUniqueId();

        if (activeCharges.containsKey(uuid) || activeDashes.containsKey(uuid)) {
            return;
        }

        // Check internal custom cooldown
        if (cooldowns.containsKey(uuid)) {
            long timeLeft = cooldowns.get(uuid) - System.currentTimeMillis();
            if (timeLeft > 0) {
                double secondsLeft = Math.ceil(timeLeft / 100.0) / 10.0;
                player.sendActionBar(Component.text("Jailbird on cooldown (" + secondsLeft + "s)").color(NamedTextColor.RED));
                return;
            }
        }

        startCharge(player);
    }

    private void startCharge(Player player) {
        UUID uuid = player.getUniqueId();

        setStepHeight(player, 2.0);

        ItemStack offhandItem = player.getInventory().getItemInOffHand();
        if (offhandItem != null && offhandItem.getType() != Material.AIR) {
            storedOffhands.put(uuid, offhandItem.clone());
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        }

        BukkitTask chargeTask = new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 30;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    setStepHeight(player, 0.6); // Safety reset
                    restoreOffhand(player);
                    activeCharges.remove(uuid);
                    this.cancel();
                    return;
                }

                if (!isJailBird(player.getInventory().getItemInMainHand())) {
                    player.sendActionBar(Component.text("Charge Cancelled").color(NamedTextColor.RED));

                    setStepHeight(player, 0.6);
                    restoreOffhand(player);
                    activeCharges.remove(uuid);
                    cooldowns.put(uuid, System.currentTimeMillis() + 1000L);
                    this.cancel();
                    return;
                }

                ticks++;

                int progressBars = (int) (((double) ticks / maxTicks) * 10);
                String bar = "§c[" + "§a|".repeat(Math.max(0, progressBars)) +
                        "§7|".repeat(Math.max(0, 10 - progressBars)) +
                        "§c]";
                player.sendActionBar(Component.text("Charging: " + bar));

                if (ticks >= maxTicks) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 2.0f);
                    activeCharges.remove(uuid);
                    this.cancel();
                    startDash(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        activeCharges.put(uuid, chargeTask);
    }

    private void startDash(Player player) {
        UUID uuid = player.getUniqueId();

        BukkitTask dashTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cleanupDash(player);
                    this.cancel();
                    return;
                }

                ticks++;

                // Movement Math (Safe)
                Vector direction = player.getLocation().getDirection();
                direction.setY(0);
                if (direction.lengthSquared() > 0) direction.normalize();
                else direction = player.getLocation().getDirection().setY(0).normalize();

                direction.multiply(1.5);
                player.setVelocity(new Vector(direction.getX(), player.getVelocity().getY(), direction.getZ()));
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 5, 0.2, 0.2, 0.2, 0.05);

                // Collision Detection
                for (org.bukkit.entity.Entity entity : player.getNearbyEntities(1.5, 1.5, 1.5)) {
                    if (entity instanceof LivingEntity target && entity != player) {

                        // 1. Primary Hit
                        player.swingMainHand();
                        Location impactLoc = target.getLocation();
                        target.damage(30.0, player);
                        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);

                        // 2. AOE Hit (Using World-based search for higher reliability)
                        target.getWorld().spawnParticle(Particle.EXPLOSION, impactLoc, 3);

                        for (org.bukkit.entity.Entity aoeTarget : impactLoc.getWorld().getNearbyEntities(impactLoc, 3.0, 3.0, 3.0)) {
                            if (aoeTarget instanceof LivingEntity living && living != player && living != target) {
                                living.damage(20.0, player);
                                // Optional: Add slight knockback
                                player.sendMessage("Gave aoe to: "+aoeTarget.getName());
                                living.setVelocity(living.getLocation().toVector().subtract(impactLoc.toVector()).normalize().multiply(0.5));
                            }
                        }

                        cleanupDash(player);
                        handleDurability(player, player.getInventory().getItemInMainHand(), 500, true);
                        this.cancel();
                        return;
                    }
                }

                if (ticks >= 40) {
                    cleanupDash(player);
                    handleDurability(player, player.getInventory().getItemInMainHand(), 300, true);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        activeDashes.put(uuid, dashTask);
    }

    // Helper to keep cleanup consistent
    private void cleanupDash(Player player) {
        UUID uuid = player.getUniqueId();
        activeDashes.remove(uuid);
        cooldowns.put(uuid, System.currentTimeMillis() + COOLDOWN_DURATION);
        setStepHeight(player, 0.6);
        restoreOffhand(player);
    }

    /**
     * Safely returns the unequipped item back to the offhand, or drops it if space shifted.
     */
    private void restoreOffhand(Player player) {
        UUID uuid = player.getUniqueId();
        if (!storedOffhands.containsKey(uuid)) return;

        ItemStack originalItem = storedOffhands.remove(uuid);

        if (player.getInventory().getItemInOffHand().getType() == Material.AIR) {
            player.getInventory().setItemInOffHand(originalItem);
        } else {
            HashMap<Integer, ItemStack> leftOvers = player.getInventory().addItem(originalItem);
            if (!leftOvers.isEmpty()) {
                for (ItemStack remainingItem : leftOvers.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), remainingItem);
                }
            }
        }
    }

    /**
     * Centralized method to handle custom durability depletion and dynamic item model swapping.
     */
    private void handleDurability(Player player, ItemStack weapon, int damageAmount, boolean isChargeSwing) {
        if (weapon == null || !weapon.hasItemMeta()) return;
        ItemMeta meta = weapon.getItemMeta();

        if (meta instanceof Damageable damageable) {
            int currentDamage = damageable.getDamage();
            int maxDurability = weapon.getType().getMaxDurability();

            int newDamage = currentDamage + damageAmount;

            if (newDamage >= maxDurability) {
                weapon.setAmount(0); // Weapon shattered completely

                if (isChargeSwing) {
                    player.getWorld().createExplosion(player.getLocation(), 7.0F, true, true);
                    player.sendMessage(Component.text("Your Jailbird exploded!").color(NamedTextColor.DARK_RED));
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                }
            } else {
                damageable.setDamage(newDamage);

                // Check remaining durability (Max is 2031 for Netherite)
                if ((maxDurability - newDamage) <= 500) {
                    meta.setItemModel(BROKEN_MODEL_KEY);
                } else {
                    meta.setItemModel(MODEL_KEY);
                }

                weapon.setItemMeta(meta);
            }
        }
    }
}