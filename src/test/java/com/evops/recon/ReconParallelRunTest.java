package com.evops.recon;

import com.evops.machine.MachineService;
import com.evops.machine.dto.MachineCreateRequest;
import com.evops.machine.entity.Machine;
import com.evops.recon.dto.SatBillImportRequest;
import com.evops.recon.dto.SatBillLineItem;
import com.evops.recon.entity.ReconCompare;
import com.evops.recon.entity.ReconRun;
import com.evops.workorder.WorkOrderService;
import com.evops.workorder.dto.TrackAreaRequest;
import com.evops.workorder.dto.WorkOrderCreateRequest;
import com.evops.workorder.entity.WorkOrder;
import com.evops.workorder.mapper.WorkOrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 新旧引擎并跑：每次勾稽双引擎都跑，对比落 t_recon_compare；
 * active-engine 决定正式差异清单；容差可配置即时影响新引擎结果。
 */
@SpringBootTest
@ActiveProfiles("test")
class ReconParallelRunTest {

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
    private ReconProperties properties;

    @BeforeEach
    void setUp() {
        properties.setActiveEngine(ReconEngineType.LEGACY);
        properties.setToleranceArea(new BigDecimal("0.05"));
    }

    @AfterEach
    void tearDown() {
        // 配置是单例 Bean、跨测试类共享，用完归位
        properties.setActiveEngine(ReconEngineType.LEGACY);
        properties.setToleranceArea(new BigDecimal("0.05"));
    }

    @Test
    void enginesDisagree_compareRecordsBothSides() {
        LocalDate bizDate = LocalDate.of(2026, 2, 20);
        // 差 0.03 亩：旧引擎（严格等额）算差异，新引擎（容差 0.05）算平
        WorkOrder order = orderWithTrackArea("WO-PP-GAP-", "10.00", bizDate);
        importBill(bizDate, order.getOrderNo(), "10.03");

        ReconRun run = reconService.reconcile(bizDate, ReconTriggerType.MANUAL);
        assertEquals(ReconEngineType.LEGACY, run.getEngine());
        assertEquals(0, run.getMatchedCount());
        assertEquals(1, run.getDiffCount(), "正式清单按旧引擎出：1 条差异");
        assertEquals(1, reconService.listDiffs(bizDate, ReconDiffStatus.OPEN).size());

        List<ReconCompare> compares = reconService.listCompares(bizDate, bizDate);
        assertEquals(1, compares.size());
        ReconCompare compare = compares.get(0);
        assertEquals(ReconEngineType.LEGACY, compare.getActiveEngine());
        assertEquals("0", compare.getIdentical(), "双引擎数字对不上");
        assertEquals(0, compare.getLegacyMatched());
        assertEquals(1, compare.getLegacyDiffCount());
        assertEquals(1, compare.getToleranceMatched());
        assertEquals(0, compare.getToleranceDiffCount());
        assertEquals(0, new BigDecimal("-0.03").compareTo(compare.getLegacyDiffArea()));
        assertEquals(0, BigDecimal.ZERO.compareTo(compare.getToleranceDiffArea()));
    }

    @Test
    void enginesAgree_identicalFlagSet() {
        LocalDate bizDate = LocalDate.of(2026, 2, 21);
        WorkOrder order = orderWithTrackArea("WO-PP-SAME-", "7.00", bizDate);
        importBill(bizDate, order.getOrderNo(), "7.00");

        reconService.reconcile(bizDate, ReconTriggerType.MANUAL);
        ReconCompare compare = reconService.listCompares(bizDate, bizDate).get(0);
        assertEquals("1", compare.getIdentical(), "数字对得上：勾平/差异/合计都一致");
        assertEquals(compare.getLegacyMatched(), compare.getToleranceMatched());
        assertEquals(compare.getLegacyDiffCount(), compare.getToleranceDiffCount());
    }

