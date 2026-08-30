package com.ssmhdssmhd.mxboxs.api;

import androidx.annotation.NonNull;

import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.api.config.LiveConfig;
import com.ssmhdssmhd.mxboxs.api.parser.EpgParser;
import com.ssmhdssmhd.mxboxs.api.parser.LiveParser;
import com.ssmhdssmhd.mxboxs.bean.Channel;
import com.ssmhdssmhd.mxboxs.bean.Epg;
import com.ssmhdssmhd.mxboxs.bean.EpgData;
import com.ssmhdssmhd.mxboxs.bean.Group;
import com.ssmhdssmhd.mxboxs.bean.Live;
import com.ssmhdssmhd.mxboxs.bean.Result;
import com.ssmhdssmhd.mxboxs.player.extractor.Source;
import com.ssmhdssmhd.mxboxs.utils.Formatters;
import com.github.catvod.net.OkHttp;

import java.time.LocalDate;
import java.time.ZoneId;

public class LiveApi {

    public static void parse(@NonNull Live item) throws Exception {
        LiveParser.start(item.recent());
        item.getGroups().removeIf(Group::isEmpty);
        if (item.getGroups().isEmpty() || item.getGroups().get(0).isKeep()) return;
        item.getGroups().add(0, Group.create(R.string.keep));
        LiveConfig.get().applyKeepsToGroups(item.getGroups());
    }

    public static boolean parseXml(@NonNull Live item) {
        return item.getEpgXml().stream().map(url -> startXml(item, url)).reduce(false, Boolean::logicalOr);
    }

    @NonNull
    public static Epg getEpg(@NonNull Channel item, @NonNull ZoneId zoneId) {
        String today = LocalDate.now(zoneId).format(Formatters.DATE);
        for (int offset : new int[]{-1, 0, 1}) fetchEpgDay(item, zoneId, offset);
        return item.getDataList().stream().filter(epg -> epg.equal(today)).findFirst().orElseGet(Epg::new).selected();
    }

    @NonNull
    public static Result getUrl(@NonNull Channel item) throws Exception {
        Source.get().stop();
        Result result = item.result();
        result.setUrl(Source.get().fetch(result));
        return result;
    }

    @NonNull
    public static Result getUrl(@NonNull Channel item, @NonNull EpgData data) throws Exception {
        Result result = getUrl(item);
        result.setUrl(item.getCatchup().format(result.getRealUrl(), data));
        if (item.isRtsp()) result.getHeader().put("rtsp_range", data.getRange());
        return result;
    }

    private static boolean startXml(Live item, String url) {
        try {
            EpgParser.start(item, url);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void fetchEpgDay(@NonNull Channel item, @NonNull ZoneId zoneId, int offset) {
        String date = LocalDate.now(zoneId).plusDays(offset).format(Formatters.DATE);
        String url = item.getEpg().replace("{date}", date);
        boolean need = url.startsWith("http") && item.getDataList().stream().noneMatch(epg -> epg.equal(date));
        if (need) item.setData(Epg.objectFrom(OkHttp.string(url), item.getTvgId(), zoneId));
    }
}
