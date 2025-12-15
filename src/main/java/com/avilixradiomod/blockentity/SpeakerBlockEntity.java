package com.avilixradiomod.blockentity;

import com.avilixradiomod.block.SpeakerBlock;
import com.avilixradiomod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import net.minecraft.network.protocol.game.ClientGamePacketListener;

public class SpeakerBlockEntity extends BlockEntity {

    private static final String TAG_LINKED = "Linked";
    private static final String TAG_X = "X";
    private static final String TAG_Y = "Y";
    private static final String TAG_Z = "Z";
    private static final String TAG_DIM = "Dim";

    @Nullable
    private BlockPos radioPos;
    @Nullable
    private ResourceLocation radioDim;

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPEAKER.get(), pos, state);
    }

    @Nullable
    public BlockPos getRadioPos() {
        return radioPos;
    }

    @Nullable
    public ResourceLocation getRadioDim() {
        return radioDim;
    }

    public void setRadioLink(@Nullable BlockPos pos, @Nullable ResourceLocation dim) {
        this.radioPos = pos;
        this.radioDim = dim;
        setChanged();

        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
            // ^ вместо 3, чтобы точно ушло клиенту
        }
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        if (radioPos != null && radioDim != null) {
            CompoundTag linked = new CompoundTag();
            linked.putInt(TAG_X, radioPos.getX());
            linked.putInt(TAG_Y, radioPos.getY());
            linked.putInt(TAG_Z, radioPos.getZ());
            linked.putString(TAG_DIM, radioDim.toString());
            tag.put(TAG_LINKED, linked);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        if (tag.contains(TAG_LINKED)) {
            CompoundTag linked = tag.getCompound(TAG_LINKED);
            radioPos = new BlockPos(
                    linked.getInt(TAG_X),
                    linked.getInt(TAG_Y),
                    linked.getInt(TAG_Z)
            );
            String dim = linked.getString(TAG_DIM);
            radioDim = dim.isEmpty() ? null : ResourceLocation.tryParse(dim);
        } else {
            radioPos = null;
            radioDim = null;
        }
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

    // ------------------------------------------------------------------
    // SERVER TICK — включает / выключает анимацию
    // ------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpeakerBlockEntity speaker) {
        if ((level.getGameTime() % 10L) != 0L) return; // раз в 10 тиков

        boolean shouldBePlaying = false;

        if (speaker.radioPos != null
                && speaker.radioDim != null
                && speaker.radioDim.equals(level.dimension().location())) {

            BlockEntity be = level.getBlockEntity(speaker.radioPos);
            if (be instanceof RadioBlockEntity radio) {
                shouldBePlaying =
                        radio.isPlaying()
                                && radio.getUrl() != null
                                && !radio.getUrl().isBlank();
            }
        }

        boolean isPlaying = state.getValue(SpeakerBlock.PLAYING);

        if (isPlaying != shouldBePlaying) {
            level.setBlock(
                    pos,
                    state.setValue(SpeakerBlock.PLAYING, shouldBePlaying),
                    3
            );
        }
    }
}
