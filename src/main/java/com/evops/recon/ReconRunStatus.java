package com.evops.recon;

/** 勾稽批次状态。 */
public final class ReconRunStatus {
    public static final String SUCCESS = "SUCCESS";
    /** 同账期重跑后被新批次取代（历史保留，不物理删除）。 */
    public static final String SUPERSEDED = "SUPERSEDED";
    public static final String FAILED = "FAILED";

    private ReconRunStatus() {
    }
}
