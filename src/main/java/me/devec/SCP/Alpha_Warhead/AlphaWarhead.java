package me.devec.SCP.Alpha_Warhead;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class AlphaWarhead {

    // Global registry to map Block Locations to their specific Warhead instance
    // ConcurrentHashMap prevents memory leaks and allows safe access from listeners
    public static final Map<Location, AlphaWarhead> ACTIVE_WARHEADS = new ConcurrentHashMap<>();

    private final ItemDisplay warhead;
    private final Interaction hitbox;
    private final Plugin plugin;
    private final Location baseLocation;
    private boolean isTriggered = false;

    public AlphaWarhead(Location location, Plugin plugin) {
        this.plugin = plugin;
        this.baseLocation = location.getBlock().getLocation(); // Normalize to block grid

        // Visual Position (Offset logic from your original script)
        Location visualLoc = baseLocation.clone().add(0, 2.75, 0);

        this.warhead = visualLoc.getWorld().spawn(visualLoc, ItemDisplay.class, wh -> {
            ItemStack stack = new ItemStack(Material.STICK);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setItemModel(NamespacedKey.fromString("alpha_warhead"));
                stack.setItemMeta(meta);
            }
            wh.setItemStack(stack);

            Transformation tr = wh.getTransformation();
            tr.getScale().set(5f, 5f, 5f);
            wh.setTransformation(tr);
        });

        // Hitbox Position (Bottom center of the 3x3)
        this.hitbox = baseLocation.getWorld().spawn(baseLocation.clone(), Interaction.class, inter -> {
            inter.setInteractionWidth(3.0f);
            inter.setInteractionHeight(11.0f);
            inter.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "warhead_id"),
                    PersistentDataType.STRING,
                    warhead.getUniqueId().toString()
            );
        });

        // Register this instance so the Redstone Listener can find it
        ACTIVE_WARHEADS.put(baseLocation, this);
    }

    public void detonate() {
        if (isTriggered) return;
        isTriggered = true;

        ACTIVE_WARHEADS.remove(baseLocation);

        Location center = warhead.getLocation();
        warhead.remove();
        hitbox.remove();

        // --- Configuration ---
        final int radius = 100;
        final double effectMultiplier = 1.5; // 1.5x range for status effects
        final double effectRadius = radius * effectMultiplier;

        // --- Player Damage & Effects Logic ---
        // We scan the larger effectRadius to catch everyone in the "fallout" zone
        center.getWorld().getNearbyEntities(center, effectRadius, effectRadius, effectRadius).forEach(entity -> {
            if (entity instanceof org.bukkit.entity.Player) {
                org.bukkit.entity.Player player = (org.bukkit.entity.Player) entity;

                // SAFETY: Ignore Creative and Spectator players
                if (player.getGameMode() != org.bukkit.GameMode.CREATIVE &&
                        player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {

                    double dist = player.getLocation().distance(center);

                    // 1. Broad Range Effects (Blindness & Poison)
                    if (dist <= effectRadius) {
                        // Applied to everyone within 150 blocks (if radius is 100)
                        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 240, 1));
                        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.POISON, 400, 2));
                    }

                    // 2. Lethal Range Logic (Block-breaking zone)
                    if (dist <= radius) {
                        // Instant kill if in the core 70% of the blast
                        if (dist < radius * 0.7) {
                            player.setHealth(0);
                        } else {
                            // Scaled damage for the outer edges of the 100-block radius
                            double damageAmount = 20.0 * (1.0 - (dist / radius));
                            player.damage(damageAmount);
                        }
                    }
                }
            }
        });

        // --- Block Clearing Loop ---
        new BukkitRunnable() {
            int currentY = -radius;
            final double roughnessScale = 0.4;
            final double maxRoughness = 4.0;

            @Override
            public void run() {
                int ySquared = currentY * currentY;
                if (ySquared > radius * radius) {
                    currentY++;
                    return;
                }

                for (int x = -radius; x <= radius; x++) {
                    int xSquared = x * x;
                    for (int z = -radius; z <= radius; z++) {
                        int zSquared = z * z;
                        double distance = Math.sqrt(xSquared + ySquared + zSquared);

                        if (distance <= radius + maxRoughness) {
                            double edgeProximity = distance / radius;
                            double currentRoughness = Math.pow(edgeProximity, 2) * maxRoughness;
                            double jitter = Math.sin(x * roughnessScale) * Math.cos(z * roughnessScale) * currentRoughness;
                            double effectiveRadius = radius + jitter;

                            if (distance <= effectiveRadius) {
                                double breakChance = 1.0 - (distance / effectiveRadius);

                                if (distance < radius * 0.7 || java.util.concurrent.ThreadLocalRandom.current().nextDouble() < (breakChance * 2)) {
                                    Location target = center.clone().add(x, currentY, z);
                                    if (target.getBlock().getType() != org.bukkit.Material.AIR) {
                                        // Set to false to disable block physics and prevent server lag/crashes
                                        target.getBlock().setType(org.bukkit.Material.AIR, false);
                                    }
                                }
                            }
                        }
                    }
                }

                currentY++;
                if (currentY > radius) this.cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public Interaction getHitbox() {
        return hitbox;
    }

    public Location getBaseLocation() {
        return baseLocation;
    }
}