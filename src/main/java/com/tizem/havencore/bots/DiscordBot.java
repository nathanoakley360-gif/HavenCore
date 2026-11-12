package com.tizem.havencore.bots;

import com.tizem.havencore.HavenCore;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.Collection;
import java.util.List;

/** Base for the 5 bots. Each one has its own token and its own JDA connection. */
public abstract class DiscordBot extends ListenerAdapter {

    protected final HavenCore plugin;
    protected final String key;
    protected volatile JDA jda;

    private volatile String msgId;
    private volatile boolean finding;

    protected DiscordBot(HavenCore plugin, String key) {
        this.plugin = plugin;
        this.key = key;
    }

    public String key() { return key; }
    public boolean isOnline() { return jda != null && jda.getStatus() == JDA.Status.CONNECTED; }

    // ---------- config helpers ----------
    protected String s(String path) {
        return plugin.getConfig().getString("discord.bots." + key + "." + path, "");
    }

    protected int i(String path, int def) {
        return plugin.getConfig().getInt("discord.bots." + key + "." + path, def);
    }

    protected boolean b(String path, boolean def) {
        return plugin.getConfig().getBoolean("discord.bots." + key + "." + path, def);
    }

    protected List<String> list(String path) {
        return plugin.getConfig().getStringList("discord.bots." + key + "." + path);
    }

    // ---------- lifecycle ----------
    protected Collection<GatewayIntent> intents() {
        return List.of(GatewayIntent.GUILD_MESSAGES);
    }

    protected void onReady() { }

    protected void onStop(boolean restarting) { }

    /** Runs off the main thread. */
    public final void start() {
        String token = s("token");
        if (token.isBlank()) {
            plugin.getLogger().info("[" + key + " bot] no token set, disabled.");
            return;
        }
        try {
            msgId = null;
            finding = false;
            jda = JDABuilder.createLight(token, intents()).addEventListeners(this).build();
            jda.awaitReady();
            plugin.getLogger().info("[" + key + " bot] online as " + jda.getSelfUser().getName());
            onReady();
        } catch (Exception ex) {
            plugin.getLogger().severe("[" + key + " bot] failed to start: " + ex.getMessage());
            jda = null;
        }
    }

    public final void stop(boolean restarting) {
        JDA j = jda;
        if (j == null) return;
        try {
            onStop(restarting);
        } catch (Exception ignored) { }
        j.shutdown();
        jda = null;
    }

    // ---------- helpers ----------
    protected TextChannel channel(String path) {
        JDA j = jda;
        String id = s(path);
        if (j == null || id.isBlank()) return null;
        return j.getTextChannelById(id);
    }

    /** Edits this bot's one embed in the channel, or posts it the first time. */
    protected void upsert(TextChannel ch, MessageEmbed embed) {
        String id = msgId;
        if (id != null) {
            ch.editMessageEmbedsById(id, embed).queue(null, err -> msgId = null);
            return;
        }
        if (finding) return;
        finding = true;
        ch.getIterableHistory().takeAsync(30).whenComplete((history, ex) -> {
            if (ex != null || jda == null) { finding = false; return; }
            String self = jda.getSelfUser().getId();
            Message mine = null;
            for (Message m : history) {
                if (m.getAuthor().getId().equals(self) && !m.getEmbeds().isEmpty()) { mine = m; break; }
            }
            if (mine != null) {
                msgId = mine.getId();
                mine.editMessageEmbeds(embed).queue();
                finding = false;
            } else {
                ch.sendMessageEmbeds(embed).queue(m -> { msgId = m.getId(); finding = false; }, er -> finding = false);
            }
        });
    }

    /** Same as upsert but waits for it. Used while the server shuts down. */
    protected void upsertBlocking(TextChannel ch, MessageEmbed embed) {
        try {
            String id = msgId;
            if (id != null) ch.editMessageEmbedsById(id, embed).complete();
            else ch.sendMessageEmbeds(embed).complete();
        } catch (Exception ignored) { }
    }

    protected static String cut(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }
}
