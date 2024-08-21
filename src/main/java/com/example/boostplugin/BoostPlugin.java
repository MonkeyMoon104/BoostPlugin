package com.example.boostplugin;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;
import com.github.sirblobman.combatlogx.api.event.PlayerPreTagEvent;


import java.util.*;


public final class BoostPlugin extends JavaPlugin implements Listener {
    private Map<Player, Long> playersOnCooldown = new HashMap<>();
    private Map<Player, BukkitTask> playersGeneratingLavaTrail = new HashMap<>();
    private Map<Player, String> lastUsedCustomItem = new HashMap<>();




    @Override
    public void onEnable() {
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();

        GiveCommand giveCommand = new GiveCommand(this);
        getCommand("magia").setExecutor(giveCommand);
        getCommand("magia").setTabCompleter(giveCommand);

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("Plugin abilitato con successo!");
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            ItemStack item = player.getInventory().getItemInMainHand();

            if (isCustomItem(item)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInMainHand = player.getInventory().getItemInMainHand();
        ItemStack itemInOffHand = player.getInventory().getItemInOffHand();

        if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            if (isCustomItem(itemInMainHand) || isCustomItem(itemInOffHand)) {

                if (isCustomItem(itemInMainHand)) {
                    applyBoost(player, itemInMainHand);
                }
                if (isCustomItem(itemInOffHand)) {
                    applyBoost(player, itemInOffHand);
                }
            }
        }
    }


