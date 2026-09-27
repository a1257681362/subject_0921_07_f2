package com.evops.recon.engine;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 一次勾稽的引擎产出：勾平笔数 + 差异明细。 */
@Data
public class ReconOutcome {
    private int matchedCount;
    private List<ReconDiffItem> diffs = new ArrayList<>();

    /** 差异面积合计（带符号：正=本侧多记，负=对方多记）。 */
    public BigDecimal totalDiffArea() {
        BigDecimal total = BigDecimal.ZERO;
        for (ReconDiffItem diff : diffs) {
            if (diff.getDiffArea() != null) {
                total = total.add(diff.getDiffArea());
            }
        }
        return total;
    }
}
