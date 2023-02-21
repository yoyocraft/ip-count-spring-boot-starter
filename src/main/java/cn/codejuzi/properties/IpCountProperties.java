package cn.codejuzi.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component("ipCountProperties")
@ConfigurationProperties(prefix = "tools.ip")
public class IpCountProperties {

    /**
     * 日志显示周期
     */
    private Long cycle = 10L;


    /**
     * 是否周期内清空数据
     */
    private Boolean cycleReset = false;

    /**
     * 日志输出格式 detail：详细模式，simple：极简模式
     */
    private String model = LogEnum.DETAIL.value;

    public enum LogEnum {
        DETAIL("detail"),
        SIMPLE("simple");

        private final String value;

        LogEnum(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public Long getCycle() {
        return cycle;
    }

    public void setCycle(Long cycle) {
        this.cycle = cycle;
    }

    public Boolean getCycleReset() {
        return cycleReset;
    }

    public void setCycleReset(Boolean cycleReset) {
        this.cycleReset = cycleReset;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
