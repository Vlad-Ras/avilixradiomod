package com.avilixradiomod.client.audio;

import com.avilixradiomod.blockentity.RadioBlockEntity;
import com.avilixradiomod.blockentity.SpeakerBlockEntity;
import com.avilixradiomod.config.ModConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;
import java.util.Random;

public final class RadioAudioController {

    private static int tickCounter = 0;

    // URL, который введён в радио (может быть плейлист)
    private static String currentSourceUrl = "";

    // Текущий реально проигрываемый URL (из плейлиста или тот же)
    private static String currentPlayingUrl = "";

    private static float smoothVolume = 0.0f;

    private static List<PlaylistEntry> playlist = List.of();
    private static int playlistIndex = 0;

    private static final Mp3StreamPlayer PLAYER = new Mp3StreamPlayer();

    // ---- режимы ----
    public enum PlayMode { NORMAL, SHUFFLE, REPEAT }
    private static PlayMode playMode = PlayMode.NORMAL;
    private static final Random RANDOM = new Random();

    // ---- псевдо-таймлайн ----
    private static long trackStartMs = 0L;

    private RadioAudioController() {}

    public static void onClientTick(final ClientTickEvent.Post event) {
        // ✅ читаем конфиг каждый тик (дёшево, зато точно работает)
        final int scanEveryTicks = ModConfigs.COMMON.scanEveryTicks.get();
        final int maxHearDistance = ModConfigs.COMMON.maxHearDistance.get();
        final int scanRadius = maxHearDistance; // логично: сканируем как слышим
        final float smoothing = ModConfigs.COMMON.smoothing.get().floatValue();
        final float stopThreshold = ModConfigs.COMMON.stopThreshold.get().floatValue();

        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        final ClientLevel level = mc.level;

        if (player == null || level == null) {
            stopIfPlaying();
            return;
        }

        if (++tickCounter % scanEveryTicks != 0) return;

        final Candidate best = findBestCandidate(level, player.blockPosition(), level.dimension(), scanRadius);
        if (best == null) {
            smoothVolume = smoothVolume + (0.0f - smoothVolume) * smoothing;
            PLAYER.setVolume((int) smoothVolume);
            if (smoothVolume <= stopThreshold) stopIfPlaying();
            return;
        }

        final String sourceUrl = best.url == null ? "" : best.url.trim();
        if (sourceUrl.isEmpty()) {
            stopIfPlaying();
            return;
        }

        final double distanceBlocks = Math.sqrt(best.distSqr);
        final int target = computeTargetVolume(best.volume, distanceBlocks, maxHearDistance);
        smoothVolume = smoothVolume + (target - smoothVolume) * smoothing;

        // сменился источник (URL/playlist) -> перезагрузка и старт
        if (!sourceUrl.equals(currentSourceUrl)) {
            currentSourceUrl = sourceUrl;
            reloadAndPlayFirst((int) smoothVolume);
        } else {
            PLAYER.setVolume((int) smoothVolume);
        }

        // если трек умер -> следующий
        if (PLAYER.consumeFailed()) {
            playNext((int) smoothVolume);
        }

        if (smoothVolume <= stopThreshold && target == 0) {
            stopIfPlaying();
        }
    }

    // ------------------ Публичные методы для GUI ------------------

    public static void nextTrack() {
        playNext((int) smoothVolume);
    }

    public static void prevTrack() {
        if (playlist.isEmpty()) return;

        if (playMode == PlayMode.SHUFFLE) {
            playlistIndex = RANDOM.nextInt(playlist.size());
        } else {
            playlistIndex = Math.max(0, playlistIndex - 1);
        }

        startIndex(playlistIndex, (int) smoothVolume);
    }

    public static void cyclePlayMode() {
        playMode = switch (playMode) {
            case NORMAL -> PlayMode.SHUFFLE;
            case SHUFFLE -> PlayMode.REPEAT;
            case REPEAT -> PlayMode.NORMAL;
        };
    }

    public static PlayMode getPlayMode() {
        return playMode;
    }

    public static String getCurrentTrackTitle() {
        if (playlist.isEmpty()) return "";
        PlaylistEntry e = playlist.get(playlistIndex);
        if (e.title() != null && !e.title().isBlank()) return e.title();
        return "Track " + (playlistIndex + 1) + "/" + playlist.size();
    }

    /** 0..1 псевдо-прогресс (для живых стримов длины нет) */
    public static float getPseudoProgress() {
        if (trackStartMs == 0L) return 0f;
        long elapsed = System.currentTimeMillis() - trackStartMs;
        return (elapsed % 180_000L) / 180_000f; // 180 секунд "круг"
    }

    // ------------------ Внутренняя логика ------------------

    private static void reloadAndPlayFirst(int vol) {
        playlist = List.of();
        playlistIndex = 0;
        currentPlayingUrl = "";
        trackStartMs = 0L;

        try {
            playlist = PlaylistLoader.load(currentSourceUrl);
        } catch (Throwable ignored) {
            playlist = List.of();
        }

        if (playlist.isEmpty()) {
            stopIfPlaying();
            return;
        }

        startIndex(0, vol);
    }

    private static void startIndex(int idx, int vol) {
        if (playlist.isEmpty()) {
            stopIfPlaying();
            return;
        }
        if (idx < 0 || idx >= playlist.size()) {
            stopIfPlaying();
            return;
        }

        playlistIndex = idx;
        currentPlayingUrl = playlist.get(playlistIndex).url();
        trackStartMs = System.currentTimeMillis();
        PLAYER.play(currentPlayingUrl, vol);
    }

    private static void playNext(int vol) {
        if (playlist.isEmpty()) {
            stopIfPlaying();
            return;
        }

        switch (playMode) {
            case REPEAT -> {
                // остаёмся на текущем
            }
            case SHUFFLE -> {
                if (playlist.size() > 1) {
                    int next;
                    do {
                        next = RANDOM.nextInt(playlist.size());
                    } while (next == playlistIndex);
                    playlistIndex = next;
                }
            }
            case NORMAL -> {
                playlistIndex++;
                if (playlistIndex >= playlist.size()) {
                    stopIfPlaying();
                    return;
                }
            }
        }

        startIndex(playlistIndex, vol);
    }

    private static void stopIfPlaying() {
        currentSourceUrl = "";
        currentPlayingUrl = "";
        playlist = List.of();
        playlistIndex = 0;
        smoothVolume = 0.0f;
        trackStartMs = 0L;
        PLAYER.stop();
    }

    private static Candidate findBestCandidate(ClientLevel level, BlockPos center, ResourceKey<Level> dim, int scanRadius) {
        Candidate best = null;
        final int r = scanRadius;

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

    private static int computeTargetVolume(int baseVolume, double distanceBlocks, int maxHearDistance) {
        double d = Math.max(0.0, distanceBlocks);
        double max = Math.max(1.0, maxHearDistance);
        if (d >= max) return 0;

        double x = 1.0 - (d / max);
        double att = x * x;

        return (int) Math.round(baseVolume * att);
    }
}
