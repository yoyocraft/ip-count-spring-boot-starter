package cn.codejuzi.service;


import cn.codejuzi.properties.IpCountProperties;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;


public class IpCountService {

    private final Map<String, Integer> ipCountMap = new HashMap<>();

    @Resource
    private HttpServletRequest servletRequest;

    @Resource
    private IpCountProperties ipCountProperties;

    public void count() {
        // 1.获取当前访问的ip
        String ipAddress = servletRequest.getRemoteAddr();
        // 2.根据IP获取对应的值
        ipCountMap.merge(ipAddress, 1, Integer::sum);
    }

    @Scheduled(cron = "0/#{ipCountProperties.cycle} * * * * ?")
    public void show() {
        System.out.println("         IP访问监控");
        if (ipCountProperties.getModel().equals(IpCountProperties.LogEnum.DETAIL.getValue())) {
            System.out.println("+-----ip-address-----+--num--+");
            for (Map.Entry<String, Integer> entry : ipCountMap.entrySet()) {
                System.out.printf("|%18s  |%5d  |%n", entry.getKey(), entry.getValue());
            }
            System.out.println("+--------------------+-------+");
        } else if (ipCountProperties.getModel().equals(IpCountProperties.LogEnum.SIMPLE.getValue())) {
            System.out.println("+-----ip-address-----+");
            for (String key : ipCountMap.keySet()) {
                System.out.printf("|%18s  |%n", key);
            }
            System.out.println("+--------------------+");
        }
        // 阶段内统计数据清零
        if (ipCountProperties.getCycleReset()) {
            ipCountMap.clear();
        }
    }

}
