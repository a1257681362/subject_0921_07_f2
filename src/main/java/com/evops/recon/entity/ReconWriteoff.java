package com.evops.recon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 核销单：手工调整的唯一入口；一笔差异只能核销一次，核销后差异即时清零。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_recon_writeoff")
public class ReconWriteoff extends BaseEntity {
    private String writeoffNo;
    private Long diffId;
    private String requestNo;
    private LocalDate bizDate;
    private String orderNo;
    /** 核销调整额 = 核销时差异全额（只全额核销，核销即清零）。 */
    private BigDecimal adjustArea;
    private String reason;
    private String operator;
}
