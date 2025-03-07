package com.sparkedhost.accuratereadings;

import lombok.Getter;
import lombok.Setter;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class Utils {
    @Getter
    @Setter
    public static Logger logger;

    /**
     * Shorthand for ChatColor#translateAlternateColorCodes() because it's a pain to type.
     * @param input String input
     * @return Translated string
     */

    public static String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    /**
     * Validates a given URL.
     * @param url URL to validate
     * @return Validation status in boolean
     */

    public static boolean validateURL(String url)
    {
        try {
            new URL(url).toURI();
            return true;
        }
        catch (URISyntaxException | MalformedURLException e) {
            return false;
        }
    }

    /**
     * Replace PlaceholderAPI placeholders if the plugin is present.
     * @param player Player, used by PlaceholderAPI to parse placeholders. Can be null if sender is console.
     * @param input Text to parse
     * @return Parsed text, hopefully
     */

    public static String parsePlaceholdersIfPresent(@Nullable Player player, String input) {
        if (Main.getInstance().isPAPIPresent()) {
            if (player == null) {
                final Pattern regexPattern = Pattern.compile("(%[A-Za-z-_]+%)", Pattern.MULTILINE);
                return regexPattern.matcher(input).replaceAll("&7(PAPI placeholders not available on console)");
            }

            return PlaceholderAPI.setPlaceholders(player, input);
        }

        return input;
    }

    /**
     * Checks if a sender has a given permission, always returning true if the sender is an instance of
     * {@link org.bukkit.command.ConsoleCommandSender}.
     * @param sender Command sender to check against
     * @param permission Permission node string
     * @return Result, always true if sender is console
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean hasPermission(CommandSender sender, String permission) {
        if (!(sender instanceof Player))
            return true;

        return sender.hasPermission(permission);
    }
}
