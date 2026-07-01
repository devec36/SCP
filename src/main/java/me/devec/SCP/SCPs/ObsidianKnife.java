package me.devec.SCP.SCPs;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Item; // Added import
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent; // Added import
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ObsidianKnife implements Listener {

    private final JavaPlugin plugin;

    private final NamespacedKey identityNameKey;
    private final NamespacedKey identityExpireKey;
    private final NamespacedKey identityValueKey;
    private final NamespacedKey identitySigKey;

    private final NamespacedKey originalValueKey;
    private final NamespacedKey originalSigKey;

    private final NamespacedKey victimNameKey;
    private final NamespacedKey victimUuidKey; // Added key to track owner UUID
    private final NamespacedKey textureValueKey;
    private final NamespacedKey textureSignatureKey;

    private final Map<UUID, Long> activeIdentity = new ConcurrentHashMap<>();

    private static final Set<ObsidianKnife> instances = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public ObsidianKnife(JavaPlugin plugin) {
        this.plugin = plugin;

        this.identityNameKey = new NamespacedKey(plugin, "identity_name");
        this.identityExpireKey = new NamespacedKey(plugin, "identity_expire");
        this.identityValueKey = new NamespacedKey(plugin, "identity_value");
        this.identitySigKey = new NamespacedKey(plugin, "identity_sig");

        this.originalValueKey = new NamespacedKey(plugin, "original_value");
        this.originalSigKey = new NamespacedKey(plugin, "original_sig");

        this.victimNameKey = new NamespacedKey(plugin, "victim_name");
        this.victimUuidKey = new NamespacedKey(plugin, "victim_uuid"); // Initialized
        this.textureValueKey = new NamespacedKey(plugin, "texture_value");
        this.textureSignatureKey = new NamespacedKey(plugin, "texture_signature");

        instances.add(this);
    }

    // =========================================================
    // DELAY-FREE STATIC SERVER CLEANUP
    // =========================================================
    public static void globalServerCleanup() {
        for (ObsidianKnife instance : instances) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                instance.instantClearIdentity(player);
            }
            instance.activeIdentity.clear();
        }
    }

    public void unregister() {
        instances.remove(this);
    }

    private void instantClearIdentity(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        String value = pdc.get(originalValueKey, PersistentDataType.STRING);
        String sig = pdc.get(originalSigKey, PersistentDataType.STRING);

        PlayerProfile profile = player.getPlayerProfile();
        profile.removeProperty("textures");

        if (value != null && sig != null) {
            profile.setProperty(new ProfileProperty("textures", value, sig));
        }

        player.setPlayerProfile(profile);

        pdc.remove(identityNameKey);
        pdc.remove(identityExpireKey);
        pdc.remove(identityValueKey);
        pdc.remove(identitySigKey);

        if (plugin.isEnabled() && player.isOnline()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hidePlayer(plugin, player);
                p.showPlayer(plugin, player);
            }
        }
    }

    // =========================================================
    // INSTANT QUIT CLEANUP
    // =========================================================
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        instantClearIdentity(player);
        activeIdentity.remove(player.getUniqueId());
    }

    // =========================================================
    // INSTANT DEATH CLEANUP
    // =========================================================
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (pdcHasIdentity(player)) {
            instantClearIdentity(player);
            activeIdentity.remove(player.getUniqueId());
            player.sendMessage(Component.text("Your identity was lost upon death!", NamedTextColor.RED));
        }
    }

    // =========================================================
    // COMMAND MONITORING SKIN WATCHER
    // =========================================================
    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            boolean hasSkin = player.getPlayerProfile().getProperties().stream()
                    .anyMatch(p -> p.getName().equals("textures"));

            if (!hasSkin) {
                setSkinFromNameAsync(player, "Notch");
                player.sendMessage(Component.text("Default skin state detected after command delay. Recovering Notch profile...", NamedTextColor.RED));
            }
        }, 20L);
    }

    // =========================================================
    // JOIN LOGIC
    // =========================================================
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            ensureOriginalSkinCaptured(player);

            boolean hasSkin = player.getPlayerProfile().getProperties().stream()
                    .anyMatch(p -> p.getName().equals("textures"));

            if (!hasSkin) {
                setSkinFromNameAsync(player, "Notch");
                player.sendMessage(Component.text("Default skin detected.", NamedTextColor.RED));
            }

        }, 40L);
    }

    // =========================================================
    // ATTACK → DROP SKIN
    // =========================================================
    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon.getType() == Material.AIR || !weapon.hasItemMeta()) return;

        ItemMeta meta = weapon.getItemMeta();
        if (meta == null || meta.getItemModel() == null) return;

        NamespacedKey modelKey = meta.getItemModel();
        if (!modelKey.toString().equals("minecraft:obsidianknife")) return;

        victim.getWorld().dropItemNaturally(
                victim.getLocation(),
                createSkinItem(victim)
        );
    }

    // =========================================================
    // BLOCKS THE VICTIM FROM PICKING UP THEIR OWN FLESH
    // =========================================================
    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Item droppedItem = event.getItem();
        ItemStack itemStack = droppedItem.getItemStack();
        if (!itemStack.hasItemMeta()) return;

        PersistentDataContainer container = itemStack.getItemMeta().getPersistentDataContainer();
        if (container.has(victimUuidKey, PersistentDataType.STRING)) {
            String rawUuid = container.get(victimUuidKey, PersistentDataType.STRING);

            if (rawUuid != null && player.getUniqueId().toString().equals(rawUuid)) {
                event.setCancelled(true);
            }
        }
    }

    // =========================================================
    // CONSUME → START IDENTITY
    // =========================================================
    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer c = meta.getPersistentDataContainer();
        if (!c.has(victimNameKey, PersistentDataType.STRING)) return;

        String targetName = c.get(victimNameKey, PersistentDataType.STRING);
        Player player = event.getPlayer();

        String value = c.get(textureValueKey, PersistentDataType.STRING);
        String sig = c.get(textureSignatureKey, PersistentDataType.STRING);

        Bukkit.getScheduler().runTask(plugin, () -> {
            ProfileProperty prop = (value != null && sig != null)
                    ? new ProfileProperty("textures", value, sig)
                    : null;

            startIdentity(player, targetName, prop);
        });
    }

    private void ensureOriginalSkinCaptured(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc.has(originalValueKey, PersistentDataType.STRING)) return;

        PlayerProfile profile = player.getPlayerProfile();

        for (ProfileProperty prop : profile.getProperties()) {
            if (prop.getName().equals("textures")) {
                pdc.set(originalValueKey, PersistentDataType.STRING, prop.getValue());
                if (prop.getSignature() != null) {
                    pdc.set(originalSigKey, PersistentDataType.STRING, prop.getSignature());
                }
                return;
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ProfileProperty notch = getSkinProperty("Notch");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        pdc.set(originalValueKey, PersistentDataType.STRING, notch.getValue());
                        pdc.set(originalSigKey, PersistentDataType.STRING, notch.getSignature());
                    }
                });
            } catch (Exception ignored) {}
        });
    }

    private void startIdentity(Player player, String name, ProfileProperty property) {
        long duration = 5 * 60 * 1000; // 5 minutes
        long expire = System.currentTimeMillis() + duration;

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        ensureOriginalSkinCaptured(player);

        pdc.set(identityNameKey, PersistentDataType.STRING, name);
        pdc.set(identityExpireKey, PersistentDataType.LONG, expire);

        if (property != null) {
            pdc.set(identityValueKey, PersistentDataType.STRING, property.getValue());
            if (property.getSignature() != null) {
                pdc.set(identitySigKey, PersistentDataType.STRING, property.getSignature());
            }
        }

        activeIdentity.put(player.getUniqueId(), expire);
        applyIdentity(player, name, property, duration);
    }

    private void applyIdentity(Player player, String name, ProfileProperty property, long remainingMillis) {
        PlayerProfile profile = player.getPlayerProfile();
        profile.removeProperty("textures");

        if (property != null) {
            profile.setProperty(property);
        }

        player.setPlayerProfile(profile);

        setNameplate(player, name);
        refresh(player);

        player.sendMessage(Component.text("Identity Shifted: " + name, NamedTextColor.DARK_RED));

        // =========================================================
        // QUALITY OF LIFE TIME WARNING SYSTEM
        // =========================================================
        int[] warningMilestones = {30, 10, 5, 4, 3, 2, 1};

        for (int seconds : warningMilestones) {
            long warnDelayMs = remainingMillis - (seconds * 1000L);
            if (warnDelayMs > 0) {
                long ticks = warnDelayMs / 50L;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline() || !pdcHasIdentity(player)) return;
                    player.sendMessage(Component.text("Your disguise is running out in " + seconds + " seconds!", NamedTextColor.YELLOW));
                }, ticks);
            }
        }

        // Expiration Task
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            clearIdentity(player);
        }, remainingMillis / 50L);
    }

    private boolean pdcHasIdentity(Player player) {
        return player.getPersistentDataContainer().has(identityNameKey, PersistentDataType.STRING);
    }

    private void clearIdentity(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        // Only run clear messages if they actually had an active identity asset
        boolean wasDisguised = pdc.has(identityNameKey, PersistentDataType.STRING);

        String value = pdc.get(originalValueKey, PersistentDataType.STRING);
        String sig = pdc.get(originalSigKey, PersistentDataType.STRING);

        PlayerProfile profile = player.getPlayerProfile();
        profile.removeProperty("textures");

        if (value != null && sig != null) {
            profile.setProperty(new ProfileProperty("textures", value, sig));
        }

        player.setPlayerProfile(profile);

        pdc.remove(identityNameKey);
        pdc.remove(identityExpireKey);
        pdc.remove(identityValueKey);
        pdc.remove(identitySigKey);

        activeIdentity.remove(player.getUniqueId());
        refresh(player);

        if (wasDisguised) {
            player.sendMessage(Component.text("Your disguise has run out!", NamedTextColor.RED));
        }
    }

    private void setNameplate(Player player, String name) {
        refresh(player);
    }

    private void resetNameplate(Player player) {
        refresh(player);
    }

    public String getIdentityName(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc.has(this.identityNameKey, PersistentDataType.STRING)) {
            return pdc.get(this.identityNameKey, PersistentDataType.STRING);
        }
        return null;
    }

    private ProfileProperty getSkinProperty(String name) throws Exception {
        URL url0 = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
        JsonObject obj0 = JsonParser.parseReader(new InputStreamReader(url0.openStream())).getAsJsonObject();
        String uuid = obj0.get("id").getAsString();

        URL url1 = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
        JsonObject obj1 = JsonParser.parseReader(new InputStreamReader(url1.openStream())).getAsJsonObject();
        JsonObject prop = obj1.get("properties").getAsJsonArray().get(0).getAsJsonObject();

        return new ProfileProperty(
                "textures",
                prop.get("value").getAsString(),
                prop.get("signature").getAsString()
        );
    }

    private void setSkinFromNameAsync(Player player, String targetName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ProfileProperty property = getSkinProperty(targetName);
                Bukkit.getScheduler().runTask(plugin, () -> applySkin(player, property));
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(Component.text("Failed to load skin", NamedTextColor.RED));
                    }
                });
            }
        });
    }

    private void applySkin(Player player, ProfileProperty property) {
        PlayerProfile profile = player.getPlayerProfile();
        profile.removeProperty("textures");

        if (property != null) {
            profile.setProperty(property);
        }

        player.setPlayerProfile(profile);
        refresh(player);
    }

    private void refresh(Player player) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hidePlayer(plugin, player);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showPlayer(plugin, player);
            }
        }, 2L);
    }

    private ItemStack createSkinItem(Player victim) {
        ItemStack item = new ItemStack(Material.ROTTEN_FLESH);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text(
                "Harvested Skin: " + victim.getName(),
                NamedTextColor.GOLD
        ).decoration(TextDecoration.ITALIC, false));

        meta.getPersistentDataContainer().set(
                victimNameKey,
                PersistentDataType.STRING,
                victim.getName()
        );

        // Tags the item with the victim's UUID to prevent pickup self-sabotage
        meta.getPersistentDataContainer().set(
                victimUuidKey,
                PersistentDataType.STRING,
                victim.getUniqueId().toString()
        );

        for (ProfileProperty prop : victim.getPlayerProfile().getProperties()) {
            if (prop.getName().equals("textures")) {
                meta.getPersistentDataContainer().set(textureValueKey, PersistentDataType.STRING, prop.getValue());
                if (prop.getSignature() != null) {
                    meta.getPersistentDataContainer().set(textureSignatureKey, PersistentDataType.STRING, prop.getSignature());
                }
                break;
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createObsidianKnifeItem() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setItemModel(NamespacedKey.minecraft("obsidianknife"));

            meta.displayName(Component.text("SCP-034 (Obsidian Ritual Knife)", NamedTextColor.DARK_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(List.of(
                    Component.text("''An ancient blade, sharp beyond reason.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, true),
                    Component.text("Flesh becomes a mask, identity a illusion.''", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, true),
                    Component.text("Hitting someone with this knife drops their flesh.", NamedTextColor.LIGHT_PURPLE),
                    Component.text("Consuming the flesh allows you to take the other's identity.", NamedTextColor.LIGHT_PURPLE)
            ));

            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

            NamespacedKey damageKey = new NamespacedKey(plugin, "obsidian_knife_damage");
            NamespacedKey speedKey = new NamespacedKey(plugin, "obsidian_knife_speed");

            AttributeModifier damageModifier = new AttributeModifier(
                    damageKey,
                    5.0,
                    AttributeModifier.Operation.ADD_NUMBER
            );

            AttributeModifier speedModifier = new AttributeModifier(
                    speedKey,
                    1.5,
                    AttributeModifier.Operation.ADD_NUMBER
            );

            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, damageModifier);
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, speedModifier);

            item.setItemMeta(meta);
        }

        return item;
    }
}