package com.tizem.havencore;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StaffCommands implements CommandExecutor, TabCompleter {

    private final HavenCore plugin;

    public StaffCommands(HavenCore plugin) {
        this.plugin = plugin;
    }

    private void msg(CommandSender s, String m) {
        s.sendMessage(HavenCore.PREFIX + m);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        StaffManager staff = plugin.staff();
        switch (cmd.getName().toLowerCase()) {

            case "sus" -> {
                if (args.length == 0) { msg(sender, "§cUsage: /sus <player> [reason] | /sus list"); return true; }
                if (args[0].equalsIgnoreCase("list")) {
                    if (staff.susMap().isEmpty()) { msg(sender, "No one is marked sus."); return true; }
                    msg(sender, "§cSus list:");
                    for (Map.Entry<UUID, String> en : staff.susMap().entrySet()) {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(en.getKey());
                        sender.sendMessage("  §8▸ §e" + op.getName() + " §7- " + en.getValue());
                    }
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[0]);
                if (t == null) { msg(sender, "§cPlayer not online."); return true; }
                String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "no reason";
                boolean on = staff.toggleSus(t.getUniqueId(), reason);
                plugin.staffAlert("§e" + sender.getName() + " §f" + (on ? "marked §e" + t.getName() + " §fas §csus §7(" + reason + ")"
                        : "removed sus from §e" + t.getName()));
            }

            case "watch" -> {
                if (!(sender instanceof Player p)) { msg(sender, "§cPlayers only."); return true; }
                if (args.length == 0 || args[0].equalsIgnoreCase("off")) {
                    if (staff.stopWatch(p)) msg(p, "§aStopped watching, returned.");
                    else msg(p, "§cYou are not watching anyone. Use /watch <player>");
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[0]);
                if (t == null) { msg(p, "§cPlayer not online."); return true; }
                if (t.equals(p)) { msg(p, "§cYou can't watch yourself."); return true; }
                staff.startWatch(p, t);
                msg(p, "§aNow watching §e" + t.getName() + "§a. Use §f/watch off §ato return.");
                plugin.staffAlert("§e" + p.getName() + " §fis watching §e" + t.getName());
            }

            case "freeze" -> {
                if (args.length == 0) { msg(sender, "§cUsage: /freeze <player>"); return true; }
                Player t = Bukkit.getPlayerExact(args[0]);
                if (t == null) { msg(sender, "§cPlayer not online."); return true; }
                boolean on = staff.toggleFreeze(t.getUniqueId());
                t.sendMessage(HavenCore.PREFIX + (on ? "§cYou have been frozen by staff. Do not log out!" : "§aYou have been unfrozen."));
                plugin.staffAlert("§e" + sender.getName() + " §f" + (on ? "froze" : "unfroze") + " §e" + t.getName());
            }

            case "sc" -> {
                if (args.length == 0) { msg(sender, "§cUsage: /sc <message>"); return true; }
                String text = String.join(" ", args);
                String line = "§b§lSTAFF §8▸ §7" + sender.getName() + "§8: §f" + text;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("havencore.staff")) p.sendMessage(line);
                }
                plugin.getLogger().info("[StaffChat] " + sender.getName() + ": " + text);
                plugin.bots().monitor().log("[staff chat] " + sender.getName() + ": " + text);
            }

            case "mutechat" -> {
                boolean muted = staff.toggleMute();
                Bukkit.broadcastMessage(HavenCore.PREFIX + (muted ? "§cChat has been muted by staff." : "§aChat has been unmuted."));
                plugin.staffAlert("§e" + sender.getName() + " §f" + (muted ? "muted" : "unmuted") + " chat");
            }

            case "hrestart" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("cancel")) {
                    if (!plugin.restart().cancel()) msg(sender, "§cNo restart is running.");
                    return true;
                }
                int secs = plugin.getConfig().getInt("restart.countdown-seconds", 30);
                if (args.length > 0) {
                    try { secs = Integer.parseInt(args[0]); } catch (NumberFormatException e) { msg(sender, "§cUsage: /hrestart [seconds|cancel]"); return true; }
                }
                if (plugin.restart().isRestarting()) { msg(sender, "§cA restart is already running."); return true; }
                plugin.restart().begin(secs);
                plugin.staffAlert("§e" + sender.getName() + " §fstarted a restart (" + secs + "s)");
            }

            case "havencore" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                    plugin.reloadConfig();
                    plugin.bots().reload();
                    msg(sender, "§aConfig reloaded, bots restarting.");
                } else if (args.length > 0 && args[0].equalsIgnoreCase("bots")) {
                    for (String line : plugin.bots().statusLines()) sender.sendMessage(line);
                } else {
                    msg(sender, "§cUsage: /havencore <reload|bots>");
                }
            }
            default -> { return false; }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String name = cmd.getName().toLowerCase();
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String start = args[0].toLowerCase();
            if (name.equals("sus") || name.equals("watch") || name.equals("freeze")) {
                if (name.equals("sus")) out.add("list");
                if (name.equals("watch")) out.add("off");
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            } else if (name.equals("hrestart")) {
                out.add("cancel");
            } else if (name.equals("havencore")) {
                out.add("reload");
                out.add("bots");
            }
            out.removeIf(s -> !s.toLowerCase().startsWith(start));
        }
        return out;
    }
}
