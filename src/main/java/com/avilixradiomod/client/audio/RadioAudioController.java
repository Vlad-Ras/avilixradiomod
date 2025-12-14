package com.avilixradiomod.client.audio;

import com.avilixradiomod.blockentity.RadioBlockEntity;
import com.avilixradiomod.blockentity.SpeakerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client-only: polls nearby speakers/radios and plays the closest active stream.
 * Adds smooth distance-based volume fade.
 */
public final class RadioAudioController {

    private static final int SCAN_EVERY_TICKS = 10;

    /** How far we scan block entities around the player (in blocks). */
    private static final int MAX_HEAR_DISTANCE = 30;
    private static final int SCAN_RADIUS = MAX_HEAR_DISTANCE;

    /** Volume smoothing (0..1). Higher = faster response. */
    private static final float SMOOTHING = 0.20f;

    /** If target is 0 and we fall below this, stop decoding. */
    private static final float STOP_THRESHOLD = 0.5f;

    private static int tickCounter = 0;

    private static String currentUrl = "";
    private static float smoothVolume = 0.0f; // 0..100

    private static final Mp3StreamPlayer PLAYER = new Mp3StreamPlayer();

    private RadioAudioController() {}

    public static void onClientTick(final ClientTickEvent.Post event) {
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        final ClientLevel level = mc.level;

        if (player == null || level == null) {
            stopIfPlaying();
            return;
        }

        if (++tickCounter % SCAN_EVERY_TICKS != 0) return;

        final Candidate best = findBestCandidate(level, player.blockPosition(), level.dimension());
        if (best == null) {
            // No valid speaker/radio found -> fade out quickly then stop
            smoothVolume = smoothVolume + (0.0f - smoothVolume) * SMOOTHING;
            PLAYER.setVolume((int) smoothVolume);
            if (smoothVolume <= STOP_THRESHOLD) stopIfPlaying();
            return;
        }

        // distSqr -> distance in blocks
        final double distanceBlocks = Math.sqrt(best.distSqr);

        // 1) Compute target volume with distance attenuation
        final int target = computeTargetVolume(best.volume, distanceBlocks);

        // 2) Smooth volume change
        smoothVolume = smoothVolume + (target - smoothVolume) * SMOOTHING;

        // 3) Apply to player
        if (!best.url.equals(currentUrl)) {
            currentUrl = best.url;
            PLAYER.play(currentUrl, (int) smoothVolume);
        } else {
            PLAYER.setVolume((int) smoothVolume);
        }

        // 4) Stop stream when essentially silent and target is 0
        if (smoothVolume <= STOP_THRESHOLD && target == 0) {
            stopIfPlaying();
        }
    }

    private static void stopIfPlaying() {
        currentUrl = "";
        smoothVolume = 0.0f;
        PLAYER.stop();
    }

    private static Candidate findBestCandidate(ClientLevel level, BlockPos center, ResourceKey<Level> dim) {
        Candidate best = null;
        final int r = SCAN_RADIUS;

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    final BlockPos pos = center.offset(dx, dy, dz);
                    final BlockEntity be = level.getBlockEntity(pos);
                    if (be == null) continue;

                    if (be instanceof SpeakerBlockEntity speaker) {
                        RadioBlockEntity radio = resolveLinkedRadio(level, speaker, dim);
                        if (radio == null) continue;
                        if (!radio.isPlaying()) continue;

                        final String url = radio.getUrl();
                        if (url == null || url.isBlank()) continue;

                        final int vol = clamp(radio.getVolume());
                        final double distSqr = center.distSqr(pos);

                        best = pickBetter(best, new Candidate(url, vol, distSqr));

                    } else if (be instanceof RadioBlockEntity radio) {
                        if (!radio.isPlaying()) continue;

                        final String url = radio.getUrl();
                        if (url == null || url.isBlank()) continue;

                        final int vol = clamp(radio.getVolume());
                        final double distSqr = center.distSqr(pos);

                        best = pickBetter(best, new Candidate(url, vol, distSqr));
                    }
                }
            }
        }

        return best;
    }

    private static RadioBlockEntity resolveLinkedRadio(ClientLevel level, SpeakerBlockEntity speaker, ResourceKey<Level> currentDim) {
        final BlockPos pos = speaker.getRadioPos();
        final var dim = speaker.getRadioDim();
        if (pos == null || dim == null) return null;
        if (!dim.equals(currentDim.location())) return null;

        final BlockEntity be = level.getBlockEntity(pos);
        return be instanceof RadioBlockEntity radio ? radio : null;
    }

    private static Candidate pickBetter(Candidate best, Candidate next) {
        if (best == null) return next;
        return next.distSqr < best.distSqr ? next : best;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private record Candidate(String url, int volume, double distSqr) {}

    /**
     * Distance-based attenuation.
     * baseVolume is 0..100, returns 0..100.
     */
    private static int computeTargetVolume(int baseVolume, double distanceBlocks) {
        double d = Math.max(0.0, distanceBlocks);
        double max = MAX_HEAR_DISTANCE;

        if (d >= max) return 0;

        // 1.0 near speaker -> 0.0 at max distance
        double x = 1.0 - (d / max);

        // smoother curve for speech
        double att = x * x;

        return (int) Math.round(baseVolume * att);
    }
}
