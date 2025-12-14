package com.avilixradiomod.blockentity;

import com.avilixradiomod.menu.RadioMenu;
import com.avilixradiomod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class RadioBlockEntity extends BlockEntity implements MenuProvider {

    private static final String TAG_URL = "Url";
    private static final String TAG_PLAYING = "Playing";
    private static final String TAG_VOLUME = "Volume";

    private String url = "";
    private boolean playing = false;
    private int volume = 100;

    public RadioBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RADIO.get(), pos, state);
    }

    public String getUrl() {
        return url;
    }

    public boolean isPlaying() {
        return playing;
    }

    public int getVolume() {
        return volume;
    }

    /**
     * Client-only preview (GUI responsiveness)
     */
    public void setClientSidePreview(String url, boolean playing, int volume) {
        if (this.level == null || !this.level.isClientSide) return;

        if (url == null) url = "";
        url = url.trim(); // ✅ УБИРАЕМ ПРОБЕЛЫ

        this.url = url;
        this.playing = playing;
        this.volume = Math.max(0, Math.min(100, volume));
        this.setChanged();
    }

    /**
     * Server-authoritative settings
     */
    public void setSettings(String url, boolean playing, int volume) {
        if (url == null) url = "";
        url = url.trim(); // ✅ УБИРАЕМ ПРОБЕЛЫ

        this.url = url;
        this.playing = playing;
        this.volume = Math.max(0, Math.min(100, volume));

        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("screen.avilixradiomod.radio");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new RadioMenu(containerId, inventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_URL, url);
        tag.putBoolean(TAG_PLAYING, playing);
        tag.putInt(TAG_VOLUME, volume);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        url = tag.getString(TAG_URL);
        playing = tag.getBoolean(TAG_PLAYING);
        volume = tag.contains(TAG_VOLUME) ? tag.getInt(TAG_VOLUME) : 100;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
