package com.github.frcsty.tabfilter;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public final class TabFilter extends JavaPlugin implements Listener, CommandExecutor
{

    private final Map<Command, String> commands = new HashMap<>();

    private LuckPerms  api;
    private CommandMap commandMap = null;

    @Override
    public void onEnable()
    {
        getServer().getPluginManager().registerEvents(this, this);

        final RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null)
        {
            api = provider.getProvider();
        }

        getCommand("completer").setExecutor(this);

        new BukkitRunnable()
        {

            @Override
            public void run()
            {
                try
                {
                    Field f = SimplePluginManager.class.getDeclaredField("commandMap");
                    f.setAccessible(true);

                    commandMap = (CommandMap) f.get(Bukkit.getPluginManager());
                    f.setAccessible(false);
                }
                catch (NoSuchFieldException | IllegalAccessException ex)
                {
                    getLogger().log(Level.WARNING, "Failed to access command map!");
                }

                final Plugin[] plugins = Bukkit.getPluginManager().getPlugins();

                for (Plugin plugin : plugins)
                {
                    if (plugin.getName().equalsIgnoreCase("Essentials"))
                    {
                        final Map<String, Map<String, Object>> ymlMap = plugin.getDescription().getCommands();

                        for (String command : ymlMap.keySet())
                        {
                            final Command cmd = commandMap.getCommand(command);

                            if (cmd != null)
                            {
                                final String permission = String.valueOf(ymlMap.get(command).get("permission"));

                                System.out.println(cmd.getName() + " | " + permission  + " | " + cmd.getPermission() + " (" + cmd.getAliases() + ")");

                                commands.put(cmd, permission);
                            }
                        }
                    }
                }

                for (String command : commandMap.getKnownCommands().keySet())
                {
                    final Command cmd = commandMap.getCommand(command);

                    if (cmd != null)
                    {
                        final String permission = cmd.getPermission();

                        if (permission != null)
                        {
                            if (!command.contains(":")) commands.put(cmd, cmd.getPermission());
                        }
                    }
                }
            }
        }.runTaskLater(this, 40);
    }

    @Override
    public void onDisable()
    {
        commands.clear();
    }

    @EventHandler
    public void onTabCompletion(PlayerCommandSendEvent event)
    {
        if (commandMap == null)
        {
            return;
        }

        final Player player = event.getPlayer();
        final User user = api.getUserManager().getUser(player.getUniqueId());
        if (user == null)
        {
            return;
        }

        final Collection<Node> userNodes = user.getNodes();
        final List<String> userUnavailableCommands = new ArrayList<>();

        for (Command command : commands.keySet())
        {
            final String permission = commands.get(command);

            if (permission == null)
            {
                continue;
            }

            final PermissionNode permissionNode = PermissionNode.builder(permission).build();
            final Optional<Node> node = userNodes.stream().filter(perm -> NodeType.PERMISSION.matches(permissionNode)).findAny();

            if (!node.isPresent())
            {
                userUnavailableCommands.add(command.getName());
            }
        }

        event.getCommands().removeAll(userUnavailableCommands);
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args)
    {
        for (Command cmd : commands.keySet())
        {
            final String permission = commands.get(cmd);

            System.out.println(cmd.getName() + " (-) " + permission);
        }
        return true;
    }

}
