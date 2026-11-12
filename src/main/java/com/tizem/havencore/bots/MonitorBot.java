package com.tizem.havencore.bots;

import com.tizem.havencore.HavenCore;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

/** Bot 3: watches game chat + Discord chat, flags bad words, logs staff actions. */
public final class MonitorBot extends DiscordBot implements Listener {

    public MonitorBot(HavenCore plugin) {
        super(plugin, "monitor");
    }

    @Override
    protected Collection<GatewayIntent> intents() {
        return List.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT);
    }

    // ---------- output ----------
    public void log(String text) {
        TextChannel ch = channel("channel-id");
        if (ch == null) return;
        ch.sendMessage("`[" + java.time.LocalTime.now().withNano(0) + "]` " + cut(text, 1900)).queue();
    }

    public void alert(String text) {
        TextChannel ch = channel("channel-id");
        if (ch == null) return;
        String role = s("alert-role-id");
        String ping = role.isBlank() ? "" : "<@&" + role + "> ";
        ch.sendMessage(ping + "⚠️ " + cut(text, 1800))
                .setAllowedMentions(EnumSet.of(Message.MentionType.ROLE))
                .queue();
    }

    // ---------- filter ----------
    private String match(String text) {
        for (String w : plugin.getConfig().getStringList("monitor.blocked-words")) {
            if (w.isBlank()) continue;
            if (Pattern.compile("(?i)\\b" + Pattern.quote(w) + "\\b").matcher(text).find()) return w;
        }
        return null;
    }

    // ---------- game chat ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameChat(AsyncChatEvent e) {
        if (jda == null) return;
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        String name = e.getPlayer().getName();
        String hit = match(text);
        if (hit != null) {
            alert("**Game chat** flagged `" + hit + "` | **" + name + "**: " + text);
        } else if (b("log-all-chat", false)) {
            log("[game] " + name + ": " + text);
        }
    }

    // ---------- Discord chat ----------
    @Override
    public void onMessageReceived(MessageReceivedEvent e) {
        if (!e.isFromGuild() || e.getAuthor().isBot() || e.isWebhookMessage()) return;
        String chatId = s("chat-channel-id");
        if (chatId.isBlank() || !e.getChannel().getId().equals(chatId)) return;

        String text = e.getMessage().getContentDisplay();
        String hit = match(text);
        if (hit != null) {
            e.getMessage().delete().queue(null, err -> { });
            alert("**Discord chat** flagged `" + hit + "` | **" + e.getAuthor().getName() + "**: " + text);
        } else if (b("log-all-chat", false)) {
            log("[discord] " + e.getAuthor().getName() + ": " + text);
        }
    }
}
