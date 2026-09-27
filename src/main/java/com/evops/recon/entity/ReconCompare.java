package com.evops.recon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 新旧引擎并跑对比：每次勾稽双引擎各跑一遍，汇总落此表。
 * 连续两天 identical=1（数字对得上）后，把 evops.recon.active-engine 切到 TOLERANCE。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_recon_compare")
public class ReconCompare extends BaseEntity {
    private LocalDate bizDate;
    private Long runId;
    /** 本次勾稽出正式结果的引擎。 */
    private String activeEngine;

    private Integer legacyMatched = 0;
    private Integer legacyDiffCount = 0;
    private BigDecimal legacyDiffArea = BigDecimal.ZERO;

    private Integer toleranceMatched = 0;
    private Integer toleranceDiffCount = 0;
    private BigDecimal toleranceDiffArea = BigDecimal.ZERO;

    /** 双引擎结果是否完全一致：'1' 一致 / '0' 不一致。 */
    private String identical = "0";
}
