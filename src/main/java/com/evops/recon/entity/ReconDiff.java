package com.evops.recon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 勾稽差异清单：只放差异记录（双边面积不符 / 单方缺账），
 * 双方明细以勾稽时刻快照随行保留，事后作业单变更不影响清单。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_recon_diff")
public class ReconDiff extends BaseEntity {
    private Long runId;
    private LocalDate bizDate;
    private String orderNo;
    /** AREA_DIFF 双边不符 / ONLY_LOCAL 本地单边 / ONLY_REMOTE 对方单边。 */
    private String diffType;

    /** 本侧明细快照。 */
    private Long localOrderId;
    private BigDecimal localTrackArea;

    /** 对方明细快照。 */
    private BigDecimal remoteTrackArea;

    /** 差异面积 = 本侧 - 对方（单边账取有账一侧带符号值）。 */
    private BigDecimal diffArea;

    /** OPEN 待处理 / WRITTEN_OFF 已核销 / SUPERSEDED 已被重跑取代。 */
    private String status;
    private Long writeoffId;
    private LocalDateTime writeoffTime;
}
