package com.evops.recon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 卫星轨迹核算平台的账单明细（对方账），由导入接口按账期推送。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_sat_bill_line")
public class SatBillLine extends BaseEntity {
    private LocalDate billDate;
    private String orderNo;
    private String machineCode;
    /** 对方核算的轨迹面积。 */
    private BigDecimal trackArea;
    /** 导入批次号（=导入请求的请求号）。 */
    private String sourceBatch;
    private String remark;
}
