package com.tizem.havencore.bots;

import com.tizem.havencore.HavenCore;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collection;
import java.util.List;

/** Bot 5: broadcasts game chat to Discord (and Discord chat back into the game). */
public final class ChatBot extends DiscordBot implements Listener {

    public ChatBot(HavenCore plugin) {
        super(plugin, "chat");
    }

    @Override
    protected Collection<GatewayIntent> intents() {
        return List.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT);
    }

    private void send(String text) {
        TextChannel ch = channel("channel-id");
        if (ch != null) ch.sendMessage(cut(text, 1900)).queue();
    }

    private static String plain(Component c) {
        return c == null ? "" : PlainTextComponentSerializer.plainText().serialize(c);
    }

    // ---------- game -> Discord ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (jda == null || !b("game-to-discord", true)) return;
        send("**" + MarkdownSanitizer.escape(e.getPlayer().getName()) + "** » "
                + MarkdownSanitizer.escape(plain(e.message())));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (jda == null || !b("join-leave", true)) return;
        send("➕ **" + MarkdownSanitizer.escape(e.getPlayer().getName()) + "** joined the server");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (jda == null || !b("join-leave", true)) return;
        send("➖ **" + MarkdownSanitizer.escape(e.getPlayer().getName()) + "** left the server");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (jda == null || !b("deaths", true)) return;
        String m = plain(e.deathMessage());
        if (!m.isBlank()) send("💀 " + MarkdownSanitizer.escape(m));
    }

    // ---------- Discord -> game ----------
    @Override
    public void onMessageReceived(MessageReceivedEvent e) {
        if (!b("discord-to-game", true)) return;
        if (!e.isFromGuild() || e.getAuthor().isBot() || e.isWebhookMessage()) return;
        String chId = s("channel-id");
        if (chId.isBlank() || !e.getChannel().getId().equals(chId)) return;

        String content = e.getMessage().getContentDisplay();
        if (!e.getMessage().getAttachments().isEmpty()) content += " [attachment]";
        if (content.isBlank()) return;

        String name = e.getMember() != null ? e.getMember().getEffectiveName() : e.getAuthor().getName();
        final Component line = Component.text("[Discord] ", NamedTextColor.BLUE)
                .append(Component.text(name, NamedTextColor.WHITE))
                .append(Component.text(" » ", NamedTextColor.GRAY))
                .append(Component.text(content, NamedTextColor.WHITE));
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(line));
    }
}
