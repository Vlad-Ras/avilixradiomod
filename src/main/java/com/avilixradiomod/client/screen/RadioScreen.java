package com.avilixradiomod.client.screen;

import com.avilixradiomod.blockentity.RadioBlockEntity;
import com.avilixradiomod.client.UrlHistory;
import com.avilixradiomod.client.audio.RadioAudioController;
import com.avilixradiomod.menu.RadioMenu;
import com.avilixradiomod.network.RadioSettingsPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Radio configuration screen (URL + play/stop + volume + playlist controls + URL history panel). */
public final class RadioScreen extends AbstractContainerScreen<RadioMenu> {

    private Button modeButton;

    private EditBox urlBox;
    private Button playStopButton;
    private VolumeSlider volumeSlider;

    private HistoryDropdown historyDropdown;
    private Button historyButton;

    // автоподстановка дефолта при фокусе (только если пусто) — 1 раз за фокус
    private boolean defaultInjected = false;

    // layout cache
    private int mainX, mainW;
    private int historyX, historyW;

    public RadioScreen(RadioMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 190;
    }

    @Override
    protected void init() {
        super.init();

        final RadioBlockEntity radio = menu.getRadio();
        final int left = this.leftPos;
        final int top = this.topPos;

        // ---------- LAYOUT ----------
        // правая панель истории ВНУТРИ 176px
        this.historyW = 64;
        final int gap = 6;

        // основная зона слева
        this.mainX = left + 10;
        this.mainW = this.imageWidth - 20 - gap - historyW;

        // правая зона
        this.historyX = mainX + mainW + gap;

        // Y
        final int yUrl = top + 28;
        final int yVolume = top + 56;
        final int yButtons = top + 80;
        final int yPlayer = top + 104;

        // ---------- URL ----------
        this.urlBox = new EditBox(
                this.font,
                mainX, yUrl,
                mainW, 16,
                Component.translatable("screen.avilixradiomod.url")
        );
        this.urlBox.setMaxLength(8192);

        String initial = radio != null ? safe(radio.getUrl()).trim() : "";
        if (initial.isEmpty()) initial = UrlHistory.getDefaultUrl();
        this.urlBox.setValue(initial);
        this.addRenderableWidget(this.urlBox);

        // сброс подсветки ошибки, если стало валидно
        this.urlBox.setResponder(value -> {
            if (menu.hasUrlError() && isValidStreamUrl(value)) {
                menu.setUrlError(false);
            }
        });

        // ---------- HISTORY DROPDOWN (right panel) ----------
        if (historyDropdown == null) {
            historyDropdown = new HistoryDropdown(0, 0, historyW, url -> {
                urlBox.setValue(url);
                menu.setUrlError(false);
                historyDropdown.close();
            });
            this.addRenderableWidget(historyDropdown);
        } else {
            historyDropdown.close();
        }

        // кнопка ▼ в правой колонке (под заголовком "Ссылка на поток")
        this.historyButton = Button.builder(Component.literal("▼"), b -> {
                    if (historyDropdown.isOpen()) {
                        historyDropdown.close();
                    } else {
                        // открываем СПРАВА, под кнопкой ▼
                        historyDropdown.openBelow(historyX, yUrl + 18, historyW);
                    }
                })
                .bounds(historyX, yUrl, historyW, 16)
                .build();
        this.addRenderableWidget(this.historyButton);

        // ---------- VOLUME ----------
        final int volume = radio != null ? radio.getVolume() : 50;
        this.volumeSlider = new VolumeSlider(mainX, yVolume, mainW, 20, volume);
        this.addRenderableWidget(this.volumeSlider);

        // ---------- PLAY / SAVE (left panel) ----------
        this.playStopButton = Button.builder(getPlayStopLabel(), btn -> {
                    sendSettings(!isPlayingNow());
                    btn.setMessage(getPlayStopLabel());
                })
                .bounds(mainX, yButtons, Math.max(40, mainW / 2 - 2), 20)
                .build();
        this.addRenderableWidget(this.playStopButton);

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.avilixradiomod.save"),
                        btn -> sendSettings(isPlayingNow()))
                .bounds(mainX + Math.max(40, mainW / 2 + 2), yButtons, Math.max(40, mainW - (Math.max(40, mainW / 2 + 2) - 0)), 20)
                .build());

        // ---------- PLAYER CONTROLS ----------
        this.addRenderableWidget(Button.builder(Component.literal("⏮"),
                        b -> RadioAudioController.prevTrack())
                .bounds(mainX, yPlayer, 24, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("⏭"),
                        b -> RadioAudioController.nextTrack())
                .bounds(mainX + 28, yPlayer, 24, 20)
                .build());

        this.modeButton = Button.builder(getModeLabel(), b -> {
                    RadioAudioController.cyclePlayMode();
                    b.setMessage(getModeLabel());
                })
                .bounds(mainX + 56, yPlayer, 70, 20) // чуть шире, чтобы влезал текст
                .build();
        this.addRenderableWidget(this.modeButton);
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        if (urlBox == null) return;

        // автоподстановка дефолта при фокусе (не открываем dropdown автоматически)
        if (urlBox.isFocused()) {
            String v = urlBox.getValue();
            if (!defaultInjected && (v == null || v.trim().isEmpty())) {
                String def = UrlHistory.getDefaultUrl();
                if (def != null && !def.isBlank()) {
                    urlBox.setValue(def);
                    urlBox.setCursorPosition(def.length());
                    urlBox.setHighlightPos(0);
                }
                defaultInjected = true;
            }
        } else {
            defaultInjected = false;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // закрываем dropdown при клике мимо него и мимо кнопки ▼
        if (historyDropdown != null && historyDropdown.isOpen()) {
            boolean overDropdown =
                    mouseX >= historyDropdown.getX() && mouseX < historyDropdown.getX() + historyDropdown.getWidth()
                            && mouseY >= historyDropdown.getY() && mouseY < historyDropdown.getY() + historyDropdown.getHeight();

            boolean overToggle =
                    historyButton != null
                            && mouseX >= historyButton.getX() && mouseX < historyButton.getX() + historyButton.getWidth()
                            && mouseY >= historyButton.getY() && mouseY < historyButton.getY() + historyButton.getHeight();

            if (!overDropdown && !overToggle) {
                historyDropdown.close();
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        sendSettings(isPlayingNow());
        if (historyDropdown != null) historyDropdown.close();
        super.removed();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // background
        g.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xAA000000);

        // title
        g.drawString(this.font, this.title, this.leftPos + 10, this.topPos + 6, 0xFFFFFF);

        // URL label
        int urlColor = menu.hasUrlError() ? 0xFF5555 : 0xFFFFFF;
        g.drawString(this.font,
                Component.translatable("screen.avilixradiomod.url"),
                this.leftPos + 10,
                this.topPos + 18,
                urlColor);

        // HISTORY label (right)
        g.drawString(this.font,
                Component.translatable("screen.avilixradiomod.history"),
                historyX,
                this.topPos + 18,
                0xFFFFFF);

        // Volume label
        g.drawString(this.font,
                Component.translatable("screen.avilixradiomod.volume"),
                this.leftPos + 10,
                this.topPos + 46,
                0xFFFFFF);

        // Error message
        if (menu.hasUrlError()) {
            g.drawString(this.font,
                    Component.translatable("message.avilixradiomod.bad_url_short"),
                    this.leftPos + 10,
                    this.topPos + 18,
                    0xFF5555);
        }

        // Track title
        String track = RadioAudioController.getCurrentTrackTitle();
        if (track != null && !track.isBlank()) {
            g.drawString(this.font,
                    Component.literal(track),
                    this.leftPos + 10,
                    this.topPos + 126,
                    0xDDDDDD);
        }

        // Mode text
        g.drawString(this.font,
                Component.translatable("screen.avilixradiomod.mode_label", getModeLabel()),
                this.leftPos + 10,
                this.topPos + 138,
                0xAAAAAA);

        // Timeline (indicator only)
        int barX = this.leftPos + 10;
        int barY = this.topPos + 150;
        int barW = 156;
        int barH = 6;

        g.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);

        float p = RadioAudioController.getPseudoProgress(); // 0..1
        int fillW = (int) (barW * Math.max(0f, Math.min(1f, p)));
        g.fill(barX, barY, barX + fillW, barY + barH, 0xFFAAAAAA);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // drawn in renderBg
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

    private Component getModeLabel() {
        return switch (RadioAudioController.getPlayMode()) {
            case NORMAL -> Component.translatable("screen.avilixradiomod.mode.normal");
            case SHUFFLE -> Component.translatable("screen.avilixradiomod.mode.shuffle");
            case REPEAT -> Component.translatable("screen.avilixradiomod.mode.repeat");
        };
    }

    private static boolean isValidStreamUrl(String url) {
        if (url == null) return false;
        url = url.trim();
        if (url.isEmpty()) return false;
        return url.startsWith("http://") || url.startsWith("https://");
    }

    private void sendSettings(boolean playing) {
        final RadioBlockEntity radio = menu.getRadio();
        if (radio == null) return;

        String url = this.urlBox != null ? this.urlBox.getValue() : radio.getUrl();
        url = safe(url).trim();

        final int volume = this.volumeSlider != null ? this.volumeSlider.getVolume() : radio.getVolume();

        // forbid play if url invalid
        if (playing && !isValidStreamUrl(url)) {
            menu.setUrlError(true);

            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(
                        Component.translatable("message.avilixradiomod.bad_url"),
                        true
                );
            }

            playing = false;
        } else {
            menu.setUrlError(false);
        }

        // remember only valid urls
        if (isValidStreamUrl(url)) {
            UrlHistory.remember(url);
        }

        PacketDistributor.sendToServer(new RadioSettingsPayload(radio.getBlockPos(), url, playing, volume));

        // local immediate
        radio.setClientSidePreview(url, playing, volume);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
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
