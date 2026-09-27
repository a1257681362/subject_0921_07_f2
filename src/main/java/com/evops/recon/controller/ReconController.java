package com.evops.recon.controller;

import com.evops.common.ApiResponse;
import com.evops.recon.ReconService;
import com.evops.recon.ReconTriggerType;
import com.evops.recon.SatBillService;
import com.evops.recon.dto.SatBillImportRequest;
import com.evops.recon.dto.WriteoffRequest;
import com.evops.recon.entity.ReconCompare;
import com.evops.recon.entity.ReconDiff;
import com.evops.recon.entity.ReconRun;
import com.evops.recon.entity.ReconWriteoff;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/recon")
public class ReconController {

    private final ReconService reconService;
    private final SatBillService satBillService;

    public ReconController(ReconService reconService, SatBillService satBillService) {
        this.reconService = reconService;
        this.satBillService = satBillService;
    }

    /** 卫星平台每日账单导入（对方推送，请求号幂等）。 */
    @PostMapping("/sat-bills/import")
    public ApiResponse<Integer> importSatBills(@Valid @RequestBody SatBillImportRequest request) {
        return ApiResponse.ok(satBillService.importBill(request));
    }

    /** 手工触发勾稽（补跑/联调），默认对前一日。 */
    @PostMapping("/run")
    public ApiResponse<ReconRun> run(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        LocalDate effectiveDate = bizDate != null ? bizDate : LocalDate.now().minusDays(1);
        return ApiResponse.ok(reconService.reconcile(effectiveDate, ReconTriggerType.MANUAL));
    }

    /** 勾稽批次列表。 */
    @GetMapping("/runs")
    public ApiResponse<List<ReconRun>> listRuns(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        return ApiResponse.ok(reconService.listRuns(bizDate));
    }

    /** 差异清单（带双方明细），状态默认 OPEN。 */
    @GetMapping("/diffs")
    public ApiResponse<List<ReconDiff>> listDiffs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(reconService.listDiffs(bizDate, status));
    }

    /** 核销：手工调整走核销流程，核销后差异即时清零。 */
    @PostMapping("/diffs/{id}/writeoff")
    public ApiResponse<ReconWriteoff> writeoff(@PathVariable Long id,
                                               @Valid @RequestBody WriteoffRequest request) {
        return ApiResponse.ok(reconService.writeoff(id, request));
    }

    /** 新旧引擎并跑对比：连续两天 identical=1 后切 active-engine。 */
    @GetMapping("/compares")
    public ApiResponse<List<ReconCompare>> listCompares(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(reconService.listCompares(from, to));
    }
}
