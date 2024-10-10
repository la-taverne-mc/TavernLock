package fr.lataverne.commands;

import fr.lataverne.tavernlock.TavernLock;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class GroupsCommands implements CommandExecutor {

    private final TavernLock plugin;
    private final FileConfiguration groupsConfig;

    public GroupsCommands(TavernLock plugin, FileConfiguration groupsConfig) {
        this.plugin = plugin;
        this.groupsConfig = groupsConfig;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cSeul les joueurs peuvent exécuter cette commande :/");
            return true;
        }

        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();

        if (command.getName().equalsIgnoreCase("tavernlock")) {
            if (args.length < 1) {
                player.sendMessage("§r");
                player.sendMessage("§6/tavernlock §eadd §7<joueur>");
                player.sendMessage("§6/tavernlock §ekick §7<joueur>");
                player.sendMessage("§6/tavernlock §egrouplist");
                player.sendMessage("§r");
                return true;
            }

            String action = args[0];

            if (args.length == 1) {
                if (action.equalsIgnoreCase("grouplist")) {
                    List<String> groupMembers = groupsConfig.getStringList("players." + playerUUID + ".group_members");

                    if (groupMembers.isEmpty()) {
                        player.sendMessage("§eVotre groupe est vide.");
                    } else {
                        player.sendMessage("§eMembres de votre groupe :");
                        player.sendMessage("§r");
                        groupMembers.stream()
                                .map(UUID::fromString)
                                .map(Bukkit::getOfflinePlayer)
                                .map(offlinePlayer -> offlinePlayer.getName() != null ? offlinePlayer.getName() : "Joueur inconnu")
                                .forEach(memberName -> player.sendMessage("§7 - " + memberName));
                    }

                } else {
                    player.sendMessage("§r");
                    player.sendMessage("§6/tavernlock §eadd §7<joueur>");
                    player.sendMessage("§6/tavernlock §ekick §7<joueur>");
                    player.sendMessage("§6/tavernlock §egrouplist");
                    player.sendMessage("§r");
                }
                return true;
            }

            if (args.length == 2) {
                Player targetPlayer = Bukkit.getPlayer(args[1]);
                if (targetPlayer == null) {
                    player.sendMessage("§cJoueur introuvable.");
                    return true;
                }

                UUID targetUUID = targetPlayer.getUniqueId();

                if (action.equalsIgnoreCase("add")) {
                    addPlayerToGroup(playerUUID, targetUUID);
                    player.sendMessage("§a" + targetPlayer.getName() + " a été ajouté à votre groupe.");
                } else if (action.equalsIgnoreCase("kick")) {
                    removePlayerFromGroup(playerUUID, targetUUID);
                    player.sendMessage("§c" + targetPlayer.getName() + " a été retiré de votre groupe.");
                } else {
                    player.sendMessage("§r");
                    player.sendMessage("§6/tavernlock §eadd §7<joueur>");
                    player.sendMessage("§6/tavernlock §ekick §7<joueur>");
                    player.sendMessage("§6/tavernlock §egrouplist");
                    player.sendMessage("§r");
                }
                return true;
            }
        }
        return true;
    }

    private void addPlayerToGroup(UUID ownerUUID, UUID memberUUID) {
        List<String> groupMembers = groupsConfig.getStringList("players." + ownerUUID + ".group_members");
        if (!groupMembers.contains(memberUUID.toString())) {
            groupMembers.add(memberUUID.toString());
            groupsConfig.set("players." + ownerUUID + ".group_members", groupMembers);
            plugin.saveGroupsFile();
        }
    }

    private void removePlayerFromGroup(UUID ownerUUID, UUID memberUUID) {
        List<String> groupMembers = groupsConfig.getStringList("players." + ownerUUID + ".group_members");
        if (groupMembers.contains(memberUUID.toString())) {
            groupMembers.remove(memberUUID.toString());
            groupsConfig.set("players." + ownerUUID + ".group_members", groupMembers);
            plugin.saveGroupsFile();
        }
    }
}
