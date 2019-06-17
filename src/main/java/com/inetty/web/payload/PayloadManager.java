package com.inetty.web.payload;

import java.util.concurrent.atomic.AtomicInteger;

public class PayloadManager{
    protected static AtomicInteger payload = new  AtomicInteger(0);

    public static int increasePayload(){return payload.incrementAndGet();}
    public static int decreasePayload(){return payload.decrementAndGet();}
    public static int getPayload(){return payload.get();}
}