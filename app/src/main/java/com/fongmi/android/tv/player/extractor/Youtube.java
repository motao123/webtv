package com.fongmi.android.tv.player.extractor;

import android.net.Uri;
import android.util.Base64;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.NewPipeImpl;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.utils.UrlUtil;

import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubePlaylistExtractor;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubePlaylistLinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

public class Youtube implements Source.Extractor {

    private static final String MPD = "<MPD xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xmlns='urn:mpeg:dash:schema:mpd:2011' xsi:schemaLocation='urn:mpeg:dash:schema:mpd:2011 DASH-MPD.xsd' type='static' mediaPresentationDuration='PT%sS' minBufferTime='PT1.500S' profiles='urn:mpeg:dash:profile:isoff-on-demand:2011'>\n" + "<Period duration='PT%sS' start='PT0S'>\n" + "%s\n" + "%s\n" + "</Period>\n" + "</MPD>";
    private static final String ADAPT = "<AdaptationSet lang='chi'>\n" + "<ContentComponent contentType='%s'/>\n" + "<Representation id='%d' bandwidth='%d' codecs='%s' mimeType='%s' %s>\n" + "<BaseURL>%s</BaseURL>\n" + "<SegmentBase indexRange='%s'>\n" + "<Initialization range='%s'/>\n" + "</SegmentBase>\n" + "</Representation>\n" + "</AdaptationSet>";

    public Youtube() {
        NewPipe.init(NewPipeImpl.get(), Localization.fromLocale(Locale.getDefault()));
    }

    @Override
    public boolean match(Uri uri) {
        String host = UrlUtil.host(uri);
        return host.contains("youtube.com") || host.contains("youtu.be");
    }

    @Override
    public String fetch(String url) throws Exception {
        StreamInfo info = StreamInfo.getInfo(url);
        RefreshEvent.vod(convert(info).trans());
        return isLive(info) ? getLive(info) : getMpd(info);
    }

    private Vod convert(StreamInfo info) {
        try {
            Vod vod = new Vod();
            vod.setName(info.getName());
            vod.setDirector(info.getUploaderName());
            vod.setContent(info.getDescription().getContent());
            vod.setPic(info.getThumbnails().get(info.getThumbnails().size() - 1).getUrl());
            return vod;
        } catch (Exception e) {
            Vod vod = new Vod();
            vod.setName(info.getName());
            vod.setContent(info.getDescription().getContent());
            return vod;
        }
    }

    private boolean isLive(StreamInfo info) {
        return StreamType.LIVE_STREAM.equals(info.getStreamType());
    }

    private String getLive(StreamInfo info) {
        if (!info.getHlsUrl().isEmpty()) {
            return info.getHlsUrl();
        } else if (!info.getDashMpdUrl().isEmpty()) {
            return info.getDashMpdUrl();
        } else {
            return "";
        }
    }

    private String getMpd(StreamInfo info) {
        StringBuilder video = new StringBuilder();
        StringBuilder audio = new StringBuilder();
        List<AudioStream> audioFormats = info.getAudioStreams();
        List<VideoStream> videoFormats = info.getVideoOnlyStreams();
        for (AudioStream format : audioFormats) audio.append(getAdaptationSet(format, getAudioParam(format)));
        for (VideoStream format : videoFormats) video.append(getAdaptationSet(format, getVideoParam(format)));
        String mpd = String.format(Locale.getDefault(), MPD, info.getDuration(), info.getDuration(), video, audio);
        return "data:application/dash+xml;base64," + Base64.encodeToString(mpd.getBytes(), Base64.DEFAULT);
    }

    private String getVideoParam(VideoStream format) {
        return String.format(Locale.getDefault(), "height='%d' width='%d' frameRate='%d' maxPlayoutRate='1' startWithSAP='1'", format.getHeight(), format.getWidth(), format.getFps());
    }

    private String getAudioParam(AudioStream format) {
        return String.format(Locale.getDefault(), "subsegmentAlignment='true' audioSamplingRate='%d'", format.getItagItem().getSampleRate());
    }

    private String getAdaptationSet(VideoStream format, String param) {
        int iTag = format.getItag();
        int bitrate = format.getBitrate();
        String codecs = format.getCodec();
        String mimeType = format.getFormat().getMimeType();
        String url = format.getContent().replace("&", "&amp;");
        String initRange = format.getInitStart() + "-" + format.getInitEnd();
        String indexRange = format.getIndexStart() + "-" + format.getIndexEnd();
        return String.format(Locale.getDefault(), ADAPT, "video", iTag, bitrate, codecs, mimeType, param, url, indexRange, initRange);
    }

    private String getAdaptationSet(AudioStream format, String param) {
        int iTag = format.getItag();
        int bitrate = format.getBitrate();
        String codecs = format.getCodec();
        String mimeType = format.getFormat().getMimeType();
        String url = format.getContent().replace("&", "&amp;");
        String initRange = format.getInitStart() + "-" + format.getInitEnd();
        String indexRange = format.getIndexStart() + "-" + format.getIndexEnd();
        return String.format(Locale.getDefault(), ADAPT, "audio", iTag, bitrate, codecs, mimeType, param, url, indexRange, initRange);
    }

    @Override
    public void stop() {
    }

    @Override
    public void exit() {
    }

    public record Parser(String url) implements Callable<List<Episode>> {

        private static final Pattern PATTERN = Pattern.compile("(youtube\\.com|youtu\\.be).*list=");

        public static boolean match(String url) {
            return PATTERN.matcher(url).find();
        }

        public static Parser get(String url) {
            return new Parser(url);
        }

        @Override
        public List<Episode> call() {
            try {
                ListLinkHandler handler = YoutubePlaylistLinkHandlerFactory.getInstance().fromUrl(url);
                YoutubePlaylistExtractor extractor = new YoutubePlaylistExtractor(ServiceList.YouTube, handler);
                extractor.forceLocalization(NewPipe.getPreferredLocalization());
                extractor.fetchPage();
                List<Episode> episodes = new ArrayList<>();
                add(extractor, episodes, extractor.getInitialPage());
                return episodes;
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }

        private void add(YoutubePlaylistExtractor extractor, List<Episode> episodes, ListExtractor.InfoItemsPage<StreamInfoItem> page) {
            for (StreamInfoItem item : page.getItems()) {
                if (item.getDuration() == -1) continue;
                episodes.add(Episode.create(item.getName(), item.getUrl()));
            }
            if (page.hasNextPage()) {
                try {
                    add(extractor, episodes, extractor.getPage(page.getNextPage()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
