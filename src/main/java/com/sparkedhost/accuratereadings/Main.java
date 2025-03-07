package com.sparkedhost.accuratereadings;

import com.sparkedhost.accuratereadings.commands.*;
import com.sparkedhost.accuratereadings.commands.control.ControlBaseCommand;
import com.sparkedhost.accuratereadings.config.Settings;
import com.sparkedhost.accuratereadings.managers.PlaceholderAPIManager;
import com.sparkedhost.accuratereadings.managers.PterodactylManager;
import com.sparkedhost.accuratereadings.managers.TaskManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.stream.Stream;

public class Main extends JavaPlugin {
    @Getter
    private static Main instance;

    @Getter
    private Settings settings;

    String panelUrl;
    String apiKey;
    String serverId;
    boolean useWebsocket;
    long updateFrequency;

    @Getter
    private boolean isPAPIPresent;

    @Getter
    public PterodactylManager pteroAPI;

    @Getter
    private TaskManager taskManager;

    @Override
    public void onEnable() {
        instance = this;

        // Set logger
        Utils.setLogger(getLogger());

        saveDefaultConfig();
        settings = new Settings();

        // This plugin's expected config version
        final int EXPECTED_CONFIG_VERSION = 2;

        // Config version stored on config file
        final int configVersion = getConfig().getInt("version", 0);

        if (configVersion == 0) {
            log(Level.SEVERE, "Config version is not defined! If you're using your old config file " +
                    "(AccurateReadings prior to v1.2.0), it is strongly recommended that you remove it and let the " +
                    "plugin generate a new one. Much of the config structure has been changed in v1.2.0. As a " +
                    "precautionary measure, the plugin will disable itself.");
            disableItself();
        } else {
            if (configVersion < EXPECTED_CONFIG_VERSION) {
                log(Level.WARNING, String.format("Config version does not match! Expected %s, got %s. It's very likely the " +
                        "configuration file is out of date. Continue at your own risk.", EXPECTED_CONFIG_VERSION, configVersion));
                log(Level.WARNING, "Resuming startup in 2 seconds...");

                try {
                    Bukkit.getServer().wait(2000);
                } catch (InterruptedException exception) {
                    log(Level.WARNING, "Timeout interrupted.");
                } catch (IllegalMonitorStateException exception) {
                    log(Level.WARNING, "I tried very hard to pause the main server thread, but so far I cannot make it work.");
                }
            }

            if (configVersion > EXPECTED_CONFIG_VERSION) {
                log(Level.SEVERE, String.format("Config version is NEWER (Expected %s, got %s)! This will cause problems, so the plugin is " +
                        "going to disable itself.", EXPECTED_CONFIG_VERSION, configVersion));
                disableItself();
            }
        }

        taskManager = new TaskManager();

        getSettings().loadValues();

        if (!isPterodactyl()) {
            log(Level.SEVERE, "Pterodactyl check failed! Are you sure this server is running in Pterodactyl?");
        }

        panelUrl = getSettings().pterodactyl_panelUrl;
        apiKey = getSettings().pterodactyl_apiKey;
        serverId = getSettings().pterodactyl_serverId;
        useWebsocket = getSettings().pterodactyl_useWebsocket;
        updateFrequency = getSettings().pterodactyl_updateFrequency;

        log(Level.INFO, "AccurateReadings is loading...");

        if (!isConfigValid()) {
            // Actual logging output is handled in the Main#isConfigValid() method.
            disableItself();
            return;
        }

        log(Level.INFO, "Attempting connection to '" + panelUrl + "'...");

        // Initialize Pterodactyl User API interface
        pteroAPI = new PterodactylManager();
        pteroAPI.initializeClient();

        // Register PlaceholderAPI placeholders and plugin commands
        registerPlaceholders();
        getCommand("stats").setExecutor(new StatsCommand());
        getCommand("arc").setExecutor(new ControlBaseCommand());
    }