    @Test
    void switchActiveEngine_formalDiffsFollowSwitch() {
        LocalDate bizDate = LocalDate.of(2026, 2, 22);
        WorkOrder order = orderWithTrackArea("WO-PP-SW-", "10.00", bizDate);
        importBill(bizDate, order.getOrderNo(), "10.03");

        reconService.reconcile(bizDate, ReconTriggerType.MANUAL);
        assertEquals(1, reconService.listDiffs(bizDate, ReconDiffStatus.OPEN).size());

        // 并跑验证后切到新引擎重跑：容差内算平，正式差异清零
        properties.setActiveEngine(ReconEngineType.TOLERANCE);
        ReconRun run = reconService.reconcile(bizDate, ReconTriggerType.MANUAL);
        assertEquals(ReconEngineType.TOLERANCE, run.getEngine());
        assertEquals(1, run.getMatchedCount());
        assertEquals(0, run.getDiffCount());
        assertTrue(reconService.listDiffs(bizDate, ReconDiffStatus.OPEN).isEmpty());
    }

    @Test
    void toleranceConfigurable_changesNewEngineResult() {
        LocalDate bizDate = LocalDate.of(2026, 2, 23);
        WorkOrder order = orderWithTrackArea("WO-PP-TOL-", "10.00", bizDate);
        importBill(bizDate, order.getOrderNo(), "10.30");

        reconService.reconcile(bizDate, ReconTriggerType.MANUAL);
        ReconCompare tight = reconService.listCompares(bizDate, bizDate).get(0);
        assertEquals(1, tight.getToleranceDiffCount(), "0.30 超 0.05 容差，新引擎也算差异");

        // 容差调到 0.50 后重跑：同一套账，新引擎勾平
        properties.setToleranceArea(new BigDecimal("0.50"));
        reconService.reconcile(bizDate, ReconTriggerType.MANUAL);
        List<ReconCompare> compares = reconService.listCompares(bizDate, bizDate);
        assertEquals(2, compares.size());
        ReconCompare loose = compares.get(1);
        assertEquals(1, loose.getToleranceMatched());
        assertEquals(0, loose.getToleranceDiffCount());
    }

    private WorkOrder orderWithTrackArea(String orderNoPrefix, String area, LocalDate bizDate) {
        MachineCreateRequest machineReq = new MachineCreateRequest();
        machineReq.setMachineCode(uniq("M-PP-"));
        machineReq.setMachineName("并跑测试机");
        machineReq.setRequestNo(uniq("REQ-PP-M-"));
        Machine machine = machineService.create(machineReq);

        WorkOrderCreateRequest createReq = new WorkOrderCreateRequest();
        createReq.setOrderNo(uniq(orderNoPrefix));
        createReq.setMachineId(machine.getId());
        createReq.setOperationType("播种");
        createReq.setRequestNo(uniq("REQ-PP-WO-"));
        WorkOrder order = workOrderService.create(createReq);

        TrackAreaRequest trackReq = new TrackAreaRequest();
        trackReq.setTrackArea(new BigDecimal(area));
        trackReq.setRequestNo(uniq("REQ-PP-TRACK-"));
        WorkOrder tracked = workOrderService.reportTrackArea(order.getId(), trackReq);
        tracked.setTrackTime(bizDate.atTime(10, 0));
        workOrderMapper.updateById(tracked);
        return tracked;
    }

    private void importBill(LocalDate bizDate, String orderNo, String area) {
        SatBillImportRequest req = new SatBillImportRequest();
        req.setRequestNo(uniq("REQ-PP-IMP-"));
        req.setBillDate(bizDate);
        SatBillLineItem item = new SatBillLineItem();
        item.setOrderNo(orderNo);
        item.setTrackArea(new BigDecimal(area));
        req.setLines(Collections.singletonList(item));
        satBillService.importBill(req);
    }

    private String uniq(String prefix) {
        return prefix + SEQ.incrementAndGet();
    }
}
