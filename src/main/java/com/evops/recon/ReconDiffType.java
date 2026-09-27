package com.evops.recon;

/** 勾稽差异类型。 */
public final class ReconDiffType {
    /** 双边都有账，但面积差超过容差。 */
    public static final String AREA_DIFF = "AREA_DIFF";
    /** 本地有账、对方无账。 */
    public static final String ONLY_LOCAL = "ONLY_LOCAL";
    /** 对方有账、本地无账。 */
    public static final String ONLY_REMOTE = "ONLY_REMOTE";

    private ReconDiffType() {
    }
}
