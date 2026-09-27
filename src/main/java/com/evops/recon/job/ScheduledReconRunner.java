package com.evops.recon.job;

import com.evops.recon.ReconService;
import com.evops.recon.ReconTriggerType;
import com.evops.recon.entity.ReconRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 每日定时勾稽入口（Quartz 调用）：T+1 对前一日的账。
 * 失败不吞：记日志 + 独立事务落一笔 FAILED 批次，便于第二天排查补跑。
 */
@Component
public class ScheduledReconRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduledReconRunner.class);

    private final ReconService reconService;

    public ScheduledReconRunner(ReconService reconService) {
        this.reconService = reconService;
    }

    /** 由 Quartz 按 evops.recon.cron 触发。 */
    public void runDaily() {
        LocalDate bizDate = LocalDate.now().minusDays(1);
        try {
            ReconRun run = reconService.reconcile(bizDate, ReconTriggerType.SCHEDULED);
            log.info("每日勾稽完成: bizDate={}, runNo={}, matched={}, diffs={}, diffArea={}",
                    bizDate, run.getRunNo(), run.getMatchedCount(), run.getDiffCount(),
                    run.getTotalDiffArea());
        } catch (Exception e) {
            log.error("每日勾稽失败: bizDate={}", bizDate, e);
            try {
                reconService.recordFailure(bizDate, ReconTriggerType.SCHEDULED, e.getMessage());
            } catch (Exception recordError) {
                log.error("勾稽失败留痕也失败了: bizDate={}", bizDate, recordError);
            }
        }
    }
}
