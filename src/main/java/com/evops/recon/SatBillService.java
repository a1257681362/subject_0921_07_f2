package com.evops.recon;

import com.evops.common.BizType;
import com.evops.common.BusinessException;
import com.evops.recon.dto.SatBillImportRequest;
import com.evops.recon.dto.SatBillLineItem;
import com.evops.recon.entity.SatBillLine;
import com.evops.recon.mapper.SatBillLineMapper;
import com.evops.request.RequestRecordService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 卫星平台账单导入：对方每日把账单明细推送进来。
 * 整批一个请求号，台账登记与明细落库同一事务；
 * 同账期同单号重复推送由 uk_sat_bill_line 兜底拒绝。
 */
@Service
public class SatBillService {

    private final SatBillLineMapper satBillLineMapper;
    private final RequestRecordService requestRecordService;

    public SatBillService(SatBillLineMapper satBillLineMapper,
                          RequestRecordService requestRecordService) {
        this.satBillLineMapper = satBillLineMapper;
        this.requestRecordService = requestRecordService;
    }

    @Transactional(rollbackFor = Exception.class)
    public int importBill(SatBillImportRequest req) {
        requestRecordService.register(req.getRequestNo(), BizType.SAT_BILL_IMPORT,
                String.valueOf(req.getBillDate()));

        int imported = 0;
        for (SatBillLineItem item : req.getLines()) {
            SatBillLine line = new SatBillLine();
            line.setBillDate(req.getBillDate());
            line.setOrderNo(item.getOrderNo());
            line.setMachineCode(item.getMachineCode());
            line.setTrackArea(item.getTrackArea());
            line.setSourceBatch(req.getRequestNo());
            line.setRemark(item.getRemark());
            try {
                satBillLineMapper.insert(line);
            } catch (DuplicateKeyException e) {
                throw new BusinessException("账期 " + req.getBillDate() + " 单号 "
                        + item.getOrderNo() + " 的账单已存在，请勿重复推送");
            }
            imported++;
        }
        return imported;
    }
}
