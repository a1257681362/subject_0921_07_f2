package com.evops.recon.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 核销请求：手工调整走核销流程，核销后差异即时清零。 */
@Data
public class WriteoffRequest {
    @NotBlank(message = "请求号不能为空")
    private String requestNo;
    @NotBlank(message = "核销原因不能为空")
    private String reason;
    private String operator;
}
