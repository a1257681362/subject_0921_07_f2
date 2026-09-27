package com.evops.recon.engine;

import com.evops.recon.ReconEngineType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** 旧逻辑：单号配对，面积严格等额才算平（无容差概念）。并跑期的对照组。 */
@Component
public class LegacyReconEngine extends AbstractOrderNoEngine {

    @Override
    public String type() {
        return ReconEngineType.LEGACY;
    }

    @Override
    protected boolean areasMatch(BigDecimal localArea, BigDecimal remoteArea) {
        return localArea.compareTo(remoteArea) == 0;
    }
}
