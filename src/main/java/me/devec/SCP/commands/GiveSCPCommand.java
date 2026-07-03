package me.devec.SCP.commands;

import me.devec.SCP.SCPs.Panacea;
import me.devec.SCP.SCPs.ObsidianKnife;
import me.devec.SCP.SCPs.JailBird;
import me.devec.SCP.SCPs.Candies.CandyBowl;
import me.devec.SCP.SCPs.PreformanceEnchancer;
import me.devec.SCP.SCPs.SuperBall;
import me.devec.SCP.SCPs.Cola; // Imported Cola Module
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GiveSCPCommand implements CommandExecutor, TabCompleter {

    private final ObsidianKnife knifeModule;
    private final CandyBowl candyBowlModule;

    public GiveSCPCommand(ObsidianKnife knifeModule, CandyBowl candyBowlModule) {
        this.knifeModule = knifeModule;
        this.candyBowlModule = candyBowlModule;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp() && !sender.hasPermission("SCP.givescp")) {
            sender.sendMessage(Component.text("You do not have permission to execute this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /givescp <item> [player]", NamedTextColor.RED));
            return true;
        }

        String scpType = args[0].toLowerCase();
        ItemStack itemToGive;

        if (scpType.equals("panacea") || scpType.equals("scp500")) {
            itemToGive = Panacea.createPanaceaItem();
        } else if (scpType.equals("knife") || scpType.equals("scp034")) {
            itemToGive = knifeModule.createObsidianKnifeItem();
        } else if (scpType.equals("bowl") || scpType.equals("scp330") || scpType.equals("candybowl")) {
            itemToGive = candyBowlModule.createCandyBowlItem();
        } else if (scpType.equals("jailbird")) {
            itemToGive = JailBird.spawnJailBird();
        } else if (scpType.equals("scp1853") || scpType.equals("preformanceenchancer")) {
            itemToGive = PreformanceEnchancer.createItem();
        } else if (scpType.equals("superball") || scpType.equals("scp018")) {
            itemToGive = SuperBall.createItem();
        } else if (scpType.equals("cola") || scpType.equals("scp207")) { // Integrated Cola check
            itemToGive = Cola.createColaItem();
        } else {
            sender.sendMessage(Component.text("Unknown SCP item type!", NamedTextColor.RED));
            return true;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found online.", NamedTextColor.RED));
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Component.text("Console must specify a target player name!", NamedTextColor.RED));
                return true;
            }
            target = (Player) sender;
        }

        target.getInventory().addItem(itemToGive);
        target.sendMessage(Component.text("You have been given an SCP item!", NamedTextColor.GREEN));

        if (target != sender) {
            sender.sendMessage(Component.text("Gave item to " + target.getName(), NamedTextColor.GREEN));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.isOp() && !(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String currentInput = args[0].toLowerCase();
            if ("panacea".startsWith(currentInput)) completions.add("panacea");
            if ("knife".startsWith(currentInput)) completions.add("knife");
            if ("bowl".startsWith(currentInput)) completions.add("bowl");
            if ("jailbird".startsWith(currentInput)) completions.add("jailbird");
            if ("preformanceenchancer".startsWith(currentInput)) completions.add("preformanceenchancer");
            if ("superball".startsWith(currentInput)) completions.add("superball");
            if ("cola".startsWith(currentInput)) completions.add("cola"); // Integrated Cola tab completion
        } else if (args.length == 2) {
            String currentInput = args[1].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(currentInput)) {
                    completions.add(player.getName());
                }
            }
        }
        return completions;
    }
}