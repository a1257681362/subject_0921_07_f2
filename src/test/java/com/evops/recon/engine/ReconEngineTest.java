package com.evops.recon.engine;

import com.evops.recon.ReconDiffType;
import com.evops.recon.ReconProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 勾稽引擎纯单元测试：不启 Spring，直接构造。 */
class ReconEngineTest {

    private final LegacyReconEngine legacy = new LegacyReconEngine();
    private final ReconProperties properties = new ReconProperties();
    private final ToleranceReconEngine tolerance = new ToleranceReconEngine(properties);

    private static ReconLine line(String orderNo, String area, Long refId) {
        return new ReconLine(orderNo, new BigDecimal(area), refId);
    }

    @Test
    void legacy_exactEqualityOnly() {
        ReconOutcome outcome = legacy.reconcile(
                Arrays.asList(line("A", "10.00", 1L), line("B", "5.00", 2L)),
                Arrays.asList(line("A", "10.00", 11L), line("B", "5.01", 12L)));

        assertEquals(1, outcome.getMatchedCount());
        assertEquals(1, outcome.getDiffs().size());
        ReconDiffItem diff = outcome.getDiffs().get(0);
        assertEquals("B", diff.getOrderNo());
        assertEquals(ReconDiffType.AREA_DIFF, diff.getDiffType());
        assertEquals(2L, diff.getLocalOrderId());
        assertEquals(0, new BigDecimal("5.00").compareTo(diff.getLocalTrackArea()));
        assertEquals(0, new BigDecimal("5.01").compareTo(diff.getRemoteTrackArea()));
        assertEquals(0, new BigDecimal("-0.01").compareTo(diff.getDiffArea()));
    }

    @Test
    void tolerance_withinAndOnBoundary_matched_beyondIsDiff() {
        properties.setToleranceArea(new BigDecimal("0.05"));
        ReconOutcome outcome = tolerance.reconcile(
                Arrays.asList(line("A", "10.00", 1L), line("B", "5.00", 2L), line("C", "3.00", 3L)),
                Arrays.asList(line("A", "10.03", 11L), line("B", "5.05", 12L), line("C", "3.06", 13L)));

        // A 差 0.03 容差内、B 差 0.05 等于容差也算平、C 差 0.06 超容差进差异
        assertEquals(2, outcome.getMatchedCount());
        assertEquals(1, outcome.getDiffs().size());
        assertEquals("C", outcome.getDiffs().get(0).getOrderNo());
    }

    @Test
    void oneSided_onlyLocalAndOnlyRemote() {
        ReconOutcome outcome = legacy.reconcile(
                Collections.singletonList(line("L1", "7.00", 1L)),
                Collections.singletonList(line("R1", "8.00", 2L)));

        assertEquals(0, outcome.getMatchedCount());
        assertEquals(2, outcome.getDiffs().size());
        // 输出按单号排序，L1 在前
        ReconDiffItem onlyLocal = outcome.getDiffs().get(0);
        assertEquals(ReconDiffType.ONLY_LOCAL, onlyLocal.getDiffType());
        assertEquals(0, new BigDecimal("7.00").compareTo(onlyLocal.getDiffArea()));
        assertNull(onlyLocal.getRemoteTrackArea());
        ReconDiffItem onlyRemote = outcome.getDiffs().get(1);
        assertEquals(ReconDiffType.ONLY_REMOTE, onlyRemote.getDiffType());
        assertEquals(0, new BigDecimal("-8.00").compareTo(onlyRemote.getDiffArea()));
        assertNull(onlyRemote.getLocalTrackArea());
    }

    @Test
    void duplicateRemoteOrderNo_pairedOneToOne_leftoverIsDiff() {
        // 对方同一单号推了两条：一条与本地勾平，剩余一条记 ONLY_REMOTE
        ReconOutcome outcome = legacy.reconcile(
                Collections.singletonList(line("A", "10.00", 1L)),
                Arrays.asList(line("A", "10.00", 11L), line("A", "10.00", 12L)));

        assertEquals(1, outcome.getMatchedCount());
        assertEquals(1, outcome.getDiffs().size());
        assertEquals(ReconDiffType.ONLY_REMOTE, outcome.getDiffs().get(0).getDiffType());
    }

    @Test
    void totalDiffArea_signedSum() {
        ReconOutcome outcome = legacy.reconcile(
                Arrays.asList(line("A", "10.00", 1L), line("B", "1.00", 2L)),
                Arrays.asList(line("A", "9.00", 11L), line("C", "2.00", 12L)));

        // A 差 +1.00，B 本地单边 +1.00，C 对方单边 -2.00 → 合计 0
        assertEquals(3, outcome.getDiffs().size());
        assertTrue(BigDecimal.ZERO.compareTo(outcome.totalDiffArea()) == 0);
    }
}
