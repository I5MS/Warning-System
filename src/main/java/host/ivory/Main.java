package host.ivory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

public class Main extends ListenerAdapter {
    public static void main(String[] args) throws IOException {
        // Check and create config if it doesn't exist
        ConfigManager.checkOrCreateConfig();

        // Load the config
        config = ConfigManager.loadConfig();
        if (config == null) {
            System.out.println("Failed to load configuration.");
            return;
        }

        loadConfig();
        loadWarnings();

        JDABuilder jda = JDABuilder.createDefault(config.getToken());
        jda.setActivity(Activity.playing("with warnings"));

        jda.addEventListeners(new Main());
        jda.enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_PRESENCES);

        jda.build();
    }
    private static Config config;
    private static Map<String, List<Warning>> warnings = new HashMap<>();
    // Load configuration from JSON
    private static void loadConfig() throws IOException {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader("config.json")) {
            config = gson.fromJson(reader, Config.class);
        }
    }

    // Load warnings from JSON
    private static void loadWarnings() throws IOException {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader("warnings.json")) {
            Type warningMapType = new TypeToken<HashMap<String, List<Warning>>>() {}.getType();
            warnings = gson.fromJson(reader, warningMapType);
            if (warnings == null) warnings = new HashMap<>();
        } catch (IOException e) {
            warnings = new HashMap<>();
            saveWarnings();
        }
    }

    private boolean hasStaffRole(MessageReceivedEvent event, Config config) {
        List<String> allowedRoles = config.getStaff();
        for (String roleId : allowedRoles) {
            if (event.getMember().getRoles().stream().anyMatch(role -> role.getId().equals(roleId))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasStaffCanRemoveWarnRole(MessageReceivedEvent event, Config config) {
        List<String> allowedRoles = config.getStaffCanRemoveWarn();
        for (String roleId : allowedRoles) {
            if (event.getMember().getRoles().stream().anyMatch(role -> role.getId().equals(roleId))) {
                return true;
            }
        }
        return false;
    }

    // Save warnings to JSON
    private static void saveWarnings() throws IOException {
        Gson gson = new Gson();
        try (FileWriter writer = new FileWriter("warnings.json")) {
            gson.toJson(warnings, writer);
        }
    }

    public List<Warning> getAllWarnings(Map<String, List<Warning>> warnings) {
        List<Warning> allWarnings = new ArrayList<>();
        for (List<Warning> userWarnings : warnings.values()) {
            allWarnings.addAll(userWarnings);
        }
        return allWarnings;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        String[] args = event.getMessage().getContentRaw().split("\\s+");
        String prefix = config.getPrefix();

        if (args[0].startsWith(prefix + "warnings")) {
            if (!hasStaffRole(event, config)) {
                return;
            }

            List<Warning> userWarnings = getAllWarnings(warnings);  // Fetch all warnings from the database
            if (userWarnings.isEmpty()) {
                event.getChannel().sendMessage("No warnings found.").queue();
                return;
            }

            WarningManager warningManager = new WarningManager();
            warningManager.sendPaginatedWarnings(event, userWarnings);  // Send the paginated warning list
        } else if (args[0].equalsIgnoreCase(config.getPrefix() + "warn")) {
            if (!hasStaffRole(event, config)) {
                return;
            }
            if (args.length < 3) {
                event.getChannel().sendMessage("Usage: " + config.getPrefix() + "warn <user> <reason>").queue();
                return;
            }

            String userId = args[1];
            String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            String issuerId = event.getAuthor().getId();

            WarningManager.addWarning(event, warnings, userId, reason, config, issuerId);
            saveData();

        } else if (args[0].equalsIgnoreCase(config.getPrefix() + "removewarn")) {
            if (!hasStaffCanRemoveWarnRole(event, config)) {
                return;
            }

            if (args.length < 2) {
                event.getChannel().sendMessage("Usage: !removewarn <user>").queue();
                return;
            }

            WarningManager.removeWarning(event, warnings, args[1], config, event.getAuthor().getId());
            saveData();

        } else if (args[0].equalsIgnoreCase(config.getPrefix() + "check")) {
            if (!hasStaffRole(event, config)) {
                return;
            }

            if (args.length != 2) {
                event.getChannel().sendMessage("Usage: !check <user>").queue();
                return;
            }

            String userId = args[1];

            if (userId.startsWith("<@") && userId.endsWith(">")) {
                userId = userId.substring(2, userId.length() - 1);  // Remove <@ and >
            }

            WarningManager.checkWarnings(event, warnings, userId);  // Pass the userId correctly
        }
    }

    private void saveData() {
        try {
            saveWarnings();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}