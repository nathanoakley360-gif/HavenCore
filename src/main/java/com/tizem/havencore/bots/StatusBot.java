package com.tizem.havencore.bots;

import com.tizem.havencore.HavenCore;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.lang.management.ManagementFactory;
import java.time.Instant;

/** Bot 2: server status (online / offline / restarting, TPS, RAM, uptime). */
public final class StatusBot extends DiscordBot {

    private BukkitTask task;

    public StatusBot(HavenCore plugin) {
        super(plugin, "status");
    }

    @Override
    protected void onReady() {
        TextChannel ch = channel("channel-id");
        if (ch != null) ch.sendMessage("🟢 **Server is online**").queue();

        int sec = Math.max(10, i("update-seconds", 30));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (task != null) task.cancel();
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::update, 20L, sec * 20L);
        });
    }

    @Override
    protected void onStop(boolean restarting) {
        if (task != null) { task.cancel(); task = null; }
        jda.getPresence().setPresence(OnlineStatus.DO_NOT_DISTURB,
                Activity.playing(restarting ? "Restarting..." : "Offline"));
        TextChannel ch = channel("channel-id");
        if (ch == null) return;
        upsertBlocking(ch, new EmbedBuilder()
                .setTitle(restarting ? "🔄 Server Restarting" : "🔴 Server Offline")
                .setDescription(restarting ? "Back in a minute." : "The server is currently offline.")
                .setColor(restarting ? 0xFEE75C : 0xED4245)
                .setTimestamp(Instant.now()).build());
        try {
            ch.sendMessage(restarting ? "🔄 **Server is restarting**" : "🔴 **Server is offline**").complete();
        } catch (Exception ignored) { }
    }

    private void update() {
        if (jda == null) return;
        double tps = Math.min(20.0, Bukkit.getTPS()[0]);
        double mspt = Bukkit.getAverageTickTime();
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
        long max = rt.maxMemory() / 1048576L;
        long up = ManagementFactory.getRuntimeMXBean().getUptime() / 1000L;
        String uptime = (up / 3600) + "h " + ((up % 3600) / 60) + "m";
        int online = Bukkit.getOnlinePlayers().size();

        int color = tps >= 18 ? 0x57F287 : tps >= 15 ? 0xFEE75C : 0xED4245;

        jda.getPresence().setPresence(OnlineStatus.ONLINE,
                Activity.playing(online + "/" + Bukkit.getMaxPlayers() + " online | " + String.format("%.1f", tps) + " TPS"));

        TextChannel ch = channel("channel-id");
        if (ch == null) return;
        upsert(ch, new EmbedBuilder()
                .setTitle("🟢 Server Online")
                .addField("Players", online + "/" + Bukkit.getMaxPlayers(), true)
                .addField("TPS", String.format("%.1f", tps), true)
                .addField("MSPT", String.format("%.1f ms", mspt), true)
                .addField("Memory", used + " / " + max + " MB", true)
                .addField("Uptime", uptime, true)
                .addField("Version", Bukkit.getMinecraftVersion(), true)
                .setColor(color)
                .setTimestamp(Instant.now())
                .build());
    }
}
