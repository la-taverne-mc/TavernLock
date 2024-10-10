package fr.lataverne.actions;

import fr.lataverne.tavernlock.TavernLock;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;

public class ChestEvent implements Listener {

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        Block block = event.getBlock();

        if (isValidSign(block)) {
            WallSign signData = (WallSign) block.getBlockData();
            Block attachedBlock = block.getRelative(signData.getFacing().getOppositeFace());

            if (isStorageBlock(attachedBlock)) {
                if (event.getLine(0).equalsIgnoreCase(TavernLock.getTAVERNLOCK().getConfig().getString("messages.detected_text_on_sign"))) {
                    UUID playerUUID = event.getPlayer().getUniqueId();

                    if (isDoubleChest(attachedBlock)) {
                        lockDoubleChest(attachedBlock, playerUUID);
                    } else {
                        saveChestData(attachedBlock, playerUUID);
                    }

                    event.setLine(1, "§e§l" + event.getPlayer().getName());
                    sendPlayerMessage(event.getPlayer(), "messages.chest_locked");
                }
            }
        }
    }

    @EventHandler
    public void onChestOpen(InventoryOpenEvent event) {
        Player player = (Player) event.getPlayer();

        if (player.hasPermission("tavernlock.bypass")) {
            return;
        }

        Optional.of(event.getInventory().getHolder())
                .filter(holder -> holder instanceof Chest || holder instanceof ShulkerBox)
                .map(holder -> (Block) ((org.bukkit.block.Container) holder).getBlock())
                .ifPresent(block -> {

                    if (isChestLocked(block) && !isPlayerOwner(player, block)) {
                        event.setCancelled(true);
                        sendPlayerMessage(player, "messages.chest_locked_others");
                        return;
                    }

                    if (isDoubleChest(block)) {
                        Optional<Block> otherHalf = getOtherHalf(block);
                        otherHalf.ifPresent(halfBlock -> {
                            if (isChestLocked(halfBlock) && !isPlayerOwner(player, halfBlock)) {
                                event.setCancelled(true);
                                sendPlayerMessage(player, "messages.chest_locked_others");
                            }
                        });
                    }
                });
    }


    @EventHandler
    public void onSignBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (player.hasPermission("tavernlock.bypass")) {
            return;
        }

        if (isValidSign(block)) {
            WallSign signData = (WallSign) block.getBlockData();
            Block attachedBlock = block.getRelative(signData.getFacing().getOppositeFace());

            if (isStorageBlock(attachedBlock)) {
                if (isChestLocked(attachedBlock)) {
                    if (isPlayerOwner(player, attachedBlock)) {
                        if (player.isSneaking()) {
                            if (isDoubleChest(attachedBlock)) {
                                unlockDoubleChest(attachedBlock);
                            } else {
                                removeChestData(attachedBlock);
                            }

                            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, SoundCategory.RECORDS, 100, 2);
                            sendPlayerMessage(player, "messages.chest_unlock");
                        } else {
                            event.setCancelled(true);
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, SoundCategory.RECORDS, 100, 0);
                            sendPlayerMessage(player, "messages.shift_leftclick_for_break");
                        }
                    } else {
                        event.setCancelled(true);
                        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, SoundCategory.RECORDS, 100, 0);
                        sendPlayerMessage(player, "messages.no_permission");
                    }
                }
            }
        } else if (isStorageBlock(block)) {
            if (isChestLocked(block)) {
                event.setCancelled(true);
                event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_CHEST_LOCKED, SoundCategory.RECORDS, 100, 0);
                sendPlayerMessage(event.getPlayer(), "messages.no_permission");
            }
        }
    }


    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();

        if (player.hasPermission("tavernlock.bypass")) {
            return;
        }

        if (block != null && isValidSign(block)) {
            WallSign signData = (WallSign) block.getBlockData();
            Block attachedBlock = block.getRelative(signData.getFacing().getOppositeFace());

            if (isStorageBlock(attachedBlock) && isChestLocked(attachedBlock)) {
                if (!isPlayerOwner(player, attachedBlock)) {
                    event.setCancelled(true);  // Annuler l'interaction
                    player.sendMessage("§cVous ne pouvez pas interagir avec ce panneau, ce coffre est privé !");
                    player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, SoundCategory.BLOCKS, 1, 1);
                    return;
                }
            }
        }

        if (block != null && isStorageBlock(block)) {
            if (isChestLocked(block) && !isPlayerOwner(player, block)) {
                event.setCancelled(true);
                sendPlayerMessage(player, "messages.chest_locked_others");
                return;
            }

            if (isDoubleChest(block)) {
                Optional<Block> otherHalf = getOtherHalf(block);
                otherHalf.ifPresent(halfBlock -> {
                    if (isChestLocked(halfBlock) && !isPlayerOwner(player, halfBlock)) {
                        event.setCancelled(true);
                        sendPlayerMessage(player, "messages.chest_locked_others");
                    }
                });
            }
        }
    }


    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        Iterator<Block> blockIterator = event.blockList().iterator();
        while (blockIterator.hasNext()) {
            Block block = blockIterator.next();

            if ((isStorageBlock(block) && isChestLocked(block)) || isProtectedSign(block)) {
                blockIterator.remove();
            }
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        Block block = event.getBlock();

        if ((isStorageBlock(block) && isChestLocked(block)) || isProtectedSign(block)) {
            event.setCancelled(true);
        }
    }

    /*
     Fonction pour envoyer un message au joueur depuis config.yml
     */
    private void sendPlayerMessage(Player player, String configPath) {
        Optional.of(TavernLock.getTAVERNLOCK().getConfig().getString("messages.prefix") + TavernLock.getTAVERNLOCK().getConfig().getString(configPath))
                .map(this::formatMessage)
                .ifPresent(player::sendMessage);
    }

    /*
    Fonction pour formater messages avec les couleurs
     */
    private String formatMessage(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /*
    Verrouiller double coffre
     */
    private void lockDoubleChest(Block block, UUID playerUUID) {
        saveChestData(block, playerUUID);

        Optional<Block> otherHalf = getOtherHalf(block);
        otherHalf.ifPresent(otherBlock -> saveChestData(otherBlock, playerUUID));
    }

    /*
    Déverrouiller double coffre
     */
    private void unlockDoubleChest(Block block) {
        removeChestData(block);

        Optional<Block> otherHalf = getOtherHalf(block);
        otherHalf.ifPresent(this::removeChestData);
    }

    /*
    Obtenir autre moitié double coffre
     */
    private Optional<Block> getOtherHalf(Block block) {
        if (block.getState() instanceof Chest) {
            Chest chest = (Chest) block.getState();
            org.bukkit.block.data.type.Chest chestData = (org.bukkit.block.data.type.Chest) block.getBlockData();

            BlockFace chestFacing = chestData.getFacing();
            Block otherHalf = null;

            switch (chestData.getType()) {
                case LEFT:
                    otherHalf = switch (chestFacing) {
                        case NORTH -> block.getRelative(BlockFace.EAST);
                        case SOUTH -> block.getRelative(BlockFace.WEST);
                        case EAST -> block.getRelative(BlockFace.SOUTH);
                        case WEST -> block.getRelative(BlockFace.NORTH);
                        default -> null;
                    };
                    break;

                case RIGHT:
                    otherHalf = switch (chestFacing) {
                        case NORTH -> block.getRelative(BlockFace.WEST);
                        case SOUTH -> block.getRelative(BlockFace.EAST);
                        case EAST -> block.getRelative(BlockFace.NORTH);
                        case WEST -> block.getRelative(BlockFace.SOUTH);
                        default -> null;
                    };
                    break;
                default:
                    return Optional.empty();
            }

            return Optional.ofNullable(otherHalf);
        }
        return Optional.empty();
    }

    /*
    Vérifier si un coffre est un double coffre
     */
    private boolean isDoubleChest(Block block) {
        if (block.getState() instanceof Chest) {
            org.bukkit.block.data.type.Chest chestData = (org.bukkit.block.data.type.Chest) block.getBlockData();
            return chestData.getType() != org.bukkit.block.data.type.Chest.Type.SINGLE;
        }
        return false;
    }

    /*
    Vérifier si le coffre est verrouillé dans data.yml
     */
    private boolean isChestLocked(Block chestBlock) {
        String key = generateBlockKey(chestBlock);
        return TavernLock.getDataConfig().contains(key + ".owner");
    }

    /*
    Vérifier si le joueur est propriétaire du coffre
     */
    private boolean isPlayerOwner(Player player, Block chestBlock) {
        String key = generateBlockKey(chestBlock);
        String ownerUUID = TavernLock.getDataConfig().getString(key + ".owner");
        return ownerUUID != null && ownerUUID.equals(player.getUniqueId().toString());
    }

    /*
    Sauvegarder les données du coffre dans data.yml
     */
    private void saveChestData(Block chestBlock, UUID playerUUID) {
        String key = generateBlockKey(chestBlock);

        TavernLock.getDataConfig().set(key + ".world", chestBlock.getWorld().getName());
        TavernLock.getDataConfig().set(key + ".x", chestBlock.getX());
        TavernLock.getDataConfig().set(key + ".y", chestBlock.getY());
        TavernLock.getDataConfig().set(key + ".z", chestBlock.getZ());
        TavernLock.getDataConfig().set(key + ".owner", playerUUID.toString());

        TavernLock.saveDataFile();
    }

    /*
    Supprimer les données du coffre dans data.yml
     */
    private void removeChestData(Block chestBlock) {
        String key = generateBlockKey(chestBlock);

        TavernLock.getDataConfig().set(key, null);

        TavernLock.saveDataFile();
    }

    /*
    Générer clé unique pour chaque bloc basé sur ses coordonnées (ex: world_0_100_5)
     */
    private String generateBlockKey(Block block) {
        return block.getWorld().getName() + "_" + block.getX() + "_" + block.getY() + "_" + block.getZ();
    }

    /*
    Vérifier si le bloc est valide pour supporter le panneau
     */
    private boolean isStorageBlock(Block block) {
        return block.getType() == Material.CHEST ||
                block.getType() == Material.TRAPPED_CHEST ||
                block.getType().name().endsWith("SHULKER_BOX");
    }

    /*
     Vérifier si le panneau est attaché à un coffre ou une Shulker Box verrouillé
     */
    private boolean isProtectedSign(Block block) {
        if (isValidSign(block)) {
            WallSign signData = (WallSign) block.getBlockData();
            Block attachedBlock = block.getRelative(signData.getFacing().getOppositeFace());
            return isStorageBlock(attachedBlock) && isChestLocked(attachedBlock);
        }
        return false;
    }

    /*
    Vérifier si le panneau est valide (pas un panneau tout seul mais sur un coffre)
     */
    private boolean isValidSign(Block block) {
        return block.getType().name().endsWith("WALL_SIGN");
    }

}
