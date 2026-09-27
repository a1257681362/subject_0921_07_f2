package com.evops.api;

import com.evops.machine.MachineService;
import com.evops.machine.dto.MachineCreateRequest;
import com.evops.machine.entity.Machine;
import com.evops.workorder.WorkOrderService;
import com.evops.workorder.dto.TrackAreaRequest;
import com.evops.workorder.dto.WorkOrderCreateRequest;
import com.evops.workorder.entity.WorkOrder;
import com.evops.workorder.mapper.WorkOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 对账接口层：统一返回结构、必填校验、导入→勾稽→差异→核销→清零闭环。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReconApiTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final LocalDate BIZ_DATE = LocalDate.of(2026, 3, 1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MachineService machineService;
    @Autowired
    private WorkOrderService workOrderService;
    @Autowired
    private WorkOrderMapper workOrderMapper;

    @Test
    void importBill_missingFields_returnsUnifiedFail() throws Exception {
        mockMvc.perform(post("/api/recon/sat-bills/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("不能为空")));
    }

    @Test
    void writeoff_missingReason_returnsUnifiedFail() throws Exception {
        mockMvc.perform(post("/api/recon/diffs/1/writeoff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestNo\":\"REQ-API-WOFF-X\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("核销原因")));
    }

    @Test
    void reconLoop_importRunDiffsWriteoff_clearedImmediately() throws Exception {
        // 准备本地账：作业单 + 轨迹面积 10.00，登记时间回放到本用例账期
        WorkOrder order = prepareOrderWithTrackArea(new BigDecimal("10.00"));

        // 对方推送账单：同一单 9.00，差 1 亩
        String importBody = "{\"requestNo\":\"" + uniq("REQ-API-IMP-")
                + "\",\"billDate\":\"" + BIZ_DATE
                + "\",\"lines\":[{\"orderNo\":\"" + order.getOrderNo() + "\",\"trackArea\":9.00}]}";
        mockMvc.perform(post("/api/recon/sat-bills/import")
                        .contentType(MediaType.APPLICATION_JSON).content(importBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(1));

        // 手工触发勾稽
        mockMvc.perform(post("/api/recon/run").param("bizDate", BIZ_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.engine").value("LEGACY"))
                .andExpect(jsonPath("$.data.triggerType").value("MANUAL"))
                .andExpect(jsonPath("$.data.diffCount").value(1));

        // 差异清单带双方明细
        String diffsResponse = mockMvc.perform(get("/api/recon/diffs").param("bizDate", BIZ_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].orderNo").value(order.getOrderNo()))
                .andExpect(jsonPath("$.data[0].diffType").value("AREA_DIFF"))
                .andExpect(jsonPath("$.data[0].localTrackArea").value(10.00))
                .andExpect(jsonPath("$.data[0].remoteTrackArea").value(9.00))
                .andExpect(jsonPath("$.data[0].diffArea").value(1.00))
                .andExpect(jsonPath("$.data[0].status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        Long diffId = ((Number) com.jayway.jsonpath.JsonPath.read(diffsResponse, "$.data[0].id")).longValue();

        // 核销 → 差异即时清零
        String writeoffBody = "{\"requestNo\":\"" + uniq("REQ-API-WOFF-")
                + "\",\"reason\":\"与对方核对为轨迹重算差异\",\"operator\":\"王五\"}";
        mockMvc.perform(post("/api/recon/diffs/" + diffId + "/writeoff")
                        .contentType(MediaType.APPLICATION_JSON).content(writeoffBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.adjustArea").value(1.00))
                .andExpect(jsonPath("$.data.orderNo").value(order.getOrderNo()));

        mockMvc.perform(get("/api/recon/diffs").param("bizDate", BIZ_DATE.toString()))
                .andExpect(jsonPath("$.data", empty()));

        // 已核销状态可查；并跑对比记录也在
        mockMvc.perform(get("/api/recon/diffs")
                        .param("bizDate", BIZ_DATE.toString()).param("status", "WRITTEN_OFF"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("WRITTEN_OFF"));
        mockMvc.perform(get("/api/recon/compares")
                        .param("from", BIZ_DATE.toString()).param("to", BIZ_DATE.toString()))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].activeEngine").value("LEGACY"));
    }

    private WorkOrder prepareOrderWithTrackArea(BigDecimal area) {
        MachineCreateRequest machineReq = new MachineCreateRequest();
        machineReq.setMachineCode(uniq("M-API-RC-"));
        machineReq.setMachineName("对账接口测试机");
        machineReq.setRequestNo(uniq("REQ-API-M-"));
        Machine machine = machineService.create(machineReq);

        WorkOrderCreateRequest createReq = new WorkOrderCreateRequest();
        createReq.setOrderNo(uniq("WO-API-RC-"));
        createReq.setMachineId(machine.getId());
        createReq.setOperationType("收割");
        createReq.setRequestNo(uniq("REQ-API-WO-"));
        WorkOrder order = workOrderService.create(createReq);

        TrackAreaRequest trackReq = new TrackAreaRequest();
        trackReq.setTrackArea(area);
        trackReq.setRequestNo(uniq("REQ-API-TRACK-"));
        WorkOrder tracked = workOrderService.reportTrackArea(order.getId(), trackReq);
        tracked.setTrackTime(BIZ_DATE.atTime(9, 0));
        workOrderMapper.updateById(tracked);
        return tracked;
    }

    private String uniq(String prefix) {
        return prefix + SEQ.incrementAndGet();
    }
}
