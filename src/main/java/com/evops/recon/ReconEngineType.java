package com.evops.recon;

/** 勾稽引擎标识：并跑期两套都算，active-engine 决定哪套出正式差异清单。 */
public final class ReconEngineType {
    /** 旧逻辑：单号配对 + 面积严格等额。 */
    public static final String LEGACY = "LEGACY";
    /** 新逻辑：单号一笔对一笔 + 容差内算平。 */
    public static final String TOLERANCE = "TOLERANCE";

    private ReconEngineType() {
    }
}
