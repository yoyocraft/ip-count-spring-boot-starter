package cn.codejuzi.autoconfig;


import cn.codejuzi.properties.IpCountProperties;
import cn.codejuzi.service.IpCountService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@Import(IpCountProperties.class)
public class IpCountAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IpCountService ipCountService() {
        return new IpCountService();
    }
}
