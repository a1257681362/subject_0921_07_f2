package com.evops.recon;

import com.evops.common.BusinessException;
import com.evops.machine.MachineService;
import com.evops.machine.dto.MachineCreateRequest;
import com.evops.machine.entity.Machine;
import com.evops.recon.dto.SatBillImportRequest;
import com.evops.recon.dto.SatBillLineItem;
import com.evops.recon.dto.WriteoffRequest;
import com.evops.recon.entity.ReconDiff;
import com.evops.recon.entity.ReconRun;
import com.evops.recon.entity.ReconWriteoff;
import com.evops.recon.mapper.ReconDiffMapper;
import com.evops.recon.mapper.ReconRunMapper;
import com.evops.workorder.WorkOrderService;
import com.evops.workorder.dto.TrackAreaRequest;
import com.evops.workorder.dto.WorkOrderCreateRequest;
import com.evops.workorder.entity.WorkOrder;
import com.evops.workorder.mapper.WorkOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对账主链路：导入对方账单 → 勾稽 → 差异清单（双方明细）→ 核销即时清零；
 * 请求号幂等、重复核销拦截、重跑作废旧批次但保留已核销历史。
 * 每个用例用独立账期（过去的日期），与作业单测试的"当天"数据互不干扰。
 */
@SpringBootTest
@ActiveProfiles("test")
class ReconMainFlowTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MachineService machineService;
    @Autowired
    private WorkOrderService workOrderService;
    @Autowired
    private WorkOrderMapper workOrderMapper;
    @Autowired
    private SatBillService satBillService;
    @Autowired
    private ReconService reconService;
    @Autowired
    private ReconDiffMapper reconDiffMapper;
    @Autowired
    private ReconRunMapper reconRunMapper;
    @Autowired
    private ReconProperties properties;

    @BeforeEach
    void resetEngineConfig() {
        // 并跑配置可能被其他测试类改过，每个用例从确定状态出发
        properties.setActiveEngine(ReconEngineType.LEGACY);
        properties.setToleranceArea(new BigDecimal("0.05"));
    }

    @Test
    void importReconWriteoff_clearsDiffImmediately() {
        LocalDate bizDate = LocalDate.of(2026, 1, 10);
        WorkOrder matched = orderWithTrackArea("WO-RC-MATCH-", "10.00", bizDate);
        WorkOrder differ = orderWithTrackArea("WO-RC-DIFF-", "8.00", bizDate);
        // 对方账：一笔勾平、一笔差 1 亩（超容差）、一笔本地没有
        importBill(bizDate, "REQ-IMP-FLOW-1",
                line(matched.getOrderNo(), "10.00"),
                line(differ.getOrderNo(), "9.00"),
                line("WO-RC-GHOST-1", "2.00"));

        ReconRun run = reconService.reconcile(bizDate, ReconTriggerType.MANUAL);
        assertEquals(ReconRunStatus.SUCCESS, run.getStatus());
        assertEquals(ReconTriggerType.MANUAL, run.getTriggerType());
        assertEquals(2, run.getLocalCount());
        assertEquals(3, run.getRemoteCount());
        assertEquals(1, run.getMatchedCount());
        assertEquals(2, run.getDiffCount());
        assertNotNull(run.getStartedAt());
        assertNotNull(run.getFinishedAt());

        // 差异清单带双方明细
        List<ReconDiff> open = reconService.listDiffs(bizDate, ReconDiffStatus.OPEN);
        assertEquals(2, open.size());
        ReconDiff areaDiff = open.stream()
                .filter(d -> d.getOrderNo().equals(differ.getOrderNo())).findFirst().orElse(null);
        assertNotNull(areaDiff);
        assertEquals(ReconDiffType.AREA_DIFF, areaDiff.getDiffType());
        assertEquals(differ.getId(), areaDiff.getLocalOrderId());
        assertEquals(0, new BigDecimal("8.00").compareTo(areaDiff.getLocalTrackArea()));
        assertEquals(0, new BigDecimal("9.00").compareTo(areaDiff.getRemoteTrackArea()));
        assertEquals(0, new BigDecimal("-1.00").compareTo(areaDiff.getDiffArea()));
        ReconDiff ghost = open.stream()
                .filter(d -> d.getOrderNo().equals("WO-RC-GHOST-1")).findFirst().orElse(null);
        assertNotNull(ghost);
        assertEquals(ReconDiffType.ONLY_REMOTE, ghost.getDiffType());
        assertNull(ghost.getLocalTrackArea());
        assertEquals(0, new BigDecimal("2.00").compareTo(ghost.getRemoteTrackArea()));
        assertEquals(0, new BigDecimal("-2.00").compareTo(ghost.getDiffArea()));

        // 核销面积差异：核销单记全额调整，差异即时清零
        ReconWriteoff writeoff = reconService.writeoff(areaDiff.getId(),
                writeoffReq("REQ-WOFF-FLOW-1", "与对方核对为轨迹重算差异，确认核销"));
        assertEquals("WOFF" + bizDate.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + areaDiff.getId(),
                writeoff.getWriteoffNo());
        assertEquals(0, areaDiff.getDiffArea().compareTo(writeoff.getAdjustArea()));
        assertEquals(bizDate, writeoff.getBizDate());

        List<ReconDiff> afterFirst = reconService.listDiffs(bizDate, ReconDiffStatus.OPEN);
        assertEquals(1, afterFirst.size(), "核销后 OPEN 清单立即少一笔");
        assertEquals("WO-RC-GHOST-1", afterFirst.get(0).getOrderNo());
        ReconDiff reloaded = reconDiffMapper.selectById(areaDiff.getId());
        assertEquals(ReconDiffStatus.WRITTEN_OFF, reloaded.getStatus());
        assertEquals(writeoff.getId(), reloaded.getWriteoffId());
        assertNotNull(reloaded.getWriteoffTime());

        // 核销另一笔 → 该账期差异全部清零
        reconService.writeoff(ghost.getId(), writeoffReq("REQ-WOFF-FLOW-2", "对方重复推送，确认核销"));
        assertTrue(reconService.listDiffs(bizDate, ReconDiffStatus.OPEN).isEmpty(), "核销后差异即时清零");
    }

    @Test
    void writeoff_replayAndDuplicate_rejected() {
        LocalDate bizDate = LocalDate.of(2026, 1, 11);
        WorkOrder order = orderWithTrackArea("WO-RC-DUP-", "6.00", bizDate);
        importBill(bizDate, "REQ-IMP-DUPWO-1", line(order.getOrderNo(), "6.50"));
        reconService.reconcile(bizDate, ReconTriggerType.MANUAL);
        List<ReconDiff> open = reconService.listDiffs(bizDate, ReconDiffStatus.OPEN);
        assertEquals(1, open.size());
        ReconDiff diff = open.get(0);

        WriteoffRequest req = writeoffReq("REQ-WOFF-REPLAY-1", "确认核销");
        reconService.writeoff(diff.getId(), req);

        // 同一请求号重放 → 台账拦截
        BusinessException replay = assertThrows(BusinessException.class,
                () -> reconService.writeoff(diff.getId(), req));
        assertTrue(replay.getMessage().contains("已处理"));

        // 换请求号再核销同一差异 → 状态拦截
        BusinessException dup = assertThrows(BusinessException.class,
                () -> reconService.writeoff(diff.getId(), writeoffReq(uniq("REQ-WOFF-AGAIN-"), "重复核销")));
        assertTrue(dup.getMessage().contains("不能核销"));
    }

    @Test
    void rerun_supersedesOpenRuns_keepsWrittenOffHistory() {
        LocalDate bizDate = LocalDate.of(2026, 1, 12);
        WorkOrder order = orderWithTrackArea("WO-RC-RERUN-", "5.00", bizDate);
        importBill(bizDate, "REQ-IMP-RERUN-1", line(order.getOrderNo(), "5.40"));
        ReconRun first = reconService.reconcile(bizDate, ReconTriggerType.MANUAL);
        ReconDiff diff = reconService.listDiffs(bizDate, ReconDiffStatus.OPEN).get(0);
        reconService.writeoff(diff.getId(), writeoffReq("REQ-WOFF-RERUN-1", "确认核销"));

        // 对方补推一笔新账单后重跑同账期
        importBill(bizDate, "REQ-IMP-RERUN-2", line("WO-RC-RERUN-NEW", "3.00"));
        ReconRun second = reconService.reconcile(bizDate, ReconTriggerType.MANUAL);

        assertEquals("R" + bizDate.format(DateTimeFormatter.BASIC_ISO_DATE) + "-2", second.getRunNo());
        assertEquals(ReconRunStatus.SUPERSEDED, reconRunMapper.selectById(first.getId()).getStatus(),
                "旧批次不物理删除，置 SUPERSEDED");
        ReconDiff writtenOff = reconDiffMapper.selectById(diff.getId());
        assertEquals(ReconDiffStatus.WRITTEN_OFF, writtenOff.getStatus(), "已核销差异是历史事实，重跑不动它");

        // 新批次按当前账重新出差异：原单面积差 + 新账单对方单边
        List<ReconDiff> open = reconService.listDiffs(bizDate, ReconDiffStatus.OPEN);
        assertEquals(2, open.size());
        assertTrue(open.stream().anyMatch(d -> d.getOrderNo().equals(order.getOrderNo())
                && ReconDiffType.AREA_DIFF.equals(d.getDiffType())));
        assertTrue(open.stream().anyMatch(d -> d.getOrderNo().equals("WO-RC-RERUN-NEW")
                && ReconDiffType.ONLY_REMOTE.equals(d.getDiffType())));
    }

    @Test
    void importBill_duplicateLineAndReplay_rejected() {
        LocalDate bizDate = LocalDate.of(2026, 1, 13);
        SatBillImportRequest req = importReq(bizDate, "REQ-IMP-DUP-1", line("WO-IMP-DUP-1", "1.00"));
        assertEquals(1, satBillService.importBill(req));

        // 同请求号重放 → 台账拦截
        assertThrows(BusinessException.class, () -> satBillService.importBill(req));

        // 换请求号推同账期同单号 → 唯一约束兜底
        SatBillImportRequest again = importReq(bizDate, "REQ-IMP-DUP-2", line("WO-IMP-DUP-1", "1.00"));
        BusinessException ex = assertThrows(BusinessException.class, () -> satBillService.importBill(again));
        assertTrue(ex.getMessage().contains("已存在"));
    }

    private WorkOrder orderWithTrackArea(String orderNoPrefix, String area, LocalDate bizDate) {
        MachineCreateRequest machineReq = new MachineCreateRequest();
        machineReq.setMachineCode(uniq("M-RC-"));
        machineReq.setMachineName("对账测试机");
        machineReq.setRequestNo(uniq("REQ-MF-M-"));
        Machine machine = machineService.create(machineReq);

        WorkOrderCreateRequest createReq = new WorkOrderCreateRequest();
        createReq.setOrderNo(uniq(orderNoPrefix));
        createReq.setMachineId(machine.getId());
        createReq.setOperationType("收割");
        createReq.setRequestNo(uniq("REQ-MF-WO-"));
        WorkOrder order = workOrderService.create(createReq);

        TrackAreaRequest trackReq = new TrackAreaRequest();
        trackReq.setTrackArea(new BigDecimal(area));
        trackReq.setRequestNo(uniq("REQ-MF-TRACK-"));
        WorkOrder tracked = workOrderService.reportTrackArea(order.getId(), trackReq);

        // 轨迹登记时间回放到指定账期（接口只能记当下，测试直接改库）
        tracked.setTrackTime(bizDate.atTime(10, 0));
        workOrderMapper.updateById(tracked);
        return tracked;
    }

    private SatBillLineItem line(String orderNo, String area) {
        SatBillLineItem item = new SatBillLineItem();
        item.setOrderNo(orderNo);
        item.setTrackArea(new BigDecimal(area));
        return item;
    }

    private void importBill(LocalDate bizDate, String requestNo, SatBillLineItem... lines) {
        satBillService.importBill(importReq(bizDate, requestNo, lines));
    }

    private SatBillImportRequest importReq(LocalDate bizDate, String requestNo, SatBillLineItem... lines) {
        SatBillImportRequest req = new SatBillImportRequest();
        req.setRequestNo(requestNo);
        req.setBillDate(bizDate);
        List<SatBillLineItem> items = new ArrayList<>();
        for (SatBillLineItem line : lines) {
            items.add(line);
        }
        req.setLines(items);
        return req;
    }

    private WriteoffRequest writeoffReq(String requestNo, String reason) {
        WriteoffRequest req = new WriteoffRequest();
        req.setRequestNo(requestNo);
        req.setReason(reason);
        req.setOperator("李四");
        return req;
    }

    private String uniq(String prefix) {
        return prefix + SEQ.incrementAndGet();
    }
}
