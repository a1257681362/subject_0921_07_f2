package com.evops.recon.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 卫星平台账单明细行。 */
@Data
public class SatBillLineItem {
    @NotBlank(message = "作业单号不能为空")
    private String orderNo;
    @NotNull(message = "对方核算面积不能为空")
    private BigDecimal trackArea;
    private String machineCode;
    private String remark;
}
