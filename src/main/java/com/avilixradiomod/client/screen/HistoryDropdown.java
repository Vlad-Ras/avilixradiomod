package com.avilixradiomod.client.screen;

import com.avilixradiomod.client.UrlHistory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Scrollable dropdown for URL history.
 * Opens only by button ▼.
 */
public final class HistoryDropdown extends AbstractWidget {

    private static final int ITEM_HEIGHT = 12;
    private static final int MAX_VISIBLE = 6;

    private final Consumer<String> onSelect;
    private List<String> items = List.of();
    private int scroll = 0;
    private boolean open = false;

    public HistoryDropdown(int x, int y, int width, Consumer<String> onSelect) {
        super(x, y, width, ITEM_HEIGHT * MAX_VISIBLE, Component.empty());
        this.onSelect = onSelect;
        this.visible = false;
        this.active = false;
    }

    // ----------------- public API -----------------

    public void openBelow(int x, int y, int width) {
        this.items = UrlHistory.getAll();
        this.scroll = 0;

        this.setX(x);
        this.setY(y);
        this.setWidth(width);
        this.setHeight(Math.min(items.size(), MAX_VISIBLE) * ITEM_HEIGHT);

        this.open = true;
        this.visible = true;
        this.active = true;
    }

    public void close() {
        this.open = false;
        this.visible = false;
        this.active = false;
    }

    public boolean isOpen() {
        return open;
    }

    // ----------------- rendering -----------------

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!visible) return;

        // background
        g.fill(getX(), getY(), getX() + width, getY() + height, 0xFF202020);
        g.fill(getX(), getY(), getX() + width, getY() + 1, 0xFF000000);

        int max = Math.min(items.size() - scroll, MAX_VISIBLE);

        for (int i = 0; i < max; i++) {
            int idx = scroll + i;
            int rowY = getY() + i * ITEM_HEIGHT;

            boolean hover =
                    mouseX >= getX() && mouseX < getX() + width
                            && mouseY >= rowY && mouseY < rowY + ITEM_HEIGHT;

            if (hover) {
                g.fill(getX(), rowY, getX() + width, rowY + ITEM_HEIGHT, 0xFF444444);
            }

            String text = items.get(idx);
            g.drawString(
                    Minecraft.getInstance().font,
                    text,
                    getX() + 4,
                    rowY + 2,
                    0xFFFFFF
            );
        }
    }

    // ----------------- mouse -----------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open || !visible) return false;

        if (mouseX < getX() || mouseX >= getX() + width) return false;
        if (mouseY < getY() || mouseY >= getY() + height) return false;

        int index = (int) ((mouseY - getY()) / ITEM_HEIGHT) + scroll;
        if (index >= 0 && index < items.size()) {
            onSelect.accept(items.get(index));
            close();
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (!open || !visible) return false;

        // scroll only when mouse is over dropdown
        if (mouseX < getX() || mouseX >= getX() + width) return false;
        if (mouseY < getY() || mouseY >= getY() + height) return false;

        int maxScroll = Math.max(0, items.size() - MAX_VISIBLE);

        if (deltaY < 0) scroll = Math.min(scroll + 1, maxScroll);
        if (deltaY > 0) scroll = Math.max(scroll - 1, 0);

        return true;
    }

    // Required by AbstractWidget (1.21+)
    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        // dropdown is mouse-only; no narration
    }
}
