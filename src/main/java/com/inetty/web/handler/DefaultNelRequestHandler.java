package com.inetty.web.handler;

import com.inetty.web.common.WSConstants;
import com.inetty.web.common.enums.ResponseEnum;
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
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.net.URI;

@Slf4j
public class DefaultNelRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static NelProcessorFactory factory = new NelProcessorFactory();
    private NelResourceManager nelResourceManager;

    public DefaultNelRequestHandler(NelResourceManager nelResourceManager) {
        this.nelResourceManager = nelResourceManager;
    }

    public void returnReponse(ChannelHandlerContext ctx, FullHttpRequest request, ResponseEnum responseEnum) throws Exception {
        FullHttpResponse res = HttpBusinessResponseUtil.makeResponse(request, responseEnum.getDesc(), responseEnum.getStatus());
        ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
        try {
            String requestPath = null;
            Channel channel = ctx.channel();
            String msgId = channel.attr(WSConstants.MSGID).get();
            long beginTime = channel.attr(WSConstants.BEGINTIME).get();
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
                    returnReponse(ctx, msg, ResponseEnum.BAD_REQUEST);
                }
                if (processEnd > 0) {
                    cost = processEnd - beginTime;
                } else {
                    cost = System.currentTimeMillis() - beginTime;
                }
                if (cost > 500L) {
                    log.info("[Nel-Common] Process request cost too long:" + requestPath + " , totalCpst = " + cost + " ,processCost = " + processCost + " , msgId = " + msgId);
                }
            }else{
                returnReponse(ctx, msg, ResponseEnum.NOT_FOUND);
            }
        } catch (Exception e) {
            returnReponse(ctx, msg, ResponseEnum.BAD_REQUEST);
            log.error("[Nel-Common] Exception =》",e);
        } finally {
            //业务流程完成以后，payload可以被减一
            PayloadManager.decreasePayload();
        }
    }
}

