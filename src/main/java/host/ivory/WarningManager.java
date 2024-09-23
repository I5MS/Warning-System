package host.ivory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;

import java.awt.*;
import java.util.Date;
import java.util.Map;

public class WarningManager {

    // Create paginated embeds for the warnings
    private List<MessageEmbed> createPaginatedEmbeds(List<Warning> warnings, int pageSize) {
        List<MessageEmbed> pages = new ArrayList<>();

        int totalPages = (int) Math.ceil((double) warnings.size() / pageSize);

        for (int page = 0; page < totalPages; page++) {
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("Warnings - Page " + (page + 1) + "/" + totalPages);
            embedBuilder.setColor(0xFFFF00);  // Yellow color for warnings

            int start = page * pageSize;
            int end = Math.min(start + pageSize, warnings.size());
            List<Warning> warningsOnPage = warnings.subList(start, end);

            StringBuilder description = new StringBuilder();

            for (Warning warning : warningsOnPage) {
                // Convert the warning date to a Unix timestamp (in seconds)
                long timestamp = warning.getDate().getTime() / 1000;

                // Append each warning as part of the description
                description.append("**User ID: <@").append(warning.getUserId()).append(">**\n")
                        .append("Reason: ").append(warning.getReason()).append("\n")
                        .append("Date: <t:").append(timestamp).append(":F>\n")
                        .append("Issued by: <@").append(warning.getIssuerId()).append(">\n\n");
            }

            embedBuilder.setDescription(description.toString());

            pages.add(embedBuilder.build());
        }

        return pages;
    }


