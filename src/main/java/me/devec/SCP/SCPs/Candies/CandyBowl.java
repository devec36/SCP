package me.devec.SCP.SCPs.Candies;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;

public class CandyBowl implements Listener {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    private static final NamespacedKey TARGET_ITEM_MODEL = NamespacedKey.fromString("candy");
    private static final String ITEM_NAME = "SCP-330 (Take Only Two)";

    private static final NamespacedKey DISP_HAS_HITBOX = new NamespacedKey("scp", "has_interaction");
    private static final NamespacedKey HITBOX_OWNER_UUID = new NamespacedKey("scp", "interaction_owner");

    private static final NamespacedKey CANDY_TAKEN_COUNT = new NamespacedKey("scp", "candy_taken");

    private static final String[] CANDY_TYPES = {
            "bluecandy", "blackcandy", "greencandy", "redcandy", "pinkcandy", "yellowcandy"
    };

    // Correctly tracking via the actual World UUID method (getUID())
    private final Map<UUID, Long> lastWorldTimes = new HashMap<>();

    public CandyBowl(JavaPlugin plugin) {
        this.plugin = plugin;
        startGlobalHitboxTicker();
        startMorningResetTicker();
    }

    private void startGlobalHitboxTicker() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                    forceHitboxInjection(display);
                }
            }
        }, 20L, 20L);
    }

    /**
     * Ticks EVERY SINGLE TICK (1L) to catch the exact moment time changes.
     * Detects when a night is skipped (time jumps backward to 0) or manually reset.
     */
    private void startMorningResetTicker() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                long currentTime = world.getTime();
                Long lastTime = lastWorldTimes.get(world.getUID());

                if (lastTime != null) {
                    // If time jumped backward OR hits exactly 0
                    if (currentTime == 0 || currentTime < lastTime) {

                        // Execute during the morning window (0 to 100 ticks)
                        if (currentTime >= 0 && currentTime < 100) {
                            for (Player player : world.getPlayers()) {
                                PersistentDataContainer pdc = player.getPersistentDataContainer();
                                int count = pdc.getOrDefault(CANDY_TAKEN_COUNT, PersistentDataType.INTEGER, 0);

                                if (count > 0) {
                                    pdc.set(CANDY_TAKEN_COUNT, PersistentDataType.INTEGER, 0);
                                    player.sendMessage(Component.text("A brand new morning has arrived! Your candy limits have reset.").color(NamedTextColor.GREEN));
                                    player.sendMessage(Component.text("You can take 2 more candies from the bowl.").color(NamedTextColor.GREEN));
                                }
                            }
                        }
                    }
                }
                lastWorldTimes.put(world.getUID(), currentTime);
            }
        }, 1L, 1L);
    }

    public static void forceHitboxInjection(ItemDisplay itemDisplay) {
        if (itemDisplay == null || !itemDisplay.isValid()) return;

        ItemStack item = itemDisplay.getItemStack();
        if (item == null || !item.hasItemMeta()) return;

        NamespacedKey model = item.getItemMeta().getItemModel();
        if (model == null || !model.equals(TARGET_ITEM_MODEL)) return;

        if (itemDisplay.getPersistentDataContainer().has(DISP_HAS_HITBOX, PersistentDataType.BYTE)) {
            boolean hitboxExists = false;
            for (Entity nearby : itemDisplay.getNearbyEntities(1.0, 1.5, 1.0)) {
                if (nearby instanceof Interaction interaction) {
                    String owner = interaction.getPersistentDataContainer().get(HITBOX_OWNER_UUID, PersistentDataType.STRING);
                    if (itemDisplay.getUniqueId().toString().equals(owner)) {
                        hitboxExists = true;
                        break;
                    }
                }
            }
            if (hitboxExists) return;
        }

        itemDisplay.getPersistentDataContainer().set(DISP_HAS_HITBOX, PersistentDataType.BYTE, (byte) 1);

        Location loc = itemDisplay.getLocation().clone().subtract(0.0, 0.5, 0.0);
        loc.getWorld().spawn(loc, Interaction.class, spawned -> {
            spawned.setInteractionWidth(0.8f);
            spawned.setInteractionHeight(0.6f);
            spawned.setPersistent(true);
            spawned.getPersistentDataContainer().set(HITBOX_OWNER_UUID, PersistentDataType.STRING, itemDisplay.getUniqueId().toString());
        });
    }

    @EventHandler
    public void onAttackBowl(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof Interaction interaction)) return;

        String ownerUUIDStr = interaction.getPersistentDataContainer().get(HITBOX_OWNER_UUID, PersistentDataType.STRING);
        if (ownerUUIDStr == null) return;

        event.setCancelled(true);
        processBowlPickup(player, interaction, ownerUUIDStr);
    }

    @EventHandler
    public void onHitBowl(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;

        String ownerUUIDStr = interaction.getPersistentDataContainer().get(HITBOX_OWNER_UUID, PersistentDataType.STRING);
        if (ownerUUIDStr == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        handleCandyDispense(player, interaction.getLocation());
    }

    private void handleCandyDispense(Player player, Location dropLoc) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            ItemStack randomCandy = getRandomCandyItem();
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(randomCandy);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(dropLoc, drop);
            }
            player.sendMessage(Component.text("You take a piece of candy from the bowl... (Creative Bypass)").color(NamedTextColor.YELLOW));
            return;
        }

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        int count = pdc.getOrDefault(CANDY_TAKEN_COUNT, PersistentDataType.INTEGER, 0);

        count++;
        pdc.set(CANDY_TAKEN_COUNT, PersistentDataType.INTEGER, count);

        if (count <= 2) {
            ItemStack randomCandy = getRandomCandyItem();
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(randomCandy);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(dropLoc, drop);
            }
            player.sendMessage(Component.text("You take a piece of candy from the bowl...").color(NamedTextColor.YELLOW));

            int remainingCandies = 2 - count;
            if (remainingCandies > 0) {
                player.sendMessage(Component.text("You can take " + remainingCandies + " more candies from the bowl.").color(NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("You can take 0 more candies from the bowl. Do not be greedy.").color(NamedTextColor.RED));
            }
        } else {
            executeSCP330Punishment(player);
        }
    }

    private void executeSCP330Punishment(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        player.sendMessage(Component.text("Your hands melt away as punishment for your greed!").color(NamedTextColor.RED));

        player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, PotionEffect.INFINITE_DURATION, 4, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, PotionEffect.INFINITE_DURATION, 4, false, false, false));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                    player.getPersistentDataContainer().set(CANDY_TAKEN_COUNT, PersistentDataType.INTEGER, 0);
                    this.cancel();
                    return;
                }

                ItemStack mainHand = player.getInventory().getItemInMainHand();
                if (mainHand.getType() != Material.AIR) {
                    player.getWorld().dropItemNaturally(player.getLocation(), mainHand.clone());
                    player.getInventory().setItemInMainHand(null);
                }

                ItemStack offHand = player.getInventory().getItemInOffHand();
                if (offHand.getType() != Material.AIR) {
                    player.getWorld().dropItemNaturally(player.getLocation(), offHand.clone());
                    player.getInventory().setItemInOffHand(null);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private ItemStack getRandomCandyItem() {
        String modelName = CANDY_TYPES[random.nextInt(CANDY_TYPES.length)];
        String readableName = modelName.substring(0, 1).toUpperCase() +
                modelName.substring(1, modelName.length() - 5) + " Candy";

        ItemStack candy = new ItemStack(Material.PAPER);
        ItemMeta meta = candy.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(readableName).color(NamedTextColor.AQUA));
            meta.setItemModel(NamespacedKey.fromString(modelName));
            candy.setItemMeta(meta);
        }

        Consumable consumableComponent = Consumable.consumable()
                .consumeSeconds(1.6f)
                .animation(ItemUseAnimation.EAT)
                .hasConsumeParticles(true)
                .build();

        FoodProperties foodComponent = FoodProperties.food()
                .nutrition(1)
                .saturation(0.1f)
                .canAlwaysEat(true)
                .build();

        candy.setData(DataComponentTypes.CONSUMABLE, consumableComponent);
        candy.setData(DataComponentTypes.FOOD, foodComponent);

        return candy;
    }

    private void processBowlPickup(Player player, Interaction interaction, String ownerUUIDStr) {
        try {
            UUID ownerUUID = UUID.fromString(ownerUUIDStr);
            Entity targetDisplay = Bukkit.getEntity(ownerUUID);

            if (targetDisplay != null) {
                targetDisplay.remove();
            }
            interaction.remove();

            if (targetDisplay != null) {
                ItemStack candyItem = createCandyItem();
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(candyItem);
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(interaction.getLocation(), drop);
                }
            }

        } catch (IllegalArgumentException e) {
            interaction.remove();
        }
    }

    @EventHandler
    public void onPlaceBowl(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        NamespacedKey model = item.getItemMeta().getItemModel();
        if (model == null || !model.equals(TARGET_ITEM_MODEL)) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        Location placeLoc = clickedBlock.getRelative(event.getBlockFace()).getLocation().add(0.5, 0.5, 0.5);

        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }

        ItemDisplay itemDisplay = placeLoc.getWorld().spawn(placeLoc, ItemDisplay.class, display -> {
            ItemStack singleBowl = createCandyItem();
            singleBowl.setAmount(1);
            display.setItemStack(singleBowl);
            display.setPersistent(true);
        });

        forceHitboxInjection(itemDisplay);
    }

    private static ItemStack createCandyItem() {
        ItemStack item = new ItemStack(Material.WARPED_FUNGUS_ON_A_STICK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(ITEM_NAME).color(NamedTextColor.GOLD));
            meta.setItemModel(TARGET_ITEM_MODEL);
            meta.lore(List.of(
                    Component.text("''Sugar and spice, a harmless delight.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true),
                    Component.text("Take only two and all is right.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true),
                    Component.text("Take a third despite the sign,", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true),
                    Component.text("and lose what was once surely thine''", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true)
            ));
            item.setItemMeta(meta);
            
        }
        return item;
    }

    // Add this inside your CandyBowl class
    public ItemStack createCandyBowlItem() {
        return createCandyItem();
    }
}