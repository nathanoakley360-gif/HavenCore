package com.tizem.havencore;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class RestartManager implements Listener {

    private final HavenCore plugin;
    private volatile boolean restarting;
    private BukkitTask countdown;
    private BukkitTask auto;

    public RestartManager(HavenCore plugin) {
        this.plugin = plugin;
    }

    public boolean isRestarting() { return restarting; }

    public void scheduleAuto() {
        long hours = plugin.getConfig().getLong("restart.schedule-hours", 0);
        if (hours <= 0) return;
        long ticks = hours * 72000L;
        auto = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> begin(plugin.getConfig().getInt("restart.countdown-seconds", 30)), ticks, ticks);
    }

    public void cancelTasks() {
        if (auto != null) { auto.cancel(); auto = null; }
        if (countdown != null) { countdown.cancel(); countdown = null; }
    }

    /** Cancel a running countdown. */
    public boolean cancel() {
        if (countdown == null) return false;
        countdown.cancel();
        countdown = null;
        restarting = false;
        broadcast("§c§lRESTART §8▸ §fRestart cancelled.");
        return true;
    }

    public void begin(int seconds) {
        if (restarting) return;
        restarting = true;
        final int[] left = {Math.max(seconds, 1)};
        countdown = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int s = left[0];
            if (s <= 0) {
                if (countdown != null) countdown.cancel();
                countdown = null;
                doRestart();
                return;
            }
            if (s == 300 || s == 60 || s == 30 || s == 15 || s == 10 || s <= 5) {
                broadcast("§c§lRESTART §8▸ §fServer restarting in §c" + s + "s§f.");
            }
            left[0]--;
        }, 0L, 20L);
    }

    private void doRestart() {
        FileConfiguration cfg = plugin.getConfig();
        String url = cfg.getString("restart.panel.url", "");
        String id = cfg.getString("restart.panel.server-id", "");
        String key = cfg.getString("restart.panel.api-key", "");

        if (!url.isBlank() && !id.isBlank() && !key.isBlank()) {
            try {
                if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
                HttpRequest req = HttpRequest.newBuilder(URI.create(url + "/api/client/servers/" + id + "/power"))
                        .header("Authorization", "Bearer " + key)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"signal\":\"restart\"}"))
                        .build();
                HttpClient.newHttpClient().sendAsync(req, HttpResponse.BodyHandlers.discarding())
                        .whenComplete((r, ex) -> {
                            if (ex != null || r.statusCode() >= 300) {
                                plugin.getLogger().warning("Panel restart failed, shutting down instead.");
                                Bukkit.getScheduler().runTask(plugin, Bukkit::shutdown);
                            }
                        });
                return;
            } catch (Exception ex) {
                plugin.getLogger().warning("Panel restart error: " + ex.getMessage());
            }
        }
        // start.sh loop brings it back up
        Bukkit.shutdown();
    }

    private void broadcast(String legacy) {
        Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(legacy));
    }

    // ---------- /stop -> restart ----------
    private boolean handleStop(String raw) {
        if (!plugin.getConfig().getBoolean("restart.convert-stop", true)) return false;
        String c = raw.trim().toLowerCase();
        if (c.startsWith("/")) c = c.substring(1);
        if (c.startsWith("minecraft:")) c = c.substring("minecraft:".length());

        if (c.equals("stop force")) {
            try {
                new File(plugin.getServer().getWorldContainer(), "no-restart").createNewFile();
            } catch (Exception ignored) { }
            Bukkit.getScheduler().runTask(plugin, Bukkit::shutdown);
            return true;
        }
        if (c.equals("stop")) {
            if (restarting) return false; // second /stop while counting down = stop now
            int secs = plugin.getConfig().getInt("restart.countdown-seconds", 30);
            Bukkit.getScheduler().runTask(plugin, () -> begin(secs));
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsole(ServerCommandEvent e) {
        if (handleStop(e.getCommand())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        if (!e.getPlayer().hasPermission("minecraft.command.stop")) return;
        if (handleStop(e.getMessage())) e.setCancelled(true);
    }
}
