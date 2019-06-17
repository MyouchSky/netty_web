package com.inetty.web.handler;

import com.inetty.web.common.utils.DateUtil;
import com.inetty.web.log.NelLog;
import com.inetty.web.manager.NelResourceManager;
import com.inetty.web.url.UrlMatch;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

import static io.netty.handler.codec.http.HttpHeaderNames.*;

@Slf4j
abstract public class NelBaseController {
    //rest请求http头
    private static String reqHeaderDate = "x-hs-date";
    protected FullHttpRequest fullHttpRequest;
    protected FullHttpResponse fullHttpResponse;
    protected String resData;
    protected NelLog nelLog;
    protected StringBuilder logBuf = new StringBuilder();
    public UrlMatch match;
    protected NelResourceManager nelResourceManager;
    protected HttpResponseStatus resStatus = HttpResponseStatus.OK;

    abstract public void handleHttpMsg(ChannelHandlerContext ctx, FullHttpRequest request);

    public void sendResponse(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (this.nelLog.getInfo() == null) {
            this.nelLog.setInfo(this.resData);
        }
        log.info(this.nelLog.info);
        ctx.writeAndFlush(response(request)).addListener(ChannelFutureListener.CLOSE);
    }

    protected FullHttpResponse response(FullHttpRequest request) throws Exception {
        FullHttpResponse response = null;
        ByteBuf content = null;
        if (resData != null) {
            content = Unpooled.wrappedBuffer(resData.getBytes(CharsetUtil.UTF_8));
        } else {
            content = Unpooled.EMPTY_BUFFER;
        }
        response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, resStatus, content);
        response.headers().set(CONTENT_TYPE,"application/json;charset=UTF-8");
        if (resData == null) {
            response.headers().set(CONTENT_LENGTH, 0);
        } else {
            response.headers().set(CONTENT_LENGTH, content.array().length);
        }
        if (response != null) {
            response.headers().set(CONNECTION, HttpHeaders.Values.CLOSE);
            response.headers().set(reqHeaderDate, DateUtil.getGmtDateStr(new Date()));
        }
        if (request != null && request.headers().get("CSeq") != null) {
            response.headers().set("CSeq", request.headers().get("CSeq"));
        }
        return response;
    }

    public void setMatch(UrlMatch match) {
        this.match = match;
    }

    public String getParm(String p) {
        if (match != null) {
            return match.get(p);
        } else {
            return null;
        }
    }

    public void initResourceManager(NelResourceManager nelResourceManager) {
        this.nelResourceManager = nelResourceManager;
    }

    public void initLog(NelLog logBean) {
        this.nelLog = logBean;
    }
}
