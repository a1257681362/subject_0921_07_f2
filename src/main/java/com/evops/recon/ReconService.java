package com.evops.recon;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evops.common.BizType;
import com.evops.common.BusinessException;
import com.evops.recon.dto.WriteoffRequest;
import com.evops.recon.engine.ReconDiffItem;
import com.evops.recon.engine.ReconEngine;
import com.evops.recon.engine.ReconLine;
import com.evops.recon.engine.ReconOutcome;
import com.evops.recon.entity.ReconCompare;
import com.evops.recon.entity.ReconDiff;
import com.evops.recon.entity.ReconRun;
import com.evops.recon.entity.ReconWriteoff;
import com.evops.recon.entity.SatBillLine;
import com.evops.recon.mapper.ReconCompareMapper;
import com.evops.recon.mapper.ReconDiffMapper;
import com.evops.recon.mapper.ReconRunMapper;
import com.evops.recon.mapper.ReconWriteoffMapper;
import com.evops.recon.mapper.SatBillLineMapper;
import com.evops.request.RequestRecordService;
import com.evops.workorder.entity.WorkOrder;
import com.evops.workorder.mapper.WorkOrderMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 每日对账主流程。
 * 勾稽：本地账（当日登记轨迹面积的作业单，含红冲负面积单）对卫星平台账单，
 * 新旧双引擎每次并跑，active-engine 出正式差异清单，双方汇总落 t_recon_compare。
 * 重跑不删历史：旧批次与其未核销差异置 SUPERSEDED，已核销差异保留。
 * 核销：手工调整唯一入口，核销单与差异清零同一事务，提交即生效。
 */
@Service
public class ReconService {

    private final WorkOrderMapper workOrderMapper;
    private final SatBillLineMapper satBillLineMapper;
    private final ReconRunMapper reconRunMapper;
    private final ReconDiffMapper reconDiffMapper;
    private final ReconWriteoffMapper reconWriteoffMapper;
    private final ReconCompareMapper reconCompareMapper;
    private final RequestRecordService requestRecordService;
    private final ReconProperties properties;
    private final Map<String, ReconEngine> engines;

    public ReconService(WorkOrderMapper workOrderMapper,
                        SatBillLineMapper satBillLineMapper,
                        ReconRunMapper reconRunMapper,
                        ReconDiffMapper reconDiffMapper,
                        ReconWriteoffMapper reconWriteoffMapper,
                        ReconCompareMapper reconCompareMapper,
                        RequestRecordService requestRecordService,
                        ReconProperties properties,
                        List<ReconEngine> engineList) {
        this.workOrderMapper = workOrderMapper;
        this.satBillLineMapper = satBillLineMapper;
        this.reconRunMapper = reconRunMapper;
        this.reconDiffMapper = reconDiffMapper;
        this.reconWriteoffMapper = reconWriteoffMapper;
        this.reconCompareMapper = reconCompareMapper;
        this.requestRecordService = requestRecordService;
        this.properties = properties;
        this.engines = new HashMap<>();
        for (ReconEngine engine : engineList) {
            this.engines.put(engine.type(), engine);
        }
    }

