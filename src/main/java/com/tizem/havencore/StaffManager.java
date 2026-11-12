package com.tizem.havencore;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class StaffManager implements Listener {

    private record Saved(Location loc, GameMode mode) {}

    private final HavenCore plugin;
    private final Map<UUID, String> sus = new HashMap<>();
    private final Set<UUID> frozen = new HashSet<>();
    private final Map<UUID, Saved> watching = new HashMap<>();      // staff -> where they were
    private final Map<UUID, UUID> watchTarget = new HashMap<>();    // staff -> watched player
    private boolean chatMuted;

    public StaffManager(HavenCore plugin) {
        this.plugin = plugin;
    }

    // ---------- sus ----------
    public Map<UUID, String> susMap() { return sus; }

    /** @return true if now marked, false if unmarked */
    public boolean toggleSus(UUID id, String reason) {
        if (sus.remove(id) != null) return false;
        sus.put(id, reason);
        return true;
    }

    // ---------- freeze ----------
    public boolean toggleFreeze(UUID id) {
        if (frozen.remove(id)) return false;
        frozen.add(id);
        return true;
    }

    public boolean isFrozen(UUID id) { return frozen.contains(id); }

    // ---------- watch ----------
    public void startWatch(Player staff, Player target) {
        UUID id = staff.getUniqueId();
        if (!watching.containsKey(id)) {
            watching.put(id, new Saved(staff.getLocation(), staff.getGameMode()));
        }
        watchTarget.put(id, target.getUniqueId());
        staff.setGameMode(GameMode.SPECTATOR);
        staff.teleport(target);
        staff.setSpectatorTarget(target);
    }

    public boolean stopWatch(Player staff) {
        UUID id = staff.getUniqueId();
        Saved s = watching.remove(id);
        watchTarget.remove(id);
        if (s == null) return false;
        staff.setSpectatorTarget(null);
        staff.setGameMode(s.mode());
        staff.teleport(s.loc());
        return true;
    }

    public void endAllWatches() {
        for (UUID id : new ArrayList<>(watching.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) stopWatch(p);
        }
    }

    // ---------- mute chat ----------
    public boolean toggleMute() {
        chatMuted = !chatMuted;
        return chatMuted;
    }

    // ---------- events ----------
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!frozen.contains(e.getPlayer().getUniqueId())) return;
        Location f = e.getFrom();
        Location t = e.getTo();
        if (t == null) return;
        if (f.getX() != t.getX() || f.getY() != t.getY() || f.getZ() != t.getZ()) {
            e.setTo(new Location(f.getWorld(), f.getX(), f.getY(), f.getZ(), t.getYaw(), t.getPitch()));
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (frozen.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (frozen.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (frozen.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(HavenCore.PREFIX + "§cYou are frozen.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        if (chatMuted && !e.getPlayer().hasPermission("havencore.staff")) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(HavenCore.PREFIX + "§cChat is muted.");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String reason = sus.get(p.getUniqueId());
        if (reason != null) {
            plugin.staffAlert("§e" + p.getName() + " §fjoined and is marked §csus §7(" + reason + ")");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        if (frozen.contains(id)) {
            plugin.staffAlert("§c" + p.getName() + " §fleft while frozen!");
        }
        if (watching.containsKey(id)) {
            stopWatch(p);
        }
        // staff who were watching this player
        for (Map.Entry<UUID, UUID> en : new ArrayList<>(watchTarget.entrySet())) {
            if (en.getValue().equals(id)) {
                Player st = Bukkit.getPlayer(en.getKey());
                if (st != null) {
                    stopWatch(st);
                    st.sendMessage(HavenCore.PREFIX + "§e" + p.getName() + " §fleft, you were returned.");
                }
            }
        }
    }
}
