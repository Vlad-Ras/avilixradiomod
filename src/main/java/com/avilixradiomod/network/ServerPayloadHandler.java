package com.avilixradiomod.network;

import com.avilixradiomod.blockentity.RadioBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ServerPayloadHandler {
    private ServerPayloadHandler() {}

    public static void handleRadioSettings(final RadioSettingsPayload payload, final IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        final Level level = player.level();
        // Basic anti-abuse: only allow changing radios reasonably close to the player.
        if (player.distanceToSqr(payload.pos().getCenter()) > 64.0) {
            return;
        }

        final BlockEntity be = level.getBlockEntity(payload.pos());
        if (be instanceof RadioBlockEntity radio) {
            final String url = sanitizeUrl(payload.url());
            final int volume = clamp(payload.volume(), 0, 100);
            radio.setSettings(url, payload.playing(), volume);
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String sanitizeUrl(String url) {
        if (url == null) return "";
        url = url.trim();
        if (url.length() > 8192) url = url.substring(0, 8192);
        // Allow only http(s) to avoid local file access.
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            return "";
        }
        return url;
    }
}
