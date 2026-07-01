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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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

import java.util.List;

public class Cola implements Listener {

    private final JavaPlugin plugin;

    private static NamespacedKey scpKey;
    private static NamespacedKey scpTimerKey;
    private static NamespacedKey speedModifierKey;
    private static NamespacedKey attackModifierKey;
    private static NamespacedKey miningModifierKey;
    private static NamespacedKey swimModifierKey;

    private static final long COLA_TOTAL_DURATION_MS = 600000L;

    public Cola(JavaPlugin plugin) {
        this.plugin = plugin;
        scpKey = new NamespacedKey(plugin, "scp_207_active");
        scpTimerKey = new NamespacedKey(plugin, "scp_207_timer");
        speedModifierKey = new NamespacedKey(plugin, "scp_207_speed");
        attackModifierKey = new NamespacedKey(plugin, "scp_207_attack");
        miningModifierKey = new NamespacedKey(plugin, "scp_207_mining");
        swimModifierKey = new NamespacedKey(plugin, "scp_207_swim");

        startColaGlobalTask();
    }

    public static ItemStack createColaItem() {
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("SCP-207 (Cola)", NamedTextColor.RED, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));

            meta.setColor(Color.fromRGB(139, 0, 0));
            meta.setItemModel(NamespacedKey.fromString("cola"));

