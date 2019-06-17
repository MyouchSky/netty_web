package com.inetty.web.handler;

import com.inetty.web.common.WSConstants;
import com.inetty.web.common.utils.HttpBusinessResponseUtil;
import com.inetty.web.log.NelLog;
import com.inetty.web.manager.NelResourceManager;
import com.inetty.web.payload.PayloadManager;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.net.URI;

@Slf4j
public class DefaultAdsRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static NelProcessorFactory factory = new NelProcessorFactory();
    private NelResourceManager nelResourceManager;

    public DefaultAdsRequestHandler() {
    }

    public DefaultAdsRequestHandler(NelResourceManager nelResourceManager) {
        this.nelResourceManager = nelResourceManager;
    }

    public void badResponse(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        HttpResponseStatus status = HttpResponseStatus.BAD_REQUEST;
        FullHttpResponse res = HttpBusinessResponseUtil.makeResponse(request, "bad request", status);
        ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
        try {
            String requestPath = null;
            Channel channel = ctx.channel();
            String msgId = (String) channel.attr(WSConstants.MSGID).get();
//            long beginTime = ((Long) channel.attr(WSConstants.BEGINTIME).get()).longValue();
            long processBegin = 0;
            long processEnd = 0;
            long processCost = 0;
            long cost = 0;
            URI uri = new URI(msg.getUri());
            requestPath = uri.getPath();
            NelBaseController handler = factory.create(requestPath, msg.getMethod());
            if (handler != null) {
                String clientIP = msg.headers().get("X-Forwarded-For");
                if (clientIP == null || clientIP.length() == 0) {
                    InetSocketAddress insocket = (InetSocketAddress) ctx.channel().remoteAddress();
                    if (insocket != null) {
                        clientIP = insocket.getAddress().getHostAddress();
                    }
                    NelLog logBean = new NelLog();
                    logBean.setAction(requestPath);
                    logBean.setMid(msgId);
                    logBean.setIp(clientIP);
                    ctx.channel().attr(WSConstants.LOG).set(logBean);
                    //初始化
                    handler.initLog(logBean);
                    handler.initResourceManager(nelResourceManager);
                    processBegin = System.currentTimeMillis();
                    //处理请求
                    handler.handleHttpMsg(ctx, msg);
                    processEnd = System.currentTimeMillis();
                    processCost = processEnd - processBegin;
                    logBean.setCost(processCost);
                    //返回请求信息
                    handler.sendResponse(ctx, msg);
                } else {
                    log.info("[Nel-Common] There is no precoess with request > " + requestPath + " , msgId = " + msgId);
                    badResponse(ctx, msg);
                }
//                if (processEnd > 0) {
//                    cost = processEnd - beginTime;
//                } else {
//                    cost = System.currentTimeMillis() - beginTime;
//                }
                if (cost > 500L) {
                    log.info("[Nel-Common] Process request cost too long:" + requestPath + " , totalCpst = " + cost + " ,processCost = " + processCost + " , msgId = " + msgId);
                }
            }
        } catch (Exception e) {
            try {
                badResponse(ctx, msg);
            } catch (Exception ex) {
                ctx.channel().close();
            }
            log.error("[Nel-Common] Exception =》",e);
        } finally {
            //业务流程完成以后，payload可以被减一
            PayloadManager.decreasePayload();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("channelInactive ...");
        ctx.close();
    }
}

