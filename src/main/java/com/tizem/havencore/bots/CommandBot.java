package com.tizem.havencore.bots;

import com.tizem.havencore.HavenCore;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Bot 4: run console commands from one Discord channel. */
public final class CommandBot extends DiscordBot {

    private final MonitorBot monitor;

    public CommandBot(HavenCore plugin, MonitorBot monitor) {
        super(plugin, "commands");
        this.monitor = monitor;
    }

    @Override
    protected Collection<GatewayIntent> intents() {
        return List.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT);
    }

    private boolean allowed(Member m) {
        if (m == null) return false;
        if (m.hasPermission(Permission.ADMINISTRATOR)) return true;
        List<String> roles = list("allowed-role-ids");
        return m.getRoles().stream().anyMatch(r -> roles.contains(r.getId()));
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent e) {
        if (!e.isFromGuild() || e.getAuthor().isBot() || e.isWebhookMessage()) return;
        String chId = s("channel-id");
        if (chId.isBlank() || !e.getChannel().getId().equals(chId)) return;

        String text = e.getMessage().getContentRaw().trim();
        String prefix = s("prefix");
        if (!prefix.isEmpty()) {
            if (!text.startsWith(prefix)) return;
            text = text.substring(prefix.length()).trim();
        }
        if (text.startsWith("/")) text = text.substring(1);
        if (text.isEmpty()) return;

        if (!allowed(e.getMember())) {
            e.getMessage().reply("❌ You are not allowed to run commands.").mentionRepliedUser(false).queue();
            return;
        }

        final String command = text;
        String first = command.split("\\s+")[0].toLowerCase();
        if (plugin.getConfig().getStringList("discord.bots.commands.blocked-commands").contains(first)) {
            e.getMessage().reply("❌ `" + first + "` is blocked from Discord.").mentionRepliedUser(false).queue();
            return;
        }

        final String who = e.getAuthor().getName();
        final CopyOnWriteArrayList<String> out = new CopyOnWriteArrayList<>();
        final boolean[] ok = {false};

        monitor.log("**" + who + "** ran `" + command + "` from Discord");

        Bukkit.getScheduler().runTask(plugin, () -> {
            CommandSender sender = Bukkit.createCommandSender(
                    c -> out.add(PlainTextComponentSerializer.plainText().serialize(c)));
            try {
                ok[0] = Bukkit.dispatchCommand(sender, command);
            } catch (Exception ex) {
                out.add("Error: " + ex.getMessage());
            }
            // give async/delayed output a moment, then answer
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                String result = String.join("\n", out).replaceAll("§.", "").trim();
                String reply;
                if (!result.isEmpty()) reply = "```\n" + cut(result, 1850) + "\n```";
                else if (ok[0]) reply = "✅ Ran `" + command + "`";
                else reply = "❌ Unknown or failed command.";
                e.getMessage().reply(reply).mentionRepliedUser(false).queue();
            }, 10L);
        });
    }
}
