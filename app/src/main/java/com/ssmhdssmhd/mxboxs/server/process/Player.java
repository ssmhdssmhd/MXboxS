package com.ssmhdssmhd.mxboxs.server.process;

import static fi.iki.elonen.NanoHTTPD.MIME_HTML;
import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

import com.ssmhdssmhd.mxboxs.server.Nano;
import com.ssmhdssmhd.mxboxs.server.impl.Process;
import com.github.catvod.utils.Asset;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class Player implements Process {

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith("/player");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        try {
            Map<String, String> params = session.getParms();
            String video = params.getOrDefault("url", "");
            String sub = params.getOrDefault("sub", "");
            String ua = params.getOrDefault("ua", "");
            String referer = params.getOrDefault("referer", "");
            String html = String.format(Asset.read("player.html"), video, sub, ua, referer);
            return newFixedLengthResponse(Status.OK, MIME_HTML, html);
        } catch (Exception e) {
            return Nano.error(e.getMessage());
        }
    }
}
