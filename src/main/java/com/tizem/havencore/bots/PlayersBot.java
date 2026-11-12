package com.tizem.havencore.bots;

import com.tizem.havencore.HavenCore;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.Collection;

/** Bot 1: shows who is online. */
public final class PlayersBot extends DiscordBot {

    private BukkitTask task;

    public PlayersBot(HavenCore plugin) {
        super(plugin, "players");
    }

    @Override
    protected void onReady() {
        int sec = Math.max(10, i("update-seconds", 30));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (task != null) task.cancel();
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::update, 20L, sec * 20L);
        });
    }

    @Override
    protected void onStop(boolean restarting) {
        if (task != null) { task.cancel(); task = null; }
        TextChannel ch = channel("channel-id");
        if (ch != null) {
            upsertBlocking(ch, new EmbedBuilder()
                    .setTitle("Players Online (0)")
                    .setDescription("*Server is " + (restarting ? "restarting" : "offline") + "*")
                    .setColor(0xED4245).setTimestamp(Instant.now()).build());
        }
    }

    private void update() {
        if (jda == null) return;
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        int n = online.size();

        StringBuilder sb = new StringBuilder();
        for (Player p : online) {
            sb.append("• **").append(p.getName()).append("** — ").append(p.getPing()).append("ms\n");
        }

        jda.getPresence().setActivity(Activity.watching(n + " player" + (n == 1 ? "" : "s") + " online"));

        TextChannel ch = channel("channel-id");
        if (ch == null) return;
        upsert(ch, new EmbedBuilder()
                .setTitle("Players Online (" + n + "/" + Bukkit.getMaxPlayers() + ")")
                .setDescription(n == 0 ? "*Nobody online*" : cut(sb.toString(), 3900))
                .setColor(0x57F287)
                .setTimestamp(Instant.now())
                .build());
    }
}
