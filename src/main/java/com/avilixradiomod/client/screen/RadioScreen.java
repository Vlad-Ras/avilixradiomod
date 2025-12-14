package com.avilixradiomod.client.screen;

import com.avilixradiomod.blockentity.RadioBlockEntity;
import com.avilixradiomod.menu.RadioMenu;
import com.avilixradiomod.network.RadioSettingsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Simple radio configuration screen (URL + play/stop + volume). */
public final class RadioScreen extends AbstractContainerScreen<RadioMenu> {
    private EditBox urlBox;
    private Button playStopButton;
    private VolumeSlider volumeSlider;

    public RadioScreen(RadioMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        // This menu doesn't show inventory slots.
        this.imageWidth = 176;
        this.imageHeight = 88;
    }

    @Override
    protected void init() {
        super.init();

        final RadioBlockEntity radio = menu.getRadio();
        final int left = this.leftPos;
        final int top = this.topPos;

        this.urlBox = new EditBox(this.font, left + 10, top + 18, 156, 16, Component.translatable("screen.avilixradiomod.url"));
        this.urlBox.setMaxLength(8192);
        this.urlBox.setValue(radio != null ? radio.getUrl() : "");
        this.addRenderableWidget(this.urlBox);

        final int volume = radio != null ? radio.getVolume() : 50;
        this.volumeSlider = new VolumeSlider(left + 10, top + 40, 156, 20, volume);
        this.addRenderableWidget(this.volumeSlider);

        this.playStopButton = Button.builder(getPlayStopLabel(), btn -> {
                    sendSettings(!isPlayingNow());
                    btn.setMessage(getPlayStopLabel());
                })
                .bounds(left + 10, top + 64, 76, 20)
                .build();
        this.addRenderableWidget(this.playStopButton);

        this.addRenderableWidget(Button.builder(Component.translatable("screen.avilixradiomod.save"), btn -> {
                    sendSettings(isPlayingNow());
                })
                .bounds(left + 90, top + 64, 76, 20)
                .build());
    }

    @Override
    public void removed() {
        // Persist URL/volume when the screen closes.
        sendSettings(isPlayingNow());
        super.removed();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // Minimal background panel (no custom texture needed).
        g.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xAA000000);
        g.drawString(this.font, this.title, this.leftPos + 10, this.topPos + 6, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("screen.avilixradiomod.url"), this.leftPos + 10, this.topPos + 18 - 10, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("screen.avilixradiomod.volume"), this.leftPos + 10, this.topPos + 40 - 10, 0xFFFFFF);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Titles are drawn in renderBg.
    }

    private boolean isPlayingNow() {
        final RadioBlockEntity radio = menu.getRadio();
        return radio != null && radio.isPlaying();
    }

    private Component getPlayStopLabel() {
        return isPlayingNow()
                ? Component.translatable("screen.avilixradiomod.stop")
                : Component.translatable("screen.avilixradiomod.play");
    }

    private void sendSettings(boolean playing) {
        final RadioBlockEntity radio = menu.getRadio();
        if (radio == null) return;

        final String url = this.urlBox != null ? this.urlBox.getValue() : radio.getUrl();
        final int volume = this.volumeSlider != null ? this.volumeSlider.getVolume() : radio.getVolume();

        PacketDistributor.sendToServer(new RadioSettingsPayload(radio.getBlockPos(), url, playing, volume));

        // Update local immediately to make UI responsive.
        radio.setClientSidePreview(url, playing, volume);
    }

    private static final class VolumeSlider extends AbstractSliderButton {
        private int volume;

        private VolumeSlider(int x, int y, int width, int height, int volume) {
            super(x, y, width, height, Component.empty(), volume / 100.0D);
            this.volume = clamp(volume);
            this.updateMessage();
        }

        int getVolume() {
            return volume;
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(volume + "%"));
        }

        @Override
        protected void applyValue() {
            this.volume = clamp((int) Math.round(this.value * 100.0D));
        }

        private static int clamp(int v) {
            return Math.max(0, Math.min(100, v));
        }
    }
}