            meta.lore(List.of(
                    Component.text("+ Progressively speeds up the player", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                    Component.text("+ Progressively adds higher attack and mining speed", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                    Component.text("- Progressively drains health over time", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                    Component.text("- Cannot sleep during the effect", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                    Component.text("Lasts 10 minutes before the user dies.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true)
            ));

            potion.setItemMeta(meta);

            hidePotionAttributes(potion);
        }
        return potion;
    }

    public static void removeAllColaEffects(Player player) {
        if (scpKey == null) return;

        player.getPersistentDataContainer().remove(scpKey);
        player.getPersistentDataContainer().remove(scpTimerKey);

        AttributeInstance speedAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.removeModifier(speedModifierKey);

        AttributeInstance attackAttr = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackAttr != null) attackAttr.removeModifier(attackModifierKey);

        AttributeInstance miningAttr = player.getAttribute(Attribute.MINING_EFFICIENCY);
        if (miningAttr != null) miningAttr.removeModifier(miningModifierKey);

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
                NamespacedKey expectedKey = NamespacedKey.fromString("cola");

                if (modelKey != null && modelKey.equals(expectedKey)) {
                    Player player = event.getPlayer();
                    boolean isSurvivalOrAdventure = player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;

                    if (player.getPersistentDataContainer().has(scpKey, PersistentDataType.BOOLEAN)) {
                        if (isSurvivalOrAdventure) {
                            player.sendMessage(Component.text("The compound violently reacts with your blood!", NamedTextColor.RED));
                            triggerRedMistExplosion(player);
                            player.damage(100.0);
                            return;
                        } else {
                            player.sendMessage(Component.text("The anomaly sparkles harmlessly in your hands...", NamedTextColor.GRAY));
                        }
                    }

                    // Explode instantly if Performance Enhancer is already active
                    NamespacedKey enhancerKey = new NamespacedKey(plugin, "scp_1853_active");
                    if (player.getPersistentDataContainer().has(enhancerKey, PersistentDataType.BOOLEAN)) {
                        if (isSurvivalOrAdventure) {
                            player.sendMessage(Component.text("The structural stimulants create a violent internal detonation!", NamedTextColor.DARK_RED, TextDecoration.BOLD));

                            triggerActualExplosion(player); // Real mechanical explosion
                            PreformanceEnchancer.removeAllPerformanceEffects(player);
                            removeAllColaEffects(player);

                            player.damage(100.0);
                            return;
                        }
                    }

                    applyColaEffects(player);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {}

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        // Clean up cola effects
        boolean hadCola = player.getPersistentDataContainer().has(scpKey, PersistentDataType.BOOLEAN);
        if (hadCola) {
            removeAllColaEffects(player);
            player.sendMessage("All cola effects have been removed.");
        }
    }

    private void applyColaEffects(Player player) {
        player.getPersistentDataContainer().set(scpKey, PersistentDataType.BOOLEAN, true);
        player.getPersistentDataContainer().set(scpTimerKey, PersistentDataType.LONG, COLA_TOTAL_DURATION_MS);

        player.sendMessage(Component.text("An unnatural surge of adrenaline courses through your veins...", NamedTextColor.DARK_RED));
    }

    private void startColaGlobalTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.getPersistentDataContainer().has(scpKey, PersistentDataType.BOOLEAN)) continue;

                if (player.isDead()) {
                    removeAllColaEffects(player);
                    continue;
                }

                long timeLeftMs = player.getPersistentDataContainer().getOrDefault(scpTimerKey, PersistentDataType.LONG, 0L);
                timeLeftMs -= 1000L;

                int secondsElapsed = (int) ((COLA_TOTAL_DURATION_MS - timeLeftMs) / 1000L);
                int minutesElapsed = secondsElapsed / 60;

                sendPhaseWarnings(player, secondsElapsed);

                if (secondsElapsed % 2 == 0) {
                    spawnBloodParticles(player.getLocation().add(0, 1, 0), 5, 0.2);
                }

                boolean isImmune = player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;

                if (timeLeftMs <= 0) {
                    removeAllColaEffects(player);
                    if (!isImmune) {
                        player.sendMessage(Component.text("Your heart completely ruptures from sheer exhaustion.", NamedTextColor.DARK_RED, TextDecoration.BOLD));
                        triggerRedMistExplosion(player);
                        player.damage(1000.0);
                    } else {
                        player.sendMessage(Component.text("The wild rush of the Cola completely settles.", NamedTextColor.GRAY));
                    }
                    continue;
                } else {
                    player.getPersistentDataContainer().set(scpTimerKey, PersistentDataType.LONG, timeLeftMs);
                }

                if (player.getFoodLevel() < 8) {
                    player.setFoodLevel(8);
                }

                double speedBonus = minutesElapsed * 0.05;
                double miningBonus = minutesElapsed * 4.0;
                double attackBonus = minutesElapsed * 1.5;
                double waterBonus = minutesElapsed * 0.10;

                updateAttribute(player, Attribute.MOVEMENT_SPEED, speedModifierKey, speedBonus);
                updateAttribute(player, Attribute.MINING_EFFICIENCY, miningModifierKey, miningBonus);
                updateAttribute(player, Attribute.ATTACK_SPEED, attackModifierKey, attackBonus);
                updateAttribute(player, Attribute.WATER_MOVEMENT_EFFICIENCY, swimModifierKey, waterBonus);

                if (!isImmune) {
                    double baseDamagePerSecond = 0.05 + (minutesElapsed * 0.05);
                    double currentHealth = player.getHealth();
                    double newHealth = Math.max(0.0, currentHealth - baseDamagePerSecond);

                    if (newHealth <= 0.0) {
                        removeAllColaEffects(player);
                        triggerRedMistExplosion(player);
                        player.damage(100.0);
                        continue;
                    } else {
                        player.setHealth(newHealth);

                        if (currentHealth <= 9.0) {
                            player.playHurtAnimation(0.0f);
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
                        }
                    }
                }

                if (player.isSleeping() && !isImmune) {
                    player.wakeup(true);
                    player.sendMessage(Component.text("Your mind is racing far too fast to rest...", NamedTextColor.RED));
                }
            }
        }, 20L, 20L);
    }

    private void updateAttribute(Player player, Attribute attribute, NamespacedKey key, double bonusValue) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;

        instance.removeModifier(key);
        if (bonusValue > 0) {
            instance.addModifier(new AttributeModifier(key, bonusValue, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
    }

    // Handles mixing SCP-207 and SCP-1853 (Actual real explosion flash/sound/knockback, no map griefing)
    private void triggerActualExplosion(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);
        loc.getWorld().createExplosion(loc, 6.0f, false, true);
    }

    // Handles pure Cola deaths and overdoses (Purely blood and subtle blast particles)
    private void triggerRedMistExplosion(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1, 0.0, 0.0, 0.0, 0.0);
        spawnBloodParticles(loc, 120, 0.6);
    }

    private void spawnBloodParticles(Location loc, int count, double spread) {
        Particle.DustOptions bloodDust = new Particle.DustOptions(Color.fromRGB(139, 0, 0), 1.5F);
        loc.getWorld().spawnParticle(Particle.DUST, loc, count, spread, spread, spread, 0.0, bloodDust);
    }

    private void sendPhaseWarnings(Player player, int seconds) {
        if (seconds == 180) {
            player.sendMessage(Component.text("You feel a sharp pain in all your muscles. You are going to die in 7 minutes."));
        } else if (seconds == 300) {
            player.sendMessage(Component.text("You can feel your muscles starting to bleed. You are going to die in 5 minutes.", NamedTextColor.RED));
        } else if (seconds == 480) {
            player.sendMessage(Component.text("Your brain feels as if it is rotting. You are going to die in 2 minutes.", NamedTextColor.GOLD, TextDecoration.ITALIC));
        } else if (seconds == 540) {
            player.sendMessage(Component.text("As your vision fades, your heart's rhythm breaks. You are going to die in 1 minute.", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60 * 20, 1, false, false, true));
        }
    }

    public static void hidePotionAttributes(ItemStack item) {
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