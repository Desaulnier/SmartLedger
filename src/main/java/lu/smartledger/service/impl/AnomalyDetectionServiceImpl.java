package lu.smartledger.service.impl;

import lu.smartledger.model.domain.Bills;
import lu.smartledger.service.AnomalyDetectionService;
import org.springframework.stereotype.Service;
import smile.anomaly.IsolationForest;
import smile.math.matrix.Matrix;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class AnomalyDetectionServiceImpl implements AnomalyDetectionService {

    private IsolationForest model;
    private void trainModel(List<Bills> history) {
        if (history.size() < 5) return;

        double[][] data = history.stream()
                .map(this::buildFeature)
                .toArray(double[][]::new);

        Matrix X = Matrix.of(data);
        this.model = IsolationForest.fit(X.toArray()); // 3.x 官方标准方法
    }

    // ==============================
    // 在线检测（每笔账单单独打分）
    // ==============================
    @Override
    public void detectAnomaly(Bills bill, List<Bills> historyBills) {
        if (!"EXPENSE".equals(bill.getBillType()) || bill.getAmount() == null) return;

        if (model == null) {
            trainModel(historyBills);
        }

        double[] feature = buildFeature(bill);
        double modelScore = model.score(feature);

        // 统计规则（混合方案）
        double ruleScore = calculateRuleScore(bill, historyBills);
        double finalScore = (modelScore + ruleScore) / 2;

        // 阈值 0.65
        if (finalScore > 0.65) {
            bill.setAnomalyScore(BigDecimal.valueOf(finalScore).setScale(2, BigDecimal.ROUND_HALF_UP));
            bill.setAnomalyType(finalScore > 0.8 ? "SEVERE" : "WARNING");
            bill.setAnomalyReason("孤立森林+统计规则：消费行为异常");
        }
    }

    // 4个特征：金额、小时、频率、分类
    private double[] buildFeature(Bills bill) {
        return new double[]{
                bill.getAmount().doubleValue(),
                bill.getOccurTime().getHour(),
                0,
                bill.getCategoryId() != null ? bill.getCategoryId().doubleValue() : 0
        };
    }

    private double calculateRuleScore(Bills current, List<Bills> history) {
        double mean = history.stream().mapToDouble(b -> b.getAmount().doubleValue()).average().orElse(0);
        double amountScore = Math.abs(current.getAmount().doubleValue() - mean) / (mean + 1e-6);

        int hour = current.getOccurTime().getHour();
        double hourScore = (hour >= 22 || hour <= 6) ? 1.0 : 0.1;

        long count = history.stream().filter(b -> ChronoUnit.HOURS.between(b.getOccurTime(), current.getOccurTime()) < 24).count();
        double freqScore = Math.min(count / 5.0, 1.0);

        return (amountScore * 0.5 + hourScore * 0.3 + freqScore * 0.2);
    }
}