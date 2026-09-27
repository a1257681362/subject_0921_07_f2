package com.evops.recon.engine;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/** 参与勾稽的一侧账单行：本地作业单轨迹面积 或 卫星平台账单明细。 */
@Data
@AllArgsConstructor
public class ReconLine {
    private String orderNo;
    private BigDecimal area;
    /** 来源主键（本地作业单 id / 对方账单行 id），仅用于差异留痕。 */
    private Long refId;
}
