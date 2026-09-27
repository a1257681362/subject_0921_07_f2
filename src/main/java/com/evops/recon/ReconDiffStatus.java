package com.evops.recon;

/** 勾稽差异状态机：OPEN 待处理 → WRITTEN_OFF 已核销 / SUPERSEDED 被重跑作废。 */
public final class ReconDiffStatus {
    /** 未核销，计入差异清单与差异合计。 */
    public static final String OPEN = "OPEN";
    /** 已核销，差异即时清零，不再计入。 */
    public static final String WRITTEN_OFF = "WRITTEN_OFF";
    /** 同账期重跑后被新批次取代，仅留痕。 */
    public static final String SUPERSEDED = "SUPERSEDED";

    private ReconDiffStatus() {
    }
}
