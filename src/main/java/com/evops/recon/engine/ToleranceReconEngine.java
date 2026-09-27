package com.evops.recon.engine;

import com.evops.recon.ReconEngineType;
import com.evops.recon.ReconProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** 新逻辑：单号一笔对一笔，面积差在可配置容差内即算平。 */
@Component
public class ToleranceReconEngine extends AbstractOrderNoEngine {

    private final ReconProperties properties;

    public ToleranceReconEngine(ReconProperties properties) {
        this.properties = properties;
    }

    @Override
    public String type() {
        return ReconEngineType.TOLERANCE;
    }

    @Override
    protected boolean areasMatch(BigDecimal localArea, BigDecimal remoteArea) {
        BigDecimal gap = localArea.subtract(remoteArea).abs();
        return gap.compareTo(properties.getToleranceArea()) <= 0;
    }
}