    /**
     * 勾稽一个账期。synchronized 守住单实例内同账期并发重跑；
     * 调度与手工触发都走这里，整个批次（作废旧批次 + 新批次 + 对比记录）一个事务。
     */
    @Transactional(rollbackFor = Exception.class)
    public synchronized ReconRun reconcile(LocalDate bizDate, String triggerType) {
        LocalDateTime startedAt = LocalDateTime.now();

        List<ReconLine> localLines = loadLocalLines(bizDate);
        List<ReconLine> remoteLines = loadRemoteLines(bizDate);

        // 新旧逻辑并跑：两套引擎对同一套账各算一遍
        ReconOutcome legacyOutcome = requireEngine(ReconEngineType.LEGACY).reconcile(localLines, remoteLines);
        ReconOutcome toleranceOutcome = requireEngine(ReconEngineType.TOLERANCE).reconcile(localLines, remoteLines);

        String activeEngine = activeEngine();
        ReconOutcome activeOutcome = ReconEngineType.TOLERANCE.equals(activeEngine)
                ? toleranceOutcome : legacyOutcome;

        supersedeOpenRuns(bizDate);

        ReconRun run = new ReconRun();
        run.setRunNo(nextRunNo(bizDate));
        run.setBizDate(bizDate);
        run.setEngine(activeEngine);
        run.setTriggerType(triggerType);
        run.setStatus(ReconRunStatus.SUCCESS);
        run.setLocalCount(localLines.size());
        run.setRemoteCount(remoteLines.size());
        run.setMatchedCount(activeOutcome.getMatchedCount());
        run.setDiffCount(activeOutcome.getDiffs().size());
        run.setTotalDiffArea(activeOutcome.totalDiffArea());
        run.setStartedAt(startedAt);
        run.setFinishedAt(LocalDateTime.now());
        reconRunMapper.insert(run);

        for (ReconDiffItem item : activeOutcome.getDiffs()) {
            ReconDiff diff = new ReconDiff();
            diff.setRunId(run.getId());
            diff.setBizDate(bizDate);
            diff.setOrderNo(item.getOrderNo());
            diff.setDiffType(item.getDiffType());
            diff.setLocalOrderId(item.getLocalOrderId());
            diff.setLocalTrackArea(item.getLocalTrackArea());
            diff.setRemoteTrackArea(item.getRemoteTrackArea());
            diff.setDiffArea(item.getDiffArea());
            diff.setStatus(ReconDiffStatus.OPEN);
            reconDiffMapper.insert(diff);
        }

        ReconCompare compare = new ReconCompare();
        compare.setBizDate(bizDate);
        compare.setRunId(run.getId());
        compare.setActiveEngine(activeEngine);
        compare.setLegacyMatched(legacyOutcome.getMatchedCount());
        compare.setLegacyDiffCount(legacyOutcome.getDiffs().size());
        compare.setLegacyDiffArea(legacyOutcome.totalDiffArea());
        compare.setToleranceMatched(toleranceOutcome.getMatchedCount());
        compare.setToleranceDiffCount(toleranceOutcome.getDiffs().size());
        compare.setToleranceDiffArea(toleranceOutcome.totalDiffArea());
        compare.setIdentical(sameNumbers(legacyOutcome, toleranceOutcome) ? "1" : "0");
        reconCompareMapper.insert(compare);

        return run;
    }

    /** 调度任务失败留痕：独立事务记一笔 FAILED 批次，不影响已有结果。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordFailure(LocalDate bizDate, String triggerType, String message) {
        ReconRun run = new ReconRun();
        run.setRunNo(nextRunNo(bizDate));
        run.setBizDate(bizDate);
        // 引擎配置本身就可能是失败原因，这里记原始配置值，不做校验
        run.setEngine(properties.getActiveEngine());
        run.setTriggerType(triggerType);
        run.setStatus(ReconRunStatus.FAILED);
        run.setMessage(message == null ? null
                : message.substring(0, Math.min(message.length(), 255)));
        run.setStartedAt(LocalDateTime.now());
        run.setFinishedAt(LocalDateTime.now());
        reconRunMapper.insert(run);
    }

    /**
     * 核销：请求号登记 → 差异必须 OPEN → 落核销单 → 差异置 WRITTEN_OFF，
     * 全部同一事务；提交后 OPEN 清单与差异合计即时清零。
     * 一笔差异只能核销一次，uk_recon_writeoff_diff 兜底并发。
     */
    @Transactional(rollbackFor = Exception.class)
    public ReconWriteoff writeoff(Long diffId, WriteoffRequest req) {
        requestRecordService.register(req.getRequestNo(), BizType.RECON_WRITEOFF, String.valueOf(diffId));

        ReconDiff diff = reconDiffMapper.selectById(diffId);
        if (diff == null) {
            throw new BusinessException("差异不存在: " + diffId);
        }
        if (!ReconDiffStatus.OPEN.equals(diff.getStatus())) {
            throw new BusinessException("差异状态为 " + diff.getStatus() + "，不能核销");
        }

        ReconWriteoff writeoff = new ReconWriteoff();
        writeoff.setWriteoffNo("WOFF" + diff.getBizDate().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + diff.getId());
        writeoff.setDiffId(diff.getId());
        writeoff.setRequestNo(req.getRequestNo());
        writeoff.setBizDate(diff.getBizDate());
        writeoff.setOrderNo(diff.getOrderNo());
        writeoff.setAdjustArea(diff.getDiffArea());
        writeoff.setReason(req.getReason());
        writeoff.setOperator(req.getOperator());
        try {
            reconWriteoffMapper.insert(writeoff);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("差异 " + diffId + " 已核销，请勿重复操作");
        }

        diff.setStatus(ReconDiffStatus.WRITTEN_OFF);
        diff.setWriteoffId(writeoff.getId());
        diff.setWriteoffTime(LocalDateTime.now());
        reconDiffMapper.updateById(diff);
        return writeoff;
    }

    /** 勾稽批次列表，可按账期过滤，新批次在前。 */
    public List<ReconRun> listRuns(LocalDate bizDate) {
        return reconRunMapper.selectList(new LambdaQueryWrapper<ReconRun>()
                .eq(bizDate != null, ReconRun::getBizDate, bizDate)
                .orderByDesc(ReconRun::getId));
    }

