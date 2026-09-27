package com.evops.recon.engine;

import java.util.List;

/** 勾稽引擎：同一套账，新旧两套逻辑各跑一遍（并跑），由 active-engine 决定谁出正式清单。 */
public interface ReconEngine {

    /** 引擎标识，见 ReconEngineType。 */
    String type();

    /** 对本地账与对方账做一笔对一笔勾稽。 */
    ReconOutcome reconcile(List<ReconLine> localLines, List<ReconLine> remoteLines);
}
