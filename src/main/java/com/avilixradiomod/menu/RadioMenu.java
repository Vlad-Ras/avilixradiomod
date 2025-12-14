package com.avilixradiomod.menu;

import com.avilixradiomod.blockentity.RadioBlockEntity;
import com.avilixradiomod.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public class RadioMenu extends AbstractContainerMenu {

    private final BlockPos pos;
    @Nullable
    private final RadioBlockEntity radio;

    public RadioMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, readRadio(playerInventory.player.level(), buf.readBlockPos()));
    }

    public RadioMenu(int containerId, Inventory playerInventory, @Nullable RadioBlockEntity radio) {
        super(ModMenus.RADIO_MENU.get(), containerId);
        this.radio = radio;
        this.pos = radio != null ? radio.getBlockPos() : playerInventory.player.blockPosition();
    }

    private static @Nullable RadioBlockEntity readRadio(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof RadioBlockEntity r ? r : null;
    }

    public BlockPos getPos() { return pos; }

    @Nullable
    public RadioBlockEntity getRadio() { return radio; }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getCenter()) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // В этом меню нет слотов -> шифт-клик ничего не делает
        return ItemStack.EMPTY;
    }
}
