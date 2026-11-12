package com.tizem.havencore;

import com.tizem.havencore.bots.BotManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class HavenCore extends JavaPlugin {

    public static final String PREFIX = "§c§lHAVEN §8▸ §f";

    private StaffManager staff;
    private RestartManager restart;
    private BotManager bots;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        staff = new StaffManager(this);
        restart = new RestartManager(this);
        bots = new BotManager(this);

        StaffCommands cmds = new StaffCommands(this);
        for (String name : new String[]{"sus", "watch", "freeze", "sc", "mutechat", "hrestart", "havencore"}) {
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(cmds);
                getCommand(name).setTabCompleter(cmds);
            }
        }

        Bukkit.getPluginManager().registerEvents(staff, this);
        Bukkit.getPluginManager().registerEvents(restart, this);
        bots.registerListeners();

        restart.scheduleAuto();
        bots.startAll();
        getLogger().info("HavenCore enabled.");
    }

    @Override
    public void onDisable() {
        if (staff != null) staff.endAllWatches();
        boolean restarting = restart != null && restart.isRestarting();
        if (restart != null) restart.cancelTasks();
        if (bots != null) bots.stopAll(restarting);
    }

    public StaffManager staff() { return staff; }
    public RestartManager restart() { return restart; }
    public BotManager bots() { return bots; }

    /** Message to every online staff member + console + Discord staff log. */
    public void staffAlert(String msg) {
        String line = PREFIX + msg;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("havencore.staff")) p.sendMessage(line);
        }
        String plain = msg.replaceAll("§.", "");
        getLogger().info(plain);
        if (bots != null) bots.monitor().log(plain);
    }
}
