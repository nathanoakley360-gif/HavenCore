package com.tizem.havencore.bots;

import com.tizem.havencore.HavenCore;
import net.dv8tion.jda.api.utils.messages.MessageRequest;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BotManager {

    private final HavenCore plugin;
    private final PlayersBot players;
    private final StatusBot status;
    private final MonitorBot monitor;
    private final CommandBot commands;
    private final ChatBot chat;
    private final List<DiscordBot> all;

    public BotManager(HavenCore plugin) {
        this.plugin = plugin;
        this.players = new PlayersBot(plugin);
        this.status = new StatusBot(plugin);
        this.monitor = new MonitorBot(plugin);
        this.commands = new CommandBot(plugin, monitor);
        this.chat = new ChatBot(plugin);
        this.all = List.of(players, status, monitor, commands, chat);
    }

    public MonitorBot monitor() { return monitor; }

    public void registerListeners() {
        Bukkit.getPluginManager().registerEvents(monitor, plugin);
        Bukkit.getPluginManager().registerEvents(chat, plugin);
    }

    public void startAll() {
        // never let a Discord message ping @everyone / users / roles by accident
        MessageRequest.setDefaultMentions(Collections.emptyList());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (DiscordBot bot : all) bot.start();
        });
    }

    public void stopAll(boolean restarting) {
        for (DiscordBot bot : all) bot.stop(restarting);
    }

    public void reload() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (DiscordBot bot : all) bot.stop(false);
            for (DiscordBot bot : all) bot.start();
        });
    }

    public List<String> statusLines() {
        List<String> out = new ArrayList<>();
        out.add(HavenCore.PREFIX + "§cDiscord bots:");
        for (DiscordBot bot : all) {
            out.add("  §8▸ §f" + bot.key() + " §7- " + (bot.isOnline() ? "§aonline" : "§coffline/disabled"));
        }
        return out;
    }
}
