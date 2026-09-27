package com.evops.recon.engine;

import com.evops.recon.ReconDiffType;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 按作业单号一笔对一笔的配对骨架：
 * 双方按单号分组后同号依次配对，配上的由子类判定面积是否算平，
 * 配不上/配不平的进差异；单边剩余记 ONLY_LOCAL / ONLY_REMOTE。
 * 输出按单号排序，保证同一套账重跑结果稳定可比。
 */
public abstract class AbstractOrderNoEngine implements ReconEngine {

    @Override
    public ReconOutcome reconcile(List<ReconLine> localLines, List<ReconLine> remoteLines) {
        Map<String, Deque<ReconLine>> localByNo = groupByOrderNo(localLines);
        Map<String, Deque<ReconLine>> remoteByNo = groupByOrderNo(remoteLines);

        ReconOutcome outcome = new ReconOutcome();
        List<String> orderNos = new ArrayList<>(localByNo.keySet());
        for (String no : remoteByNo.keySet()) {
            if (!localByNo.containsKey(no)) {
                orderNos.add(no);
            }
        }
        orderNos.sort(String::compareTo);

        for (String orderNo : orderNos) {
            Deque<ReconLine> locals = localByNo.getOrDefault(orderNo, new ArrayDeque<>());
            Deque<ReconLine> remotes = remoteByNo.getOrDefault(orderNo, new ArrayDeque<>());

            while (!locals.isEmpty() && !remotes.isEmpty()) {
                ReconLine local = locals.poll();
                ReconLine remote = remotes.poll();
                if (areasMatch(local.getArea(), remote.getArea())) {
                    outcome.setMatchedCount(outcome.getMatchedCount() + 1);
                } else {
                    outcome.getDiffs().add(areaDiff(orderNo, local, remote));
                }
            }
            for (ReconLine local : locals) {
                outcome.getDiffs().add(onlyLocal(orderNo, local));
            }
            for (ReconLine remote : remotes) {
                outcome.getDiffs().add(onlyRemote(orderNo, remote));
            }
        }
        return outcome;
    }

    /** 配对成功后判定两边面积是否算平（旧引擎严格等额，新引擎容差内算平）。 */
    protected abstract boolean areasMatch(BigDecimal localArea, BigDecimal remoteArea);

    private Map<String, Deque<ReconLine>> groupByOrderNo(List<ReconLine> lines) {
        return lines.stream().collect(Collectors.groupingBy(
                ReconLine::getOrderNo, TreeMap::new,
                Collectors.toCollection(ArrayDeque::new)));
    }

    private ReconDiffItem areaDiff(String orderNo, ReconLine local, ReconLine remote) {
        ReconDiffItem item = new ReconDiffItem();
        item.setOrderNo(orderNo);
        item.setDiffType(ReconDiffType.AREA_DIFF);
        item.setLocalOrderId(local.getRefId());
        item.setLocalTrackArea(local.getArea());
        item.setRemoteTrackArea(remote.getArea());
        item.setDiffArea(local.getArea().subtract(remote.getArea()));
        return item;
    }

    private ReconDiffItem onlyLocal(String orderNo, ReconLine local) {
        ReconDiffItem item = new ReconDiffItem();
        item.setOrderNo(orderNo);
        item.setDiffType(ReconDiffType.ONLY_LOCAL);
        item.setLocalOrderId(local.getRefId());
        item.setLocalTrackArea(local.getArea());
        item.setDiffArea(local.getArea());
        return item;
    }

    private ReconDiffItem onlyRemote(String orderNo, ReconLine remote) {
        ReconDiffItem item = new ReconDiffItem();
        item.setOrderNo(orderNo);
        item.setDiffType(ReconDiffType.ONLY_REMOTE);
        item.setRemoteTrackArea(remote.getArea());
        item.setDiffArea(remote.getArea().negate());
        return item;
    }
}
