package host.ivory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class ConfigManager {

    private static final String CONFIG_PATH = "config.json";

    // Method to check if config exists, and create it if not
    public static void checkOrCreateConfig() {
        File configFile = new File(CONFIG_PATH);
        if (!configFile.exists()) {
            // If the config file does not exist, create it with default values
            saveDefaultConfig();
        }
    }

    // Method to save the default config file
    public static void saveDefaultConfig() {
        Config defaultConfig = new Config(
                "!",  // Default prefix
                "INSERT_TOKEN_HERE",  // Placeholder for token
                "LOG-CHANNEL-ID",  // Placeholder for log channel ID
                "ROLE-ID",  // Placeholder for warn role ID
                Arrays.asList("ROLE-1", "ROLE-2"),  // Staff roles
                Arrays.asList("ROLE-1", "ROLE-2")   // Roles that can remove warnings
        );

        // Convert the Config object to JSON using Gson
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(defaultConfig);

        try (FileWriter writer = new FileWriter(CONFIG_PATH)) {
            writer.write(json);
            System.out.println("Config file created with default values.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to load the config file
    public static Config loadConfig() {
        try {
            String json = new String(Files.readAllBytes(Paths.get(CONFIG_PATH)));
            Gson gson = new Gson();
            return gson.fromJson(json, Config.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