    public void onDisable() {
        log(Level.INFO, "Plugin is disabling.");

        if (pteroAPI != null && pteroAPI.getResourceUsageManager() != null) {
            if (pteroAPI.getResourceUsageManager().isRunning())
                pteroAPI.getResourceUsageManager().stopListener();
        }

        log(Level.INFO, "Plugin disabled, have a nice day.");
    }

    /**
     * Shorthand function to log a message with the appropriate prefix.
     * @param level Level of logging
     * @param msg Message to log
     */
    public void log(Level level, String msg) {
        getLogger().log(level, msg);
    }

    /**
     * Shorthand function to log a message with the appropriate prefix.
     * @param level Level of logging
     * @param msg Message to log
     * @param throwable The throwable to log
     */
    public void log(Level level, String msg, Throwable throwable) {
        getLogger().log(level, msg, throwable);
    }

    /**
     * Disables the plugin, only used when an error occurs during startup.
     */
    public void disableItself() {
        log(Level.SEVERE, "The plugin will now disable itself.");
        getServer().getPluginManager().disablePlugin(this);
    }

    /**
     * Registers AccurateReadings' placeholders into PlaceholderAPI.
     */
    private void registerPlaceholders() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        boolean placeholderApiSuccess = new PlaceholderAPIManager().register();
        isPAPIPresent = true;

        if (!placeholderApiSuccess) {
            log(Level.WARNING, "PlaceholderAPI was found on your server, but we were unable to register our placeholders.");
            return;
        }

        log(Level.INFO, "Successfully hooked into PlaceholderAPI and registered our placeholders.");
    }

    /**
     * Reloads the configuration, and reinitializes the API if needed.
     */
    public void reload() {
        reloadConfig();
        getSettings().loadValues();
        log(Level.INFO, "Configuration file has been reloaded.");

        if (hasPteroConfigChanged()) {
            log(Level.INFO, "Pterodactyl configuration has changed, logging back in...");
            pteroAPI.getResourceUsageManager().stopListener();
            pteroAPI.initializeClient();
        }
    }

    /**
     * Validates the configuration file.
     * @return Validation result in boolean
     */
    private boolean isConfigValid() {
        if (panelUrl.isEmpty()) {
            log(Level.SEVERE, "You have not provided a panel URL in your config.yml.");
            return false;
        }

        if (!Utils.validateURL(panelUrl)) {
            log(Level.SEVERE, "You have provided an invalid panel URL in your config.yml.");
            return false;
        }

        if (apiKey.isEmpty() || apiKey.equalsIgnoreCase("CHANGETHIS")) {
            log(Level.SEVERE, "You have not provided an API key in your config.yml. Read how to get the API key on the GitHub page.");
            return false;
        }

        // All checks passed
        return true;
    }

    /**
     * Returns whether the Pterodactyl-specific configuration differs from what's currently stored in memory.
     * @return Result in boolean
     */
    private boolean hasPteroConfigChanged() {
        return !(getSettings().pterodactyl_panelUrl.equals(panelUrl) &&
                getSettings().pterodactyl_apiKey.equals(apiKey) &&
                getSettings().pterodactyl_serverId.equals(serverId) &&
                getSettings().pterodactyl_useWebsocket == useWebsocket &&
                getSettings().pterodactyl_updateFrequency == updateFrequency);
    }

    private boolean isPterodactyl() {
        return Files.exists(Paths.get("/entrypoint.sh")) && isRunningInsideDocker();
    }

    /**
     * Check if current process is running within a Docker container.
     * <p>
     * Source: <a href="https://stackoverflow.com/a/52581380">StackOverflow</a>
     * </p>
     * @return Whether the process is in Docker or not
     */
    private boolean isRunningInsideDocker() {
        try (Stream < String > stream =
                     Files.lines(Paths.get("/proc/1/cgroup"))) {
            return stream.anyMatch(line -> line.contains("/docker"));
        } catch (IOException exception) {
            return false;
        }
    }
}
