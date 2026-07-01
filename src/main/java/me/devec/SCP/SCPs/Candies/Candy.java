package me.devec.SCP.SCPs.Candies;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class Candy {

    private final Plugin plugin;

    public Candy(Plugin plugin) {
        this.plugin = plugin;
    }

    // ===================== MESSAGING =====================
    private void msg(Player player, String text) {
        player.sendMessage("§8[§5SCP CANDY§8] §f" + text);
    }

    // ===================== HELPERS =====================
    private boolean isProtected(Player player) {
        GameMode gm = player.getGameMode();
        return gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR;
    }

    private boolean isProtected(LivingEntity entity) {
        if (entity instanceof Player p) return isProtected(p);
        return false;
    }

    /**
     * Returns true for blocks that should never be broken:
     * hardness -1 covers bedrock, command blocks, end portal frames,
     * barriers, structure blocks, jigsaw blocks, etc.
     */
    private boolean isUnbreakable(Block block) {
        if (block.getType().isAir()) return true;
        return block.getType().getHardness() < 0f;
    }

    /**
     * Checks one block directly in front of the entity on its path toward the hole.
     * Only clears it if solid and breakable. Uses setType(AIR) to avoid item drops
     * and physics cascades. Returns early after the first solid block found so we
     * do at most one block removal per entity per call.
     */
    private void clearNextBlockInPath(Location from, Location to, World world,
                                      Set<Long> clearedBlocks) {
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        if (distance < 1.0) return;

        direction.normalize();

        // Only scan the first 3 blocks ahead — enough to unbury the entity
        // without raycasting the entire 100-block path every tick
        for (double d = 0.5; d <= Math.min(distance, 3.0); d += 0.5) {
            Location point = from.clone().add(direction.clone().multiply(d));
            Block block = world.getBlockAt(point);

            if (block.getType().isAir()) continue;
            if (isUnbreakable(block)) break; // stop at indestructible walls

            long key = block.getX() * 1000003L ^ block.getY() * 1001L ^ block.getZ();
            if (clearedBlocks.contains(key)) continue;

            clearedBlocks.add(key);
            world.spawnParticle(Particle.BLOCK,
                    block.getLocation().add(0.5, 0.5, 0.5),
                    6, 0.3, 0.3, 0.3, block.getBlockData());
            world.playSound(block.getLocation(), Sound.BLOCK_STONE_BREAK, 0.3f, 1.4f);
            block.setType(Material.AIR);
            break; // one block per entity per call
        }
    }

    // ===================== OUTCOMES =====================
    private int getWeightedRandomOutcome() {
        Random r = new Random();
        int roll = r.nextInt(100) + 1;

        if (roll <= 30) return 1;
        if (roll <= 50) return 3;
        if (roll <= 70) return 4;
        if (roll <= 85) return 5;
        if (roll <= 94) return 2;
        if (roll <= 98) return 6;

        return 7;
    }

    // ===================== PINK CANDY =====================
    public void pinkCandy(Player player) {

        msg(player, "§dPINK CANDY BOOOOOM!!!");

        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) return;

        world.createExplosion(
                center.getX(),
                center.getY(),
                center.getZ(),
                6.5F,
                false,
                true
        );

        double radius = 10.0;

        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (isProtected(living)) continue;

            double distance = entity.getLocation().distance(center);

            if (distance <= 3.5) {
                living.setHealth(0.0);
                continue;
            }

            double damage = Math.max(0, 300 - (distance * 21));

            Vector knockback = entity.getLocation().toVector()
                    .subtract(center.toVector());

            if (knockback.lengthSquared() > 0.0001) {
                knockback.normalize();
                knockback.multiply(2.2);
                knockback.setY(1.0);
                living.setVelocity(knockback);
            }

            living.damage(damage, player);
        }

        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 2);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.8f);
    }

    // ===================== BLACK CANDY =====================
    public void blackCandy(Player player) {

        msg(player, "§7Unknown SCP reaction detected...");

        int outcome = getWeightedRandomOutcome();
        Random r = new Random();

        switch (outcome) {

            case 1 -> {
                msg(player, "§aYou got lucky.");
                int amountOfEffects = r.nextInt(3) + 2;

                List<Integer> candyChoices = new ArrayList<>(Arrays.asList(2, 3, 4, 5, 6));
                Collections.shuffle(candyChoices);

                for (int i = 0; i < amountOfEffects; i++) {
                    int choice = candyChoices.get(i);
                    if (choice == 2) yellowCandy(player);
                    if (choice == 3) blueCandy(player);
                    if (choice == 4) redCandy(player);
                    if (choice == 5) greenCandy(player);
                    if (choice == 6) purpleCandy(player);
                }
            }

            case 2 -> {
                msg(player, "§8BLACK CANDY BOOOOOM!!!");
                pinkCandy(player);
            }

            case 3 -> {
                msg(player, "§7Your body destabilizes...");
                List<WeightedPotion> pool = new ArrayList<>();

                pool.add(new WeightedPotion(PotionEffectType.SPEED, 2, 15));
                pool.add(new WeightedPotion(PotionEffectType.STRENGTH, 1, 15));
                pool.add(new WeightedPotion(PotionEffectType.JUMP_BOOST, 1, 15));
                pool.add(new WeightedPotion(PotionEffectType.HASTE, 1, 15));
                pool.add(new WeightedPotion(PotionEffectType.ABSORPTION, 2, 12));
                pool.add(new WeightedPotion(PotionEffectType.RESISTANCE, 1, 12));
                pool.add(new WeightedPotion(PotionEffectType.REGENERATION, 1, 12));

                pool.add(new WeightedPotion(PotionEffectType.SLOWNESS, 0, 5));
                pool.add(new WeightedPotion(PotionEffectType.POISON, 0, 5));
                pool.add(new WeightedPotion(PotionEffectType.INSTANT_DAMAGE, 0, 3));
                pool.add(new WeightedPotion(PotionEffectType.BLINDNESS, 0, 4));
                pool.add(new WeightedPotion(PotionEffectType.NAUSEA, 0, 4));

                Set<PotionEffectType> selectedTypes = new HashSet<>();
                int appliedCount = 0;

                while (appliedCount < 3) {
                    int totalWeight = 0;
                    for (WeightedPotion wp : pool) totalWeight += wp.weight;

                    int rolledWeight = r.nextInt(totalWeight);

                    WeightedPotion selected = null;
                    int countWeight = 0;

                    for (WeightedPotion wp : pool) {
                        countWeight += wp.weight;
                        if (rolledWeight < countWeight) {
                            selected = wp;
                            break;
                        }
                    }

                    if (selected != null && !selectedTypes.contains(selected.type)) {
                        selectedTypes.add(selected.type);

                        int duration = selected.type.equals(PotionEffectType.INSTANT_DAMAGE)
                                ? 1
                                : 20 * 60;

                        player.addPotionEffect(new PotionEffect(
                                selected.type,
                                duration,
                                selected.amplifier,
                                false,
                                false
                        ));

                        appliedCount++;
                    }
                }
            }

            case 4 -> {
                msg(player, "§bYou feel dizzy...");
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA,4*20,1));

                double angle = r.nextDouble() * 2 * Math.PI;
                double xOffset = Math.cos(angle) * 60;
                double zOffset = Math.sin(angle) * 60;

                Location loc = player.getLocation().add(xOffset, 0, zOffset);
                Block highestBlock = loc.getWorld().getHighestBlockAt(loc);

                Location safeLocation = highestBlock.getLocation().add(0.5, 1.0, 0.5);
                safeLocation.setYaw(player.getLocation().getYaw());
                safeLocation.setPitch(player.getLocation().getPitch());

                player.teleport(safeLocation);
            }

            case 5 -> {
                msg(player, "§ePosition swap initiated.");

                List<LivingEntity> validMobs = new ArrayList<>();

                for (Entity entity : player.getWorld().getEntities()) {
                    if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
                        if (living.getLocation().distance(player.getLocation()) >= 30.0) {
                            validMobs.add(living);
                        }
                    }
                }

                if (!validMobs.isEmpty()) {
                    LivingEntity targetMob = validMobs.get(r.nextInt(validMobs.size()));

                    Location playerLoc = player.getLocation();
                    Location mobLoc = targetMob.getLocation();

                    player.teleport(mobLoc);
                    targetMob.teleport(playerLoc);
                } else {
                    pinkCandy(player);
                }
            }

            case 6 -> {
                msg(player, "§4The sweet taste of malice dissolves into a suffocating, hollow numbness.");
                if (!isProtected(player)) {
                    player.setHealth(0.0);
                }
            }

            case 7 -> {
                msg(player, "§8[REDACTED]");

                executeBlackHole(player);
            }
        }
    }

    // ===================== BLACK HOLE =====================
    private void executeBlackHole(Player initiator) {

        final Location holeCenter = initiator.getLocation();
        final World world = holeCenter.getWorld();
        if (world == null) return;

        world.playSound(holeCenter, Sound.ENTITY_WITHER_SPAWN, 3f, 0.4f);

        final List<LivingEntity> suckedEntities = new ArrayList<>();

        for (Entity entity : world.getNearbyEntities(holeCenter, 100, 100, 100)) {
            if (entity instanceof LivingEntity living && !living.equals(initiator)) {
                suckedEntities.add(living);
            }
        }

        // Tracks blocks already cleared this event so we never re-scan them
        final Set<Long> clearedBlocks = new HashSet<>();

        new BukkitRunnable() {

            int timer = 0;

            @Override
            public void run() {

                if (timer == 0) {
                    world.strikeLightningEffect(holeCenter);
                    world.spawnParticle(Particle.REVERSE_PORTAL, holeCenter, 200, 2, 2, 2);
                }

                if (timer >= 200) {

                    msg(initiator, "§4Reality stabilized.");
                    initiator.sendMessage("<[DATA CORRUPTED]> What have you done?");

                    // Kill all sucked entities
                    for (LivingEntity entity : suckedEntities) {
                        if (entity.isValid() && !isProtected(entity)) {
                            entity.setHealth(0.0);
                        }
                    }

                    // Kill the initiator last, after the sequence
                    if (!isProtected(initiator)) {
                        initiator.setHealth(0.0);
                    }

                    world.playSound(holeCenter, Sound.ENTITY_WITHER_DEATH, 3f, 0.5f);
                    this.cancel();
                    return;
                }

                // Spiral particle ring
                for (int i = 0; i < 4; i++) {
                    double angle = (timer * 0.5) + (i * Math.PI / 2);
                    double radius = 2.5;

                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;

                    world.spawnParticle(Particle.SQUID_INK,
                            holeCenter.clone().add(x, 1, z),
                            5, 0.1, 0.1, 0.1);

                    world.spawnParticle(Particle.LARGE_SMOKE,
                            holeCenter,
                            10, 1, 1, 1);
                }

                // Suck entities toward the center; clear blocks every 5 ticks
                boolean doBlockClear = (timer % 5 == 0);

                for (LivingEntity entity : suckedEntities) {
                    if (!entity.isValid()) continue;

                    Location entLoc = entity.getLocation();

                    Vector direction = holeCenter.toVector().subtract(entLoc.toVector());
                    double distance = direction.length();

                    if (distance > 0.5) {
                        if (doBlockClear) {
                            clearNextBlockInPath(entLoc, holeCenter, world, clearedBlocks);
                        }
                        direction.normalize();
                        entity.setVelocity(direction.multiply(0.65));
                    }
                }

                timer++;
            }
        }.runTaskTimer(this.plugin, 0L, 1L);
    }

    // ===================== SIMPLE CANDIES =====================
    public void yellowCandy(Player player){
        msg(player, "§eYou feel energized.");
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,20*10,2,false,false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,20*10,2,false,false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,20*10,2,false,false));
    }

    public void blueCandy(Player player){
        msg(player, "§9A protective field surrounds you.");
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, PotionEffect.INFINITE_DURATION,3,false,false));
    }

    public void redCandy(Player player){
        msg(player, "§cRegeneration overload detected.");
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20*12, 9,false,false));
    }

    public void greenCandy(Player player){
        msg(player, "§aHealing surge activated.");
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20*60, 1,false,false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 0,false,false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20*60, 3,false,false));
    }

    public void purpleCandy(Player player){
        msg(player, "§5Damage resistance stabilized.");
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20*60, 3,false,false));
    }

    // ===================== DATA =====================
    private static class WeightedPotion {
        PotionEffectType type;
        int amplifier;
        int weight;

        WeightedPotion(PotionEffectType type, int amplifier, int weight) {
            this.type = type;
            this.amplifier = amplifier;
            this.weight = weight;
        }
    }
}