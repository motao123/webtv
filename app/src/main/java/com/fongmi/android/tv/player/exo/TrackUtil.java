package com.fongmi.android.tv.player.exo;

import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;

import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.player.PlayerHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrackUtil {

    public static int count(Tracks tracks, int type) {
        return tracks.getGroups().stream().filter(trackGroup -> trackGroup.getType() == type).mapToInt(trackGroup -> trackGroup.length).sum();
    }

    public static void reset(Player player) {
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverrides().build());
    }

    private static TrackInfo find(Player player, Track track) {
        if (track.getFormat() == null) return null;
        Tracks currentTracks = player.getCurrentTracks();
        for (Tracks.Group trackGroup : currentTracks.getGroups()) {
            if (trackGroup.getType() != track.getType()) continue;
            for (int i = 0; i < trackGroup.length; i++) {
                Format format = trackGroup.getTrackFormat(i);
                if (track.getFormat().equals(PlayerHelper.describeFormat(format))) {
                    return new TrackInfo(trackGroup, i);
                }
            }
        }
        return null;
    }

    public static void setTrackSelection(Player player, List<Track> tracks) {
        Map<Integer, TrackGroup> mediaGroupMapByType = new HashMap<>();
        Map<Integer, Integer> selectedIndexMapByType = new HashMap<>();
        for (Track track : tracks) {
            TrackInfo info = find(player, track);
            if (info == null) continue;
            int type = info.trackGroup.getType();
            mediaGroupMapByType.put(type, info.trackGroup.getMediaTrackGroup());
            if (track.isSelected()) selectedIndexMapByType.put(type, info.trackIndex);
        }
        TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters().buildUpon();
        mediaGroupMapByType.forEach((type, mediaGroup) -> {
            Integer selectedIndex = selectedIndexMapByType.get(type);
            List<Integer> indices = selectedIndex != null ? List.of(selectedIndex) : List.of();
            builder.setOverrideForType(new TrackSelectionOverride(mediaGroup, indices));
        });
        player.setTrackSelectionParameters(builder.build());
    }

    private record TrackInfo(Tracks.Group trackGroup, int trackIndex) {
    }
}
