package com.evops.recon;

/** 勾稽触发方式。 */
public final class ReconTriggerType {
    /** 定时调度。 */
    public static final String SCHEDULED = "SCHEDULED";
    /** 手工触发（补跑/联调）。 */
    public static final String MANUAL = "MANUAL";

    private ReconTriggerType() {
    }
}
