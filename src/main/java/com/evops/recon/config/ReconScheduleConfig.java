package com.evops.recon.config;

import com.evops.recon.ReconProperties;
import com.evops.recon.job.ScheduledReconRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.MethodInvokingJobDetailFactoryBean;

/**
 * 每日勾稽的 Quartz 装配：Spring Boot 自动把 JobDetail + Trigger 注册进调度器。
 * cron 走 evops.recon.cron 配置，改配置重启即换点；concurrent=false 防重入。
 */
@Configuration
public class ReconScheduleConfig {

    @Bean
    public MethodInvokingJobDetailFactoryBean reconJobDetail(ScheduledReconRunner runner) {
        MethodInvokingJobDetailFactoryBean factory = new MethodInvokingJobDetailFactoryBean();
        factory.setName("reconDailyJob");
        factory.setGroup("recon");
        factory.setTargetObject(runner);
        factory.setTargetMethod("runDaily");
        factory.setConcurrent(false);
        return factory;
    }

    @Bean
    public CronTriggerFactoryBean reconCronTrigger(MethodInvokingJobDetailFactoryBean reconJobDetail,
                                                   ReconProperties properties) {
        CronTriggerFactoryBean factory = new CronTriggerFactoryBean();
        factory.setName("reconDailyTrigger");
        factory.setGroup("recon");
        factory.setJobDetail(reconJobDetail.getObject());
        factory.setCronExpression(properties.getCron());
        return factory;
    }
}