    private boolean isCustomItem(ItemStack item) {
        if (item != null && item.hasItemMeta()) {
            ItemMeta itemMeta = item.getItemMeta();
            if (itemMeta.hasDisplayName()) {
                String displayName = ChatColor.stripColor(itemMeta.getDisplayName());

                ConfigurationSection itemsConfig = getConfig().getConfigurationSection("items");
                if (itemsConfig != null) {
                    for (String itemName : itemsConfig.getKeys(false)) {
                        ConfigurationSection itemConfig = itemsConfig.getConfigurationSection(itemName);
                        if (itemConfig != null && itemConfig.isString("item")) {
                            String itemType = itemConfig.getString("item");
                            Material material = Material.getMaterial(itemType);

                            if (material != null && item.getType() == material) {
                                String customItemName = ChatColor.stripColor(itemConfig.getString("name"));

                                if (displayName.equals(customItemName)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }


    public void giveCustomItem(String itemName, CommandSender sender, String playerName) {
        ConfigurationSection itemsConfig = getConfig().getConfigurationSection("items");

        if (itemsConfig != null && itemsConfig.contains(itemName)) {
            ConfigurationSection itemConfig = itemsConfig.getConfigurationSection(itemName);
            String itemType = itemConfig.getString("item");
            if (itemType != null) {
                Material material = Material.getMaterial(itemType);

                if (material != null) {

                    ItemStack customItem = new ItemStack(material);
                    ItemMeta itemMeta = customItem.getItemMeta();
                    int usage = itemConfig.getInt("usage");
                    int boostintensity = itemConfig.getInt("intensity-boost");
                    int boostcooldown = itemConfig.getInt("cooldown-boost");


                    itemMeta.setDisplayName(itemConfig.getString("name"));


                    List<String> lore = new ArrayList<>();
                    lore.addAll(itemConfig.getStringList("lore"));
                    lore.add(ChatColor.DARK_GREEN + "Max boost usage: " + ChatColor.AQUA + usage);
                    lore.add(ChatColor.BLUE + "Ulitità: " + ChatColor.YELLOW + "BOOSTING");
                    lore.add(ChatColor.LIGHT_PURPLE + "Intensità del boost: " + ChatColor.GREEN + boostintensity);
                    lore.add(ChatColor.RED + "Cooldown boost: " + ChatColor.DARK_AQUA + boostcooldown + ChatColor.RED + " sec");
                    lore.add(ChatColor.GREEN + "Damage: " + ChatColor.RED + "0");

                    itemMeta.setLore(lore);


                    ConfigurationSection enchantmentsConfig = itemConfig.getConfigurationSection("enchantments");
                    if (enchantmentsConfig != null) {
                        enchantmentsConfig.getKeys(false).forEach(enchantName -> {
                            int enchantLevel = enchantmentsConfig.getInt(enchantName);
                            itemMeta.addEnchant(Enchantment.getByName(enchantName), enchantLevel, true);
                        });
                    }

                    if (itemConfig.contains("custom-model-data")) {
                        int customModelData = itemConfig.getInt("custom-model-data");
                        itemMeta.setCustomModelData(customModelData);
                    }



                    PersistentDataContainer data = itemMeta.getPersistentDataContainer();
                    Double randomDouble = Math.floor(Math.random() * (Double.MAX_VALUE - Double.MIN_VALUE + 1) + Double.MIN_VALUE);
                    data.set(new NamespacedKey(this, String.valueOf(randomDouble)), PersistentDataType.DOUBLE, randomDouble);
                    data.set(new NamespacedKey(this, "CustomItemUnique"), PersistentDataType.STRING, "true");

                    customItem.setItemMeta(itemMeta);


                    Player targetPlayer = getServer().getPlayer(playerName);

                    if (targetPlayer != null) {

                        int emptySlot = targetPlayer.getInventory().firstEmpty();
                        if (emptySlot != -1) {
                            targetPlayer.getInventory().setItem(emptySlot, customItem);
                            sender.sendMessage(ChatColor.GREEN + "Given custom item " + ChatColor.AQUA + itemName + ChatColor.GREEN + " to " + ChatColor.YELLOW + targetPlayer.getName());
                        } else {
                            sender.sendMessage(ChatColor.RED + "Il giocatore ha l'inventario pieno, l'oggetto personalizzato non può essere dato.");
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "Player not found.");
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Invalid item type.");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Item type not defined for " + itemName);
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Custom item not found.");
        }
    }

    private boolean hasCooldownExpired(Player player) {
        Long cooldownEndTime = playersOnCooldown.get(player);
        if (cooldownEndTime == null || System.currentTimeMillis() >= cooldownEndTime) {
            return true;
        }
        return false;
    }

    private boolean canUseCustomItem(Player player, String customItemName) {
        String lastCustomItemUsed = lastUsedCustomItem.get(player);
        if (lastCustomItemUsed == null || !lastCustomItemUsed.equals(customItemName)) {
            return true;
        }
        return false;
    }

    private void applyBoost(Player player, ItemStack item) {
        if (!isPlayerInRestrictedRegion(player)) {
            ConfigurationSection itemsConfig = getConfig().getConfigurationSection("items");

            if (itemsConfig != null) {
                for (String itemName : itemsConfig.getKeys(false)) {
                    ConfigurationSection itemConfig = itemsConfig.getConfigurationSection(itemName);

                    if (itemConfig != null && itemConfig.isString("item")) {
                        Material material = Material.getMaterial(itemConfig.getString("item"));

                        if (material != null && item.getType() == material) {
                            String customItemName = ChatColor.stripColor(itemConfig.getString("name"));

                            if (customItemName != null && customItemName.equals(ChatColor.stripColor(item.getItemMeta().getDisplayName()))) {
                                ItemMeta itemMeta = item.getItemMeta();

                                if (!playerIsOnCooldown(player) && hasCooldownExpired(player) && canUseCustomItem(player, customItemName)) {
                                    if (itemConfig.contains("usage")) {
                                        int maxUsages = itemConfig.getInt("usage");


                                        PersistentDataContainer data = itemMeta.getPersistentDataContainer();
                                        String usageKey = "CustomItemUsage_" + player.getUniqueId().toString();

                                        if (!data.has(new NamespacedKey(this, usageKey), PersistentDataType.INTEGER)) {

                                            List<String> lore = itemMeta.getLore();
                                            lore.add(ChatColor.GOLD + "Utilizzi: " + maxUsages + "/" + maxUsages);
                                            itemMeta.setLore(lore);


                                            data.set(new NamespacedKey(this, usageKey), PersistentDataType.INTEGER, maxUsages);
                                        }

                                        int remainingUsages = data.getOrDefault(new NamespacedKey(this, usageKey), PersistentDataType.INTEGER, maxUsages);

                                        if (remainingUsages > 0) {

                                            remainingUsages--;


                                            data.set(new NamespacedKey(this, usageKey), PersistentDataType.INTEGER, remainingUsages);


                                            List<String> lore = itemMeta.getLore();
                                            for (int i = 0; i < lore.size(); i++) {
                                                if (lore.get(i).startsWith(ChatColor.GOLD + "Utilizzi: ")) {
                                                    lore.set(i, ChatColor.GOLD + "Utilizzi: " + remainingUsages + "/" + maxUsages);
                                                    break;
                                                }
                                            }
                                            itemMeta.setLore(lore);

                                            int boostIntensity = itemConfig.getInt("intensity-boost");
                                            Vector boostVector = player.getLocation().getDirection().multiply(boostIntensity);

                                            player.getWorld().spawnParticle(Particle.REDSTONE, player.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2, new Particle.DustOptions(Color.RED, 1.0f));

                                            Vector jumpVector = new Vector(boostVector.getX(), 0.5, boostVector.getZ());
                                            player.setVelocity(player.getVelocity().add(jumpVector));

                                            double fakeDamage = 0.03;
                                            player.damage(fakeDamage);
                                            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "ctx tag " + player.getName() + " 10");

                                            boostVector.setY(player.getVelocity().getY());
                                            player.setVelocity(boostVector);

                                            ItemStack itemInMainHand = player.getInventory().getItemInMainHand();
                                            ItemStack itemInOffHand = player.getInventory().getItemInOffHand();

                                            if (remainingUsages <= 0) {
                                                if (itemInMainHand.equals(item)) {
                                                    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                                                } else if (itemInOffHand.equals(item)) {
                                                    player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                                                }
                                            }

                                            item.setItemMeta(itemMeta);


                                            if (player.isOnGround()) {
                                                Bukkit.getScheduler().runTaskLater(this, () -> {
                                                    if (!player.isOnGround()) {
                                                        generateLavaTrail(player);
                                                    }
                                                }, 4);

                                            } else {
                                                generateLavaTrail(player);
                                            }

                                            int cooldownSeconds = itemConfig.getInt("cooldown-boost");
                                            playersOnCooldown.put(player, System.currentTimeMillis() + cooldownSeconds * 1000);

                                            updateActionBar(player, cooldownSeconds);

                                        }
                                    }
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void stopGeneratingLavaTrail(Player player) {
        BukkitTask task = playersGeneratingLavaTrail.get(player);
        if (task != null) {
            task.cancel();
            playersGeneratingLavaTrail.remove(player);
        }
    }

    private void generateLavaTrail(Player player) {
        int particlesPerTick = 10;

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!player.isOnGround()) {
                Location playerLocation = player.getLocation().add(0, 1, 0);
                World world = player.getWorld();

                for (int i = 0; i < particlesPerTick; i++) {
                    double xOffset = (Math.random() - 0.5) * 0.3;
                    double yOffset = (Math.random() - 0.5) * 0.1;
                    double zOffset = (Math.random() - 0.5) * 0.3;

                    Location particleLocation = playerLocation.clone().add(xOffset, yOffset, zOffset);
                    world.spawnParticle(Particle.LAVA, particleLocation, 1, 0, 0, 0, 0);
                }


                world.playSound(playerLocation, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.5f, 1.0f);
            } else {
                stopGeneratingLavaTrail(player);
                generateTotemExplosionEffect(player);
            }
        }, 0, 1);

        playersGeneratingLavaTrail.put(player, task);
    }




    private void generateTotemExplosionEffect(Player player) {
        Location playerLocation = player.getLocation();
        World world = player.getWorld();


        double startRadius = 0.1;
        int steps = 10;
        int duration = 30;

        generateExpandingFirework(playerLocation, startRadius, steps, duration);


        world.playSound(playerLocation, Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
    }

    private void generateExpandingFirework(Location center, double startRadius, int steps, int duration) {
        World world = center.getWorld();
        double angleIncrement = 1 * Math.PI / steps;
        final double[] currentRadius = {startRadius};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (int t = 0; t < steps; t++) {
                double angle = angleIncrement * t;
                double x = center.getX() + currentRadius[0] * Math.cos(angle);
                double z = center.getZ() + currentRadius[0] * Math.sin(angle);
                double y = center.getY() + currentRadius[0] * Math.sin(angle);
                Location location = new Location(world, x, y, z);

                world.spawnParticle(Particle.FIREWORKS_SPARK, location, 1);
            }

            currentRadius[0] += 0.3;
        }, 0, 1);


        Bukkit.getScheduler().runTaskLater(this, () -> task.cancel(), duration);
    }

    private boolean playerIsOnCooldown(Player player) {
        return playersOnCooldown.containsKey(player) && playersOnCooldown.get(player) > System.currentTimeMillis();
    }

    private void updateActionBar(Player player, int cooldownSeconds) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (playerIsOnCooldown(player)) {
                long remainingTimeMillis = playersOnCooldown.get(player) - System.currentTimeMillis();
                int remainingTimeSeconds = (int) Math.ceil(remainingTimeMillis / 1000.0);

                if (remainingTimeSeconds <= 0) {
                    player.sendActionBar("");
                    playersOnCooldown.remove(player);
                } else {
                    player.sendActionBar("§6§l§oBoost Cooldown: §r§b§l" + remainingTimeSeconds + "s");
                    updateActionBar(player, cooldownSeconds);
                }
            }
        }, 20);
    }

    private boolean isPlayerInRestrictedRegion(Player player) {
        Location playerLocation = player.getLocation();
        String restrictedRegionName = getConfig().getString("region-boost-disallowed");

        if (restrictedRegionName != null) {
            RegionContainer regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();
            ApplicableRegionSet regions = regionContainer.createQuery().getApplicableRegions(BukkitAdapter.adapt(playerLocation));

            for (ProtectedRegion region : regions) {
                if (region.getId().equalsIgnoreCase(restrictedRegionName)) {
                    return true;
                }
            }
        }

        return false;
    }


    @Override
    public void onDisable() {

    }
}
