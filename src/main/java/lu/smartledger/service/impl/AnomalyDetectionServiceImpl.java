package lu.smartledger.service.impl;

import lu.smartledger.model.domain.Bills;
import lu.smartledger.service.AnomalyDetectionService;
import org.springframework.stereotype.Service;
import smile.anomaly.IsolationForest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AnomalyDetectionServiceImpl implements AnomalyDetectionService {

    private static final int MIN_HISTORY_SIZE = 12;
    private static final BigDecimal DEFAULT_NORMAL_SCORE = BigDecimal.valueOf(0.10);
    private static final BigDecimal DEFAULT_ABNORMAL_SCORE = BigDecimal.valueOf(0.85);

    /**
     * 检测异常
     *
     * @param bill
     * @param historyBills
     */
    @Override
    public void detectAnomaly(Bills bill, List<Bills> historyBills) {
        if (bill == null || !"EXPENSE".equals(bill.getBillType()) || bill.getAmount() == null) {
            return;
        }
        List<Bills> validHistory = filterExpenseHistory(historyBills);
        if (validHistory.size() < MIN_HISTORY_SIZE) {
            applyFallbackDetection(bill, validHistory);
            return;
        }
        try {
            double[][] train = buildFeatureMatrix(validHistory);// 构建训练集特征矩阵
            double[] current = buildFeatures(bill);// 构建当前记录特征向量
            IsolationForest forest = IsolationForest.fit(train);// 训练异常检测模型
            double rawScore = forest.score(current);// 计算原始异常分数
            double[] historyScores = forest.score(train);// 计算历史记录异常分数
            double normalizedScore = normalizeScore(rawScore, historyScores);// 归一化异常分数
            String anomalyType = resolveAnomalyType(bill, validHistory, normalizedScore);// 解析异常类型
            String anomalyReason = buildReason(bill, validHistory, normalizedScore, anomalyType);// 构建异常原因
            bill.setAnomalyScore(BigDecimal.valueOf(normalizedScore).setScale(4, RoundingMode.HALF_UP));// 设置异常分数
            if (normalizedScore >= 0.68) {
                bill.setAnomalyType(anomalyType);
                bill.setAnomalyReason(anomalyReason);
            } else {
                bill.setAnomalyType(null);
                bill.setAnomalyReason(null);
            }
        } catch (Exception e) {
            applyFallbackDetection(bill, validHistory);
        }
    }

    /**
     * 清洗历史记录，只保留有效支出记录
     *
     * @param historyBills
     * @return
     */
    private List<Bills> filterExpenseHistory(List<Bills> historyBills) {
        List<Bills> result = new ArrayList<>();
        if (historyBills == null) {
            return result;
        }

        for (Bills item : historyBills) {
            if (item == null) {
                continue;
            }
            if (!"EXPENSE".equals(item.getBillType())) {
                continue;
            }
            if (item.getAmount() == null || item.getOccurTime() == null) {
                continue;
            }
            result.add(item);
        }
        return result;
    }

    /**
     * 构建特征矩阵
     *
     * @param historyBills
     * @return
     */
    private double[][] buildFeatureMatrix(List<Bills> historyBills) {
        double[][] matrix = new double[historyBills.size()][];// 特征矩阵
        for (int i = 0; i < historyBills.size(); i++) { // 遍历历史记录
            matrix[i] = buildFeatures(historyBills.get(i));
        }
        return matrix;
    }

    /**
     * 构建特征向量
     *
     * @param bill
     * @return
     */
    private double[] buildFeatures(Bills bill) {
        LocalDateTime occurTime = bill.getOccurTime() != null ? bill.getOccurTime() : LocalDateTime.now();
        double amount = bill.getAmount() != null ? bill.getAmount().doubleValue() : 0D;
        double logAmount = Math.log1p(Math.max(amount, 0D));
        double hour = occurTime.getHour() / 23.0;
        double dayOfWeek = occurTime.getDayOfWeek().getValue() / 7.0;
        double attribute = bill.getConsumptionAttribute() != null ? bill.getConsumptionAttribute() / 3.0 : 0.0;

        return new double[] { logAmount, hour, dayOfWeek, attribute };
    }

    /**
     * 归一化分数
     *
     * @param rawScore
     * @param historyScores
     * @return
     */
    private double normalizeScore(double rawScore, double[] historyScores) {
        double min = rawScore;
        double max = rawScore;

        for (double score : historyScores) {
            min = Math.min(min, score);
            max = Math.max(max, score);
        }

        if (Math.abs(max - min) < 1e-9) {
            return 0.10;
        }

        double normalized = (rawScore - min) / (max - min);
        if (normalized < 0) {
            normalized = 0;
        }
        if (normalized > 1) {
            normalized = 1;
        }
        return normalized;
    }

    /**
     * 解析异常类型
     *
     * @param bill
     * @param historyBills
     * @param normalizedScore
     * @return
     */
    private String resolveAnomalyType(Bills bill, List<Bills> historyBills, double normalizedScore) {
        BigDecimal avgAmount = averageAmount(historyBills);
        int currentHour = bill.getOccurTime() != null ? bill.getOccurTime().getHour() : 12;

        if (bill.getAmount().compareTo(avgAmount.multiply(BigDecimal.valueOf(2))) > 0) {
            return "AMOUNT_ABNORMAL";
        }

        if (currentHour <= 5 || currentHour >= 23) {
            return "TIME_ABNORMAL";
        }

        if (normalizedScore >= 0.80) {
            return "COMPOSITE_ABNORMAL";
        }

        return "BEHAVIOR_ABNORMAL";
    }

    /**
     * 构建异常原因
     *
     * @param bill
     * @param historyBills
     * @param normalizedScore
     * @param anomalyType
     * @return
     */
    private String buildReason(Bills bill, List<Bills> historyBills, double normalizedScore, String anomalyType) {
        BigDecimal avgAmount = averageAmount(historyBills).setScale(2, RoundingMode.HALF_UP);
        int currentHour = bill.getOccurTime() != null ? bill.getOccurTime().getHour() : 12;

        if ("AMOUNT_ABNORMAL".equals(anomalyType)) {
            return "该笔消费金额明显高于历史平均水平，当前金额为 ¥"
                    + bill.getAmount().setScale(2, RoundingMode.HALF_UP)
                    + "，历史平均约为 ¥" + avgAmount;
        }

        if ("TIME_ABNORMAL".equals(anomalyType)) {
            return "该笔消费发生在非常规消费时段，当前时间为 "
                    + String.format("%02d:00", currentHour)
                    + "，与日常消费时间分布差异较大";
        }

        if ("COMPOSITE_ABNORMAL".equals(anomalyType)) {
            return "该笔消费在金额、时段或消费属性上与历史行为差异较大，异常评分为 "
                    + BigDecimal.valueOf(normalizedScore).setScale(2, RoundingMode.HALF_UP);
        }

        return "该笔消费与历史消费模式存在偏离，异常评分为 "
                + BigDecimal.valueOf(normalizedScore).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 获取历史平均金额
     *
     * @param historyBills
     * @return
     */
    private BigDecimal averageAmount(List<Bills> historyBills) {
        if (historyBills == null || historyBills.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (Bills item : historyBills) {
            if (item.getAmount() != null) {
                sum = sum.add(item.getAmount());
            }
        }

        return sum.divide(BigDecimal.valueOf(historyBills.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * 默认异常处理
     *
     * @param bill
     * @param historyBills
     */
    private void applyFallbackDetection(Bills bill, List<Bills> historyBills) {
        BigDecimal avgAmount = averageAmount(historyBills);
        int hour = bill.getOccurTime() != null ? bill.getOccurTime().getHour() : 12;

        boolean amountAbnormal = avgAmount.compareTo(BigDecimal.ZERO) > 0
                && bill.getAmount().compareTo(avgAmount.multiply(BigDecimal.valueOf(2))) > 0;
        boolean timeAbnormal = hour <= 5 || hour >= 23;

        if (amountAbnormal && timeAbnormal) {
            bill.setAnomalyScore(BigDecimal.valueOf(0.90).setScale(4, RoundingMode.HALF_UP));
            bill.setAnomalyType("COMPOSITE_ABNORMAL");
            bill.setAnomalyReason("该笔消费同时存在金额偏高和消费时段异常的情况");
        } else if (amountAbnormal) {
            bill.setAnomalyScore(DEFAULT_ABNORMAL_SCORE.setScale(4, RoundingMode.HALF_UP));
            bill.setAnomalyType("AMOUNT_ABNORMAL");
            bill.setAnomalyReason("该笔消费金额明显高于历史平均水平");
        } else if (timeAbnormal) {
            bill.setAnomalyScore(BigDecimal.valueOf(0.72).setScale(4, RoundingMode.HALF_UP));
            bill.setAnomalyType("TIME_ABNORMAL");
            bill.setAnomalyReason("该笔消费发生在非常规消费时段");
        } else {
            bill.setAnomalyScore(DEFAULT_NORMAL_SCORE.setScale(4, RoundingMode.HALF_UP));
            bill.setAnomalyType(null);
            bill.setAnomalyReason(null);
        }
    }
}