    /** 差异清单（带双方明细快照）；状态默认 OPEN——即当前待核销差异。 */
    public List<ReconDiff> listDiffs(LocalDate bizDate, String status) {
        String effectiveStatus = StringUtils.hasText(status) ? status : ReconDiffStatus.OPEN;
        return reconDiffMapper.selectList(new LambdaQueryWrapper<ReconDiff>()
                .eq(bizDate != null, ReconDiff::getBizDate, bizDate)
                .eq(ReconDiff::getStatus, effectiveStatus)
                .orderByAsc(ReconDiff::getId));
    }

    /** 新旧引擎并跑对比记录，供"两天数字对得上再切"核对。 */
    public List<ReconCompare> listCompares(LocalDate from, LocalDate to) {
        return reconCompareMapper.selectList(new LambdaQueryWrapper<ReconCompare>()
                .ge(from != null, ReconCompare::getBizDate, from)
                .le(to != null, ReconCompare::getBizDate, to)
                .orderByAsc(ReconCompare::getBizDate)
                .orderByAsc(ReconCompare::getId));
    }

    /** 本地账：当日登记轨迹面积的作业单（含红冲负面积单），轨迹面积是权威口径。 */
    private List<ReconLine> loadLocalLines(LocalDate bizDate) {
        List<WorkOrder> orders = workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrder>()
                .isNotNull(WorkOrder::getTrackArea)
                .ge(WorkOrder::getTrackTime, bizDate.atStartOfDay())
                .lt(WorkOrder::getTrackTime, bizDate.plusDays(1).atStartOfDay()));
        return orders.stream()
                .map(o -> new ReconLine(o.getOrderNo(), o.getTrackArea(), o.getId()))
                .collect(Collectors.toList());
    }

    /** 对方账：卫星平台该账期推送的账单明细。 */
    private List<ReconLine> loadRemoteLines(LocalDate bizDate) {
        List<SatBillLine> lines = satBillLineMapper.selectList(new LambdaQueryWrapper<SatBillLine>()
                .eq(SatBillLine::getBillDate, bizDate));
        return lines.stream()
                .map(l -> new ReconLine(l.getOrderNo(), l.getTrackArea(), l.getId()))
                .collect(Collectors.toList());
    }

    /** 同账期历史批次（及其未核销差异）置 SUPERSEDED；已核销差异是历史事实，保留不动。 */
    private void supersedeOpenRuns(LocalDate bizDate) {
        List<ReconRun> oldRuns = reconRunMapper.selectList(new LambdaQueryWrapper<ReconRun>()
                .eq(ReconRun::getBizDate, bizDate)
                .eq(ReconRun::getStatus, ReconRunStatus.SUCCESS));
        for (ReconRun old : oldRuns) {
            old.setStatus(ReconRunStatus.SUPERSEDED);
            reconRunMapper.updateById(old);
            List<ReconDiff> openDiffs = reconDiffMapper.selectList(new LambdaQueryWrapper<ReconDiff>()
                    .eq(ReconDiff::getRunId, old.getId())
                    .eq(ReconDiff::getStatus, ReconDiffStatus.OPEN));
            for (ReconDiff diff : openDiffs) {
                diff.setStatus(ReconDiffStatus.SUPERSEDED);
                reconDiffMapper.updateById(diff);
            }
        }
    }

    private String nextRunNo(LocalDate bizDate) {
        long seq = reconRunMapper.selectCount(new LambdaQueryWrapper<ReconRun>()
                .eq(ReconRun::getBizDate, bizDate)) + 1;
        return "R" + bizDate.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + seq;
    }

    private String activeEngine() {
        String configured = properties.getActiveEngine();
        if (!StringUtils.hasText(configured)) {
            return ReconEngineType.LEGACY;
        }
        String normalized = configured.trim().toUpperCase();
        if (!engines.containsKey(normalized)) {
            throw new BusinessException("未知的勾稽引擎配置: " + configured);
        }
        return normalized;
    }

    private ReconEngine requireEngine(String type) {
        ReconEngine engine = engines.get(type);
        if (engine == null) {
            throw new BusinessException("勾稽引擎未装配: " + type);
        }
        return engine;
    }

    /** 两套引擎数字是否对得上：勾平笔数、差异笔数、差异面积合计都一致。 */
    private boolean sameNumbers(ReconOutcome a, ReconOutcome b) {
        return a.getMatchedCount() == b.getMatchedCount()
                && a.getDiffs().size() == b.getDiffs().size()
                && a.totalDiffArea().compareTo(b.totalDiffArea()) == 0;
    }
}