    // Send paginated warnings with buttons for navigation
    public void sendPaginatedWarnings(MessageReceivedEvent event, List<Warning> warnings) {
        List<MessageEmbed> pages = createPaginatedEmbeds(warnings, 10);  // 10 warnings per page
        int[] currentPage = {0};  // Mutable int to track the current page

        event.getChannel().sendMessageEmbeds(pages.get(0))
                .setActionRow(
                        Button.primary("prev", "Previous").asDisabled(),  // Disable 'Previous' on the first page
                        Button.primary("next", "Next")
                )
                .queue(message -> message.getJDA().addEventListener(new ListenerAdapter() {
                    @Override
                    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
                        if (!event.getMessageId().equals(message.getId())) return;  // Ignore other messages

                        if (event.getComponentId().equals("next") && currentPage[0] < pages.size() - 1) {
                            currentPage[0]++;
                            event.editMessageEmbeds(pages.get(currentPage[0]))
                                    .setActionRow(
                                            Button.primary("prev", "Previous").withDisabled(currentPage[0] == 0),
                                            Button.primary("next", "Next").withDisabled(currentPage[0] == pages.size() - 1)
                                    ).queue();
                        }

                        if (event.getComponentId().equals("prev") && currentPage[0] > 0) {
                            currentPage[0]--;
                            event.editMessageEmbeds(pages.get(currentPage[0]))
                                    .setActionRow(
                                            Button.primary("prev", "Previous").withDisabled(currentPage[0] == 0),
                                            Button.primary("next", "Next").withDisabled(currentPage[0] == pages.size() - 1)
                                    ).queue();
                        }
                    }
                }));
    }


    // Add a warning to a user
    public static void addWarning(MessageReceivedEvent event, Map<String, List<Warning>> warnings, String userIdRaw, String reason, Config config, String issuerId) {
        // Sanitize the user ID to ensure it only contains the numeric part
        String userId = userIdRaw.replaceAll("[^0-9]", "");  // Remove <@ and >

        // Create a new warning instance with the issuer ID
        Warning warning = new Warning(userId, reason, new Date(), issuerId);
        warnings.computeIfAbsent(userId, k -> new ArrayList<>()).add(warning);

        // Inform the channel that the user was warned
        event.getChannel().sendMessage("Warned user <@" + userId + "> for: " + reason).queue();

        // Retrieve the role ID from the config
        String warnRoleId = config.getWarnRoleId();

        // Assign the role to the user
        event.getGuild().retrieveMemberById(userId).queue(member -> {
            Role warnRole = event.getGuild().getRoleById(warnRoleId);
            if (warnRole == null) {
                event.getChannel().sendMessage("Role with ID " + warnRoleId + " not found!").queue();
            } else {
                member.getGuild().addRoleToMember(member, warnRole).queue(); // Assign the role
            }
        });

        // Log to the log channel
        String logChannelId = config.getLogChannelId();
        TextChannel logChannel = event.getGuild().getTextChannelById(logChannelId);
        if (logChannel != null) {
            // Create a log embed
            EmbedBuilder logEmbed = new EmbedBuilder();
            logEmbed.setColor(Color.YELLOW);
            logEmbed.setTitle("User Warned");
            logEmbed.setDescription("**User:** <@" + userId + ">\n**Reason:** " + reason + "\n**Issued by:** <@" + issuerId + ">\n**Date:** <t:" + (new Date().getTime() / 1000) + ":F>");
            logEmbed.setFooter(event.getGuild().getName(), event.getGuild().getIconUrl());

            // Send the log message to the log channel
            logChannel.sendMessageEmbeds(logEmbed.build()).queue();
        } else {
            event.getChannel().sendMessage("Log channel not found!").queue();
        }

        // Send a DM embed to the warned user
        event.getJDA().retrieveUserById(userId).queue(user -> user.openPrivateChannel().queue(privateChannel -> {
            // Create the embed
            EmbedBuilder dmEmbed = new EmbedBuilder();
            dmEmbed.setColor(Color.YELLOW);  // Set the color to yellow
            dmEmbed.setTitle("You got a warning: ⚠️");
            dmEmbed.setDescription("Please go to the support voice channel.");

            // Set the footer with server name
            dmEmbed.setFooter(event.getGuild().getName(), event.getGuild().getIconUrl());

            // Send the embed to the user's DM
            privateChannel.sendMessageEmbeds(dmEmbed.build()).queue();
        }, error -> {
            // Handle the case where the user cannot be DM'd
            event.getChannel().sendMessage("Could not send a DM to <@" + userId + ">.").queue();
        }));
    }


    public static void removeWarning(MessageReceivedEvent event, Map<String, List<Warning>> warnings, String userIdRaw, Config config, String removerId) {
        // Sanitize the user ID to ensure it only contains the numeric part
        String userId = userIdRaw.replaceAll("[^0-9]", "");  // Remove <@ and >

        // Check if the user has warnings
        if (warnings.containsKey(userId)) {
            List<Warning> userWarnings = warnings.get(userId);

            if (!userWarnings.isEmpty()) {
                // Remove the last warning
                Warning removedWarning = userWarnings.remove(userWarnings.size() - 1);

                // Log message for warning removal
                String logChannelId = config.getLogChannelId();
                TextChannel logChannel = event.getGuild().getTextChannelById(logChannelId);
                if (logChannel != null) {
                    EmbedBuilder logEmbed = new EmbedBuilder();
                    logEmbed.setColor(Color.ORANGE);  // Use orange for warning removal
                    logEmbed.setTitle("Warning Removed");
                    logEmbed.setDescription("**User:** <@" + userId + ">\n**Reason:** " + removedWarning.getReason() +
                            "\n**Removed by:** <@" + removerId + ">\n**Date:** <t:" + (new Date().getTime() / 1000) + ":F>");
                    logEmbed.setFooter(event.getGuild().getName(), event.getGuild().getIconUrl());
                    logChannel.sendMessageEmbeds(logEmbed.build()).queue();
                }

                // If all warnings are removed, remove the role
                if (userWarnings.isEmpty()) {
                    warnings.remove(userId);  // Remove the user from the map

                    // Retrieve the role ID from the config
                    String warnRoleId = config.getWarnRoleId();

                    // Remove the role from the user
                    event.getGuild().retrieveMemberById(userId).queue(member -> {
                        Role warnRole = event.getGuild().getRoleById(warnRoleId);
                        if (warnRole != null) {
                            event.getGuild().removeRoleFromMember(member, warnRole).queue(
                                    success -> event.getChannel().sendMessage("Removed all warnings and role from <@" + userId + ">").queue(),
                                    failure -> event.getChannel().sendMessage("Failed to remove role from <@" + userId + ">").queue()
                            );
                        } else {
                            event.getChannel().sendMessage("Role with ID " + warnRoleId + " not found!").queue();
                        }
                    });
                } else {
                    event.getChannel().sendMessage("Removed the last warning for <@" + userId + ">. They still have " + userWarnings.size() + " warning(s).").queue();
                }
            } else {
                event.getChannel().sendMessage("<@" + userId + "> has no warnings to remove.").queue();
            }
        } else {
            event.getChannel().sendMessage("<@" + userId + "> has no warnings to remove.").queue();
        }
    }



    // Check if a user has warnings
    public static void checkWarnings(MessageReceivedEvent event, Map<String, List<Warning>> warnings, String userId) {
        if (warnings.containsKey(userId)) {
            StringBuilder message = new StringBuilder("User <@" + userId + "> has the following warnings:\n");
            List<Warning> warningList = warnings.get(userId);
            for (Warning warning : warningList) {
                // Convert date to Discord timestamp
                long timestamp = warning.getDate().getTime() / 1000;
                message.append("Reason: ").append(warning.getReason())
                        .append("\nDate: <t:").append(timestamp).append(":F>")
                        .append("\nIssued by: <@").append(warning.getIssuerId()).append(">\n\n"); // Add issuer info
            }
            event.getChannel().sendMessage(message.toString()).queue();
        } else {
            event.getChannel().sendMessage("No warnings found for user " + userId).queue();
        }
    }
}
