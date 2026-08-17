package com.compactmachinespor.block;

import com.compactmachinespor.Cyumocompactmachinespor;
import com.compactmachinespor.core.AntiCheat;
import com.compactmachinespor.core.Core;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import static com.compactmachinespor.Cyumocompactmachinespor.FACTORY_BLOCK;

public class FactoryBlockItem extends BlockItem {
    public FactoryBlockItem(Properties properties) {
        super(FACTORY_BLOCK.get(), properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        if (player.isShiftKeyDown()) {
            ItemStack itemInHand = player.getItemInHand(usedHand);
            CustomData s = itemInHand.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            if (s.contains("room_code") && !s.copyTag().getString("room_code").isEmpty()) {
                String roomCode = s.copyTag().getString("room_code");
                if (!level.isClientSide) {
                    // Unpacking must "obtain item" before "consuming": any failure step must not swallow the factory block.
                    if (!AntiCheat.checkUnpack(player, roomCode, level, itemInHand, usedHand)) {
                        player.displayClientMessage(Component.translatable("chat.compactmachinespor.unpack_denied").withStyle(ChatFormatting.RED), true);
                        return InteractionResultHolder.consume(itemInHand);
                    }

                    try {
                        ItemStack bound = Core.unpackToItem(roomCode);
                        if (bound.isEmpty()) {
                            Cyumocompactmachinespor.LOGGER.warn("[CMP] unpack failed: bound machine stack is empty for room {}", roomCode);
                            player.displayClientMessage(Component.translatable("chat.compactmachinespor.unpack_failed").withStyle(ChatFormatting.RED), true);
                            return InteractionResultHolder.consume(itemInHand);
                        }

                        boolean added = player.getInventory().add(bound);
                        Cyumocompactmachinespor.LOGGER.debug("[CMP] unpack room {}: Inventory.add(bound) returned {}", roomCode, added);
                        if (!added) {
                            // Inventory is full: drop at player's feet to prevent item loss.
                            player.drop(bound, false);
                            Cyumocompactmachinespor.LOGGER.info("[CMP] unpack: inventory full, dropped bound machine for room {}", roomCode);
                            player.displayClientMessage(Component.translatable("chat.compactmachinespor.unpack_dropped").withStyle(ChatFormatting.GRAY), true);
                        }

                        if (!player.hasInfiniteMaterials()) {
                            itemInHand.shrink(1);
                        }
                        player.inventoryMenu.broadcastChanges();
                    } catch (Throwable t) {
                        // Keep the factory block intact on error to facilitate root cause investigation.
                        Cyumocompactmachinespor.LOGGER.error("[CMP] unpack failed for room {}", roomCode, t);
                        player.displayClientMessage(Component.translatable("chat.compactmachinespor.unpack_failed").withStyle(ChatFormatting.RED), true);
                        return InteractionResultHolder.consume(itemInHand);
                    }
                }
                // Must return the stack currently in the hand slot, not the outdated itemInHand reference that was shrunk to empty.
                // ServerPlayerGameMode.useItem will forcefully setItemInHand(EMPTY) if the returned object isEmpty(),
                // while add() might have placed the bound machine into the newly freed hand slot - using the old empty reference would overwrite it.
                return InteractionResultHolder.consume(player.getItemInHand(usedHand));
            }
        }
        return super.use(level, player, usedHand);
    }
}
