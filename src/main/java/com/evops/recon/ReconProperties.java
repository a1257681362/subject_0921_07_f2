package com.evops.recon;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 对账配置。
 * cron：定时勾稽表达式（默认每天 06:00 对前一日的账）；
 * toleranceArea：新引擎匹配容差（亩），|本侧-对方| ≤ 容差算平，改配置即生效；
 * activeEngine：并跑期出正式差异清单的引擎（LEGACY/TOLERANCE），
 * 连续两天 t_recon_compare.identical=1 后切到 TOLERANCE。
 */
@Data
@Component
@ConfigurationProperties(prefix = "evops.recon")
public class ReconProperties {
    private String cron = "0 0 6 * * ?";
    private BigDecimal toleranceArea = new BigDecimal("0.05");
    private String activeEngine = ReconEngineType.LEGACY;

    /** 指定引擎是否为当前出正式差异清单的引擎。 */
    public boolean isActive(String engineType) {
        return activeEngine != null && activeEngine.equalsIgnoreCase(engineType);
    }
}
