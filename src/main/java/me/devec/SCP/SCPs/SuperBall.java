package me.devec.SCP.SCPs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class SuperBall implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey lastDamagedEntityKey;
    private final NamespacedKey explosionTimeKey;

    private static final double MAX_SPEED_CAP = 15.0;
    private static final double DAMAGE_MULTIPLIER = 3.0;
    private static final float EXPLOSION_POWER = 6.0f;

    private static final double INITIAL_SPEED = 1.8;
    private static final double SPEED_MULTIPLIER = 1.25;
    private static final long LIFETIME_MILLIS = 20 * 1000L;
    private static final double RANDOM_SPREAD_ANGLE = 0.12;
    private static final double BALL_RAYTRACE_RADIUS = 0.15;

    public SuperBall(JavaPlugin plugin) {
        this.plugin = plugin;
        this.lastDamagedEntityKey = new NamespacedKey(plugin, "superball_last_damaged");
        this.explosionTimeKey = new NamespacedKey(plugin, "superball_explosion_time");
    }

    public static ItemStack createItem() {
        ItemStack ball = new ItemStack(Material.SNOWBALL);
        ItemMeta meta = ball.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.text("SCP-018 (Super Ball)")
                    .color(net.kyori.adventure.text.format.NamedTextColor.RED));
            meta.setItemModel(NamespacedKey.minecraft("superball"));
            meta.lore(List.of(
                    Component.text("An anomalous ball, capable of doubling ", NamedTextColor.GRAY),
                    Component.text("its speed every single bounce",NamedTextColor.GRAY)
            ));
            ball.setItemMeta(meta);
        }
        return ball;
    }

    @EventHandler
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball) || !(snowball.getShooter() instanceof Player player)) return;

        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem.getType() != Material.SNOWBALL) handItem = player.getInventory().getItemInOffHand();

        if (handItem.hasItemMeta() && handItem.getItemMeta().hasItemModel()) {
            NamespacedKey model = handItem.getItemMeta().getItemModel();
            if (model != null && model.getKey().equals("superball")) {
                snowball.setItem(handItem.clone());

                snowball.setVelocity(snowball.getVelocity().normalize().multiply(INITIAL_SPEED));
                long absoluteExplosionTime = System.currentTimeMillis() + LIFETIME_MILLIS;
                snowball.getPersistentDataContainer().set(explosionTimeKey, PersistentDataType.LONG, absoluteExplosionTime);

                startParticleTrailTask(snowball);
                startCollisionMonitorTask(snowball);
                startLifetimeExplosionTask(snowball);
            }
        }
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball oldSnowball)) return;

        PersistentDataContainer pdc = oldSnowball.getPersistentDataContainer();
        if (!pdc.has(explosionTimeKey, PersistentDataType.LONG)) return;

        long expireTime = pdc.get(explosionTimeKey, PersistentDataType.LONG);

        // FIX: Broaden the trigger window. If it's expired, explode immediately, cancel the hit processing entirely.
        if (System.currentTimeMillis() >= expireTime) {
            oldSnowball.getWorld().createExplosion(oldSnowball.getLocation(), EXPLOSION_POWER, true, true);
            oldSnowball.remove();
            event.setCancelled(true);
            return;
        }

        Vector velocity = oldSnowball.getVelocity();
        double currentSpeed = velocity.length();

        if (event.getHitEntity() instanceof LivingEntity victim) {
            String lastDamagedUUIDString = pdc.get(lastDamagedEntityKey, PersistentDataType.STRING);

            if (lastDamagedUUIDString == null || !lastDamagedUUIDString.equals(victim.getUniqueId().toString())) {
                double calculatedDamage = currentSpeed * DAMAGE_MULTIPLIER;
                victim.damage(calculatedDamage, oldSnowball.getShooter() instanceof LivingEntity shooter ? shooter : null);
                pdc.set(lastDamagedEntityKey, PersistentDataType.STRING, victim.getUniqueId().toString());
            }

            event.setCancelled(true);
            Location passThroughLoc = oldSnowball.getLocation().add(velocity.clone().normalize().multiply(0.2));
            oldSnowball.teleport(passThroughLoc);
            oldSnowball.setVelocity(velocity);
            return;
        }

        if (event.getHitBlock() == null || event.getHitBlockFace() == null) return;

        BlockFace face = event.getHitBlockFace();
        Vector blockFaceNormal = face.getDirection();
        double dotProduct = velocity.dot(blockFaceNormal);

        if (dotProduct >= 0) return;

        event.setCancelled(true);
        executeBounce(oldSnowball, velocity, currentSpeed, blockFaceNormal, dotProduct, expireTime, oldSnowball.getLocation());
    }

    private void startLifetimeExplosionTask(Snowball snowball) {
        new BukkitRunnable() {
            @Override
            public void run() {
                // FIX: If the ball dies because it bounced, this runner terminates safely,
                // but the NEXT ball's task will instantly pick up the time check.
                if (!snowball.isValid() || snowball.isDead()) {
                    this.cancel();
                    return;
                }

                PersistentDataContainer pdc = snowball.getPersistentDataContainer();
                long expireTime = pdc.getOrDefault(explosionTimeKey, PersistentDataType.LONG, 0L);
                if (System.currentTimeMillis() >= expireTime) {
                    snowball.getWorld().createExplosion(snowball.getLocation(), EXPLOSION_POWER, true, true);
                    snowball.remove();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void startCollisionMonitorTask(Snowball snowball) {
        new BukkitRunnable() {
            private Location lastSafeLocation = snowball.getLocation();

            @Override
            public void run() {
                if (!snowball.isValid() || snowball.isDead()) {
                    this.cancel();
                    return;
                }

                PersistentDataContainer pdc = snowball.getPersistentDataContainer();
                long expireTime = pdc.getOrDefault(explosionTimeKey, PersistentDataType.LONG, 0L);

                // FIX: Check expiration inside the monitor task BEFORE calculating custom bounce physics
                if (System.currentTimeMillis() >= expireTime) {
                    snowball.getWorld().createExplosion(snowball.getLocation(), EXPLOSION_POWER, true, true);
                    snowball.remove();
                    this.cancel();
                    return;
                }

                Vector velocity = snowball.getVelocity();
                World world = snowball.getWorld();
                Location currentLoc = snowball.getLocation();

                if (currentLoc.getBlock().getType().isSolid()) {
                    BlockFace fallbackFace = BlockFace.UP;
                    if (!currentLoc.getBlock().getRelative(BlockFace.UP).getType().isSolid()) fallbackFace = BlockFace.UP;
                    else if (!currentLoc.getBlock().getRelative(BlockFace.DOWN).getType().isSolid()) fallbackFace = BlockFace.DOWN;
                    else {
                        double absX = Math.abs(velocity.getX());
                        double absY = Math.abs(velocity.getY());
                        double absZ = Math.abs(velocity.getZ());
                        if (absX > absY && absX > absZ) fallbackFace = velocity.getX() > 0 ? BlockFace.WEST : BlockFace.EAST;
                        else if (absZ > absX && absZ > absY) fallbackFace = velocity.getZ() > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
                        else fallbackFace = velocity.getY() > 0 ? BlockFace.DOWN : BlockFace.UP;
                    }
                    executeBounce(snowball, velocity, velocity.length(), fallbackFace.getDirection(), -1.0, expireTime, lastSafeLocation);
                    this.cancel();
                    return;
                }

                lastSafeLocation = currentLoc.clone();

                double distanceRemaining = velocity.length();
                Location rayOrigin = currentLoc.clone();
                Vector rayDir = velocity.clone().normalize();

                int safetyBrake = 0;

                while (distanceRemaining > 0.01 && safetyBrake < 5) {
                    RayTraceResult hit = world.rayTraceBlocks(
                            rayOrigin,
                            rayDir,
                            distanceRemaining + BALL_RAYTRACE_RADIUS,
                            FluidCollisionMode.NEVER,
                            true
                    );

                    if (hit != null && hit.getHitBlock() != null && hit.getHitBlockFace() != null) {
                        Vector blockNormal = hit.getHitBlockFace().getDirection();
                        double dot = velocity.dot(blockNormal);

                        if (dot < 0) {
                            Location preciseHitLoc = hit.getHitPosition().toLocation(world);
                            executeBounce(snowball, velocity, velocity.length(), blockNormal, dot, expireTime, preciseHitLoc);
                            this.cancel();
                            return;
                        }
                    }

                    distanceRemaining -= 0.5;
                    rayOrigin.add(rayDir.clone().multiply(0.5));
                    safetyBrake++;
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void executeBounce(Snowball oldSnowball, Vector velocity, double currentSpeed,
                               Vector blockFaceNormal, double dotProduct, long expireTime, Location referenceLoc) {
        World world = oldSnowball.getWorld();

        // FIX: Final sanity check before spawning a new entity. If it expired right during calculations, explode immediately.
        if (System.currentTimeMillis() >= expireTime) {
            world.createExplosion(referenceLoc, EXPLOSION_POWER, true, true);
            oldSnowball.remove();
            return;
        }

        float dynamicPitch = 0.6f + (float) (Math.min(currentSpeed, MAX_SPEED_CAP) / MAX_SPEED_CAP) * 1.2f;
        world.playSound(referenceLoc, Sound.ENTITY_ITEM_PICKUP, 1.5f, dynamicPitch);
        world.playSound(referenceLoc, Sound.BLOCK_BAMBOO_WOOD_BREAK, 1.0f, dynamicPitch - 0.2f);

        Vector bounceVelocity = velocity.clone()
                .subtract(blockFaceNormal.clone().multiply(2 * dotProduct))
                .multiply(SPEED_MULTIPLIER);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vector deviation = new Vector(
                random.nextDouble(-RANDOM_SPREAD_ANGLE, RANDOM_SPREAD_ANGLE),
                random.nextDouble(-RANDOM_SPREAD_ANGLE, RANDOM_SPREAD_ANGLE),
                random.nextDouble(-RANDOM_SPREAD_ANGLE, RANDOM_SPREAD_ANGLE)
        );

        double speedBeforeDeviation = bounceVelocity.length();
        bounceVelocity.add(deviation).normalize().multiply(speedBeforeDeviation);

        if (bounceVelocity.lengthSquared() > (MAX_SPEED_CAP * MAX_SPEED_CAP)) {
            bounceVelocity.normalize().multiply(MAX_SPEED_CAP);
        }

        Location spawnLoc = referenceLoc.clone().add(blockFaceNormal.clone().multiply(0.15));
        spawnLoc.setDirection(bounceVelocity);

        ItemStack flyingItem = oldSnowball.getItem();
        String finalLastDamaged = oldSnowball.getPersistentDataContainer().get(lastDamagedEntityKey, PersistentDataType.STRING);

        world.spawn(spawnLoc, Snowball.class, newSnowball -> {
            newSnowball.setShooter(oldSnowball.getShooter());
            newSnowball.setVelocity(bounceVelocity);
            newSnowball.setItem(flyingItem);

            PersistentDataContainer newPdc = newSnowball.getPersistentDataContainer();
            newPdc.set(explosionTimeKey, PersistentDataType.LONG, expireTime);

            if (finalLastDamaged != null) {
                newPdc.set(lastDamagedEntityKey, PersistentDataType.STRING, finalLastDamaged);
            }

            startParticleTrailTask(newSnowball);
            startCollisionMonitorTask(newSnowball);
            startLifetimeExplosionTask(newSnowball);
        });

        oldSnowball.remove();
    }

    private void startParticleTrailTask(Snowball snowball) {
        new BukkitRunnable() {
            private final org.bukkit.Particle.DustOptions redDust =
                    new org.bukkit.Particle.DustOptions(Color.fromRGB(255, 0, 0), 1.5f);

            @Override
            public void run() {
                if (!snowball.isValid() || snowball.isDead()) {
                    this.cancel();
                    return;
                }

                Location currentLoc = snowball.getLocation();
                Vector velocity = snowball.getVelocity();
                double currentSpeed = velocity.length();

                int points = Math.max(3, (int) (currentSpeed * 1.5));
                World world = snowball.getWorld();
                Vector directionStep = velocity.clone().multiply(-1).multiply(1.0 / points);

                for (int i = 0; i < points; i++) {
                    Location point = currentLoc.clone().add(directionStep.clone().multiply(i));
                    world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, redDust);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}