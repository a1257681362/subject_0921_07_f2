package com.evops.recon.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/** 卫星轨迹核算平台每日账单导入请求：整批一个请求号，要么全成要么全回滚。 */
@Data
public class SatBillImportRequest {
    @NotBlank(message = "请求号不能为空")
    private String requestNo;
    @NotNull(message = "账期不能为空")
    private LocalDate billDate;
    @Valid
    @NotEmpty(message = "账单明细不能为空")
    private List<SatBillLineItem> lines;
}
