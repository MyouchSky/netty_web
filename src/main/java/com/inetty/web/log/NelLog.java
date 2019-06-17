package com.inetty.web.log;

import lombok.Data;

@Data
public class NelLog {
    public String action;
    public String mid;
    public String ip;
    public Long cost;
    public String info;

}
