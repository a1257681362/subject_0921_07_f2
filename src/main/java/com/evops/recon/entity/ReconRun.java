package com.evops.recon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 勾稽批次：每次定时调度/手工触发产生一条；重跑不删历史，旧批次置 SUPERSEDED。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_recon_run")
public class ReconRun extends BaseEntity {
    private String runNo;
    private LocalDate bizDate;
    /** 出正式差异清单的引擎：LEGACY / TOLERANCE。 */
    private String engine;
    /** SCHEDULED 定时调度 / MANUAL 手工触发。 */
    private String triggerType;
    private String status;
    private Integer localCount = 0;
    private Integer remoteCount = 0;
    private Integer matchedCount = 0;
    private Integer diffCount = 0;
    /** 正式引擎产出的差异面积合计（本侧-对方，带符号）。 */
    private BigDecimal totalDiffArea = BigDecimal.ZERO;
    private String message;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
