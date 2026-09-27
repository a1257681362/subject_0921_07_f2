package com.evops.recon.engine;

import lombok.Data;

import java.math.BigDecimal;

/** 引擎产出的一条差异（双方明细快照字段在此成形，落库时原样带走）。 */
@Data
public class ReconDiffItem {
    private String orderNo;
    /** 见 ReconDiffType：AREA_DIFF / ONLY_LOCAL / ONLY_REMOTE。 */
    private String diffType;
    private Long localOrderId;
    private BigDecimal localTrackArea;
    private BigDecimal remoteTrackArea;
    /** 差异面积 = 本侧 - 对方；单边账取有账一侧带符号值（ONLY_REMOTE 为负）。 */
    private BigDecimal diffArea;
}
