package me.devec.SCP.Alpha_Warhead;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class AlphaWarhead {

    public static final Map<Location, AlphaWarhead> ACTIVE_WARHEADS = new ConcurrentHashMap<>();

    private final ItemDisplay warhead;
    private final Interaction hitbox;
    private final Plugin plugin;
    private final Location baseLocation;
    private boolean isTriggered = false;

    public AlphaWarhead(Location location, Plugin plugin) {
        this.plugin = plugin;
        this.baseLocation = location.getBlock().getLocation();

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

        this.hitbox = baseLocation.getWorld().spawn(baseLocation.clone(), Interaction.class, inter -> {
            inter.setInteractionWidth(3.0f);
            inter.setInteractionHeight(11.0f);
            inter.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "warhead_id"),
                    PersistentDataType.STRING,
                    warhead.getUniqueId().toString()
            );
        });

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
        final double effectMultiplier = 1.5;
        final double effectRadius = radius * effectMultiplier;

        // --- Entity Damage & Effects ---
        center.getWorld().getNearbyEntities(
                center,
                effectRadius,
                effectRadius,
                effectRadius
        ).forEach(entity -> {

            if (!(entity instanceof LivingEntity living))
                return;

            if (living instanceof Player player) {
                if (player.getGameMode() == GameMode.CREATIVE ||
                        player.getGameMode() == GameMode.SPECTATOR) {
                    return;
                }
            }

            // --- SHIELDING / LINE OF SIGHT CHECK ---
            if (isShieldedFromExplosion(center, living)) {
                return; // Skip damage and potion effects completely!
            }

            double distSquared = living.getLocation().distanceSquared(center);
            double radiusSquared = radius * radius;
            double effectRadiusSquared = effectRadius * effectRadius;
            double lethalRadius = radius * 0.7;
            double lethalRadiusSquared = lethalRadius * lethalRadius;

            // Fallout effects
            if (distSquared <= effectRadiusSquared) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 240, 1));
                living.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 400, 2));
            }

            // Blast damage
            if (distSquared <= radiusSquared) {
                if (distSquared <= lethalRadiusSquared) {
                    living.damage(100000.0);
                } else {
                    double dist = Math.sqrt(distSquared);
                    double damageAmount = Math.max(2.0, 60.0 * (1.0 - (dist / radius)));
                    living.damage(damageAmount);
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

                                if (distance < radius * 0.7 || ThreadLocalRandom.current().nextDouble() < (breakChance * 2)) {
                                    Location target = center.clone().add(x, currentY, z);
                                    Block block = target.getBlock();
                                    Material type = block.getType();

                                    if (type != Material.AIR && type.getHardness() >= 0.0f) {
                                        if (type != Material.OBSIDIAN &&
                                                type != Material.CRYING_OBSIDIAN &&
                                                type != Material.REINFORCED_DEEPSLATE) {

                                            block.setType(Material.AIR, false);
                                        }
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

    /**
     * Raytraces from the explosion center to the entity's eye location.
     * Returns true if a blast-resistant wall blocks the line of sight.
     */
    private boolean isShieldedFromExplosion(Location center, LivingEntity entity) {
        Location targetLoc = entity.getEyeLocation();
        Vector direction = targetLoc.toVector().subtract(center.toVector());
        double distance = direction.length();

        if (distance <= 0) return false; // Directly on the nuke

        direction.normalize();

        // Raytrace only against blocks, checking up to the distance of the entity
        RayTraceResult result = center.getWorld().rayTraceBlocks(
                center,
                direction,
                distance
        );

        if (result != null && result.getHitBlock() != null) {
            Material hitType = result.getHitBlock().getType();

            // If the ray hits an unbreakable block, obsidian, or reinforced deepslate, the player is safe
            if (hitType.getHardness() < 0.0f ||
                    hitType == Material.OBSIDIAN ||
                    hitType == Material.CRYING_OBSIDIAN ||
                    hitType == Material.REINFORCED_DEEPSLATE) {
                return true;
            }
        }

        return false;
    }

    public Interaction getHitbox() { return hitbox; }
    public Location getBaseLocation() { return baseLocation; }
}