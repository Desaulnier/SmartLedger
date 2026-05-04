package lu.smartledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lu.smartledger.common.utls.JsonResponse;
import lu.smartledger.mapper.BillsMapper;
import lu.smartledger.mapper.UsersMapper;
import lu.smartledger.mapper.WarningRecordsMapper;
import lu.smartledger.model.domain.Bills;
import lu.smartledger.model.domain.Users;
import lu.smartledger.model.domain.WarningRecords;
import lu.smartledger.service.IWarningRecordsService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WarningRecordsServiceImpl extends ServiceImpl<WarningRecordsMapper, WarningRecords> implements IWarningRecordsService {

    private final UsersMapper usersMapper;
    private final BillsMapper billsMapper;

    private static class BudgetWarningResult {
        private String warningLevel;
        private String message;
        private BigDecimal monthlyLimit;
        private BigDecimal currentSpent;
        private BigDecimal warningThreshold;
        private BigDecimal usageRate;
        private BigDecimal predictTotal;
        private BigDecimal predictOver;
        private Integer remainingDays;
    }

    private BigDecimal calcCurrentMonthSpent(Long userId) {
        if (userId == null) {
            return BigDecimal.ZERO;
        }

        LocalDate now = LocalDate.now();
        LocalDateTime monthStart = now.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
        LocalDateTime monthEnd = now.with(TemporalAdjusters.lastDayOfMonth()).atTime(23, 59, 59);

        List<Bills> bills = billsMapper.selectList(
                new LambdaQueryWrapper<Bills>()
                        .eq(Bills::getUserId, userId)
                        .eq(Bills::getBillType, "EXPENSE")
                        .between(Bills::getOccurTime, monthStart, monthEnd)
        );

        BigDecimal total = BigDecimal.ZERO;
        for (Bills bill : bills) {
            if (bill.getAmount() != null) {
                total = total.add(bill.getAmount());
            }
        }
        return total;
    }

    private BigDecimal normalizeThresholdPercent(BigDecimal thresholdPercent) {
        BigDecimal finalThreshold = thresholdPercent == null ? BigDecimal.valueOf(80) : thresholdPercent;
        if (finalThreshold.compareTo(BigDecimal.ONE) <= 0) {
            finalThreshold = finalThreshold.multiply(BigDecimal.valueOf(100));
        }
        return finalThreshold;
    }

    /**
     * 构建预算预警结果
     *
     * @param user
     * @param userId
     * @return
     */
    private BudgetWarningResult buildBudgetWarningResult(Users user, Long userId) {
        BigDecimal monthlyLimit = user.getMonthlyLimit();
        BigDecimal currentSpent = calcCurrentMonthSpent(userId);
        BigDecimal thresholdPercent = normalizeThresholdPercent(user.getWarningThreshold());

        LocalDate now = LocalDate.now();
        int dayOfMonth = now.getDayOfMonth();
        int lastDay = now.with(TemporalAdjusters.lastDayOfMonth()).getDayOfMonth();

        BigDecimal progress = BigDecimal.valueOf(dayOfMonth)
                .divide(BigDecimal.valueOf(lastDay), 4, RoundingMode.HALF_UP);

        BigDecimal predictTotal = progress.compareTo(BigDecimal.ZERO) == 0
                ? currentSpent
                : currentSpent.divide(progress, 2, RoundingMode.HALF_UP);

        BigDecimal predictOver = predictTotal.subtract(monthlyLimit);
        if (predictOver.compareTo(BigDecimal.ZERO) < 0) {
            predictOver = BigDecimal.ZERO;
        }

        BigDecimal usageRateRaw = currentSpent.divide(monthlyLimit, 4, RoundingMode.HALF_UP);
        BigDecimal thresholdRate = thresholdPercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

        BudgetWarningResult result = new BudgetWarningResult();
        result.monthlyLimit = monthlyLimit;
        result.currentSpent = currentSpent;
        result.warningThreshold = thresholdPercent;
        result.usageRate = usageRateRaw.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP);
        result.predictTotal = predictTotal;
        result.predictOver = predictOver;
        result.remainingDays = lastDay - dayOfMonth + 1;

        if (predictTotal.compareTo(monthlyLimit) > 0 || usageRateRaw.compareTo(BigDecimal.ONE) >= 0) {
            result.warningLevel = "RED";
            result.message = "按当前消费趋势，本月存在明显超支风险";
        } else if (usageRateRaw.compareTo(thresholdRate) >= 0) {
            result.warningLevel = "YELLOW";
            result.message = "当前消费已达到预警阈值，请注意控制支出";
        } else {
            result.warningLevel = "GREEN";
            result.message = "当前消费状态正常，预算风险较低";
        }

        return result;
    }

    @Override
    public JsonResponse<Object> getBudgetWarning(Long userId) {
        Users user = usersMapper.selectById(userId);
        if (user == null) {
            return JsonResponse.fail("用户不存在");
        }

        if (user.getMonthlyLimit() == null || user.getMonthlyLimit().compareTo(BigDecimal.ZERO) <= 0) {
            return JsonResponse.fail("请先设置月度预算");
        }

        BudgetWarningResult result = buildBudgetWarningResult(user, userId);

        Map<String, Object> data = new HashMap<>();
        data.put("warningLevel", result.warningLevel);
        data.put("message", result.message);
        data.put("monthlyLimit", result.monthlyLimit);
        data.put("currentSpent", result.currentSpent);
        data.put("warningThreshold", result.warningThreshold);
        data.put("usageRate", result.usageRate);
        data.put("predictTotal", result.predictTotal);
        data.put("predictOver", result.predictOver);
        data.put("remainingDays", result.remainingDays);

        return JsonResponse.success("获取成功", data);
    }

    @Override
    public JsonResponse<Object> checkConsumptionWarning(Long userId, Map<String, Object> params) {
        Users user = usersMapper.selectById(userId);
        if (user == null) {
            return JsonResponse.fail("用户不存在");
        }

        if (user.getMonthlyLimit() == null || user.getMonthlyLimit().compareTo(BigDecimal.ZERO) <= 0) {
            return JsonResponse.fail("请先设置月度预算");
        }

        Object amountObj = params.get("amount");
        if (amountObj == null) {
            return JsonResponse.fail("消费金额不能为空");
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountObj.toString()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return JsonResponse.fail("消费金额格式错误");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return JsonResponse.fail("消费金额必须大于0");
        }

        Integer attribute = 1;
        Object attributeObj = params.get("attribute");
        if (attributeObj != null) {
            try {
                attribute = Integer.parseInt(attributeObj.toString());
            } catch (Exception ignored) {
                attribute = 1;
            }
        }

        BigDecimal monthlyLimit = user.getMonthlyLimit();
        BigDecimal currentSpent = calcCurrentMonthSpent(userId);

        BigDecimal remainingBudget = monthlyLimit.subtract(currentSpent);
        BigDecimal afterSpend = remainingBudget.subtract(amount);
        BigDecimal spentAfterThis = currentSpent.add(amount);

        LocalDate now = LocalDate.now();
        int dayOfMonth = now.getDayOfMonth();
        int lastDayOfMonth = now.with(TemporalAdjusters.lastDayOfMonth()).getDayOfMonth();
        int remainingDays = Math.max(1, lastDayOfMonth - dayOfMonth + 1);
        int passedDays = Math.max(1, dayOfMonth);

        BigDecimal dailyBudget = remainingBudget.compareTo(BigDecimal.ZERO) > 0
                ? remainingBudget.divide(BigDecimal.valueOf(remainingDays), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal thresholdPercent = normalizeThresholdPercent(user.getWarningThreshold());
        BigDecimal thresholdRate = thresholdPercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal currentUsageRate = currentSpent.divide(monthlyLimit, 4, RoundingMode.HALF_UP);
        BigDecimal afterUsageRate = spentAfterThis.divide(monthlyLimit, 4, RoundingMode.HALF_UP);

        BigDecimal predictedMonthTotal = spentAfterThis
                .divide(BigDecimal.valueOf(passedDays), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(lastDayOfMonth))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal predictedOver = predictedMonthTotal.subtract(monthlyLimit);
        if (predictedOver.compareTo(BigDecimal.ZERO) < 0) {
            predictedOver = BigDecimal.ZERO;
        }

        String level;
        String levelText;
        String icon;
        String title;
        String reason;
        String suggestion;

        if (afterSpend.compareTo(BigDecimal.ZERO) < 0) {
            level = "danger";
            levelText = "红灯 - 超支风险";
            icon = "🚨";
            title = "不建议消费";
            reason = "这笔消费后将超出本月预算 ¥" + afterSpend.abs().setScale(2, RoundingMode.HALF_UP);
            suggestion = attribute == 1
                    ? "如果属于必要支出，建议同步压缩本月后续非必要消费。"
                    : "建议暂缓这笔消费，优先保证本月预算平衡。";
        } else if (predictedOver.compareTo(BigDecimal.ZERO) > 0) {
            level = "warning";
            levelText = "黄灯 - 月末可能超支";
            icon = "⚠️";
            title = "预算趋势预警";
            reason = "按当前消费节奏，预计月末将超支 ¥" + predictedOver.setScale(2, RoundingMode.HALF_UP);
            suggestion = attribute == 1
                    ? "可以消费，但后续要尽量减少弹性支出。"
                    : "建议控制非必要消费，避免月底预算不足。";
        } else if (afterUsageRate.compareTo(thresholdRate) >= 0) {
            level = "warning";
            levelText = "黄灯 - 接近预算阈值";
            icon = "🟡";
            title = "阈值预警";
            reason = "消费后预算使用率将达到 "
                    + afterUsageRate.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP)
                    + "%，已接近或达到预警阈值";
            suggestion = "建议本周优先保留必要支出，减少改善型和欲望型消费。";
        } else if (attribute == 3 && dailyBudget.compareTo(BigDecimal.ZERO) > 0
                && amount.compareTo(dailyBudget.multiply(BigDecimal.valueOf(2))) > 0) {
            level = "warning";
            levelText = "黄灯 - 大额消费提醒";
            icon = "💰";
            title = "单笔大额消费提醒";
            reason = "这笔欲望型消费已超过当前日均可用预算的 2 倍";
            suggestion = "建议冷静 24 小时后再决定，避免冲动消费。";
        } else {
            level = "success";
            levelText = "绿灯 - 可正常消费";
            icon = "✅";
            title = "可以消费";
            reason = "当前预算余额充足，这笔消费不会带来明显超支风险";
            suggestion = attribute == 1
                    ? "属于必要支出，可以正常消费。"
                    : "可以消费，但仍建议保持理性节奏。";
        }

        Map<String, Object> data = new HashMap<>();
        data.put("level", level);
        data.put("levelText", levelText);
        data.put("icon", icon);
        data.put("title", title);
        data.put("reason", reason);
        data.put("suggestion", suggestion);
        data.put("remainingBudget", remainingBudget.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        data.put("afterSpend", afterSpend.setScale(2, RoundingMode.HALF_UP));
        data.put("dailyBudget", dailyBudget.setScale(2, RoundingMode.HALF_UP));
        data.put("attribute", attribute);
        data.put("currentUsageRate", currentUsageRate.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP));
        data.put("afterUsageRate", afterUsageRate.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP));
        data.put("predictedMonthTotal", predictedMonthTotal);
        data.put("predictedOver", predictedOver.setScale(2, RoundingMode.HALF_UP));

        return JsonResponse.success("检查成功", data);
    }

    @Override
    public JsonResponse<Object> getWarningRecords(Long userId) {
        LocalDateTime monthStart = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
        LocalDateTime monthEnd = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()).atTime(23, 59, 59);

        List<WarningRecords> records = this.list(
                new LambdaQueryWrapper<WarningRecords>()
                        .eq(WarningRecords::getUserId, userId)
                        .in(WarningRecords::getWarningType, "RED", "YELLOW", "ANOMALY")
                        .between(WarningRecords::getCreatedAt, monthStart, monthEnd)
                        .orderByDesc(WarningRecords::getCreatedAt)
                        .last("limit 10")
        );

        return JsonResponse.success("获取成功", records);
    }

    @Override
    public JsonResponse<Object> getUnreadCount(Long userId) {
        LocalDateTime monthStart = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
        LocalDateTime monthEnd = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()).atTime(23, 59, 59);

        long count = this.count(
                new LambdaQueryWrapper<WarningRecords>()
                        .eq(WarningRecords::getUserId, userId)
                        .eq(WarningRecords::getIsRead, false)
                        .in(WarningRecords::getWarningType, "RED", "YELLOW", "ANOMALY")
                        .between(WarningRecords::getCreatedAt, monthStart, monthEnd)
        );

        Map<String, Object> data = new HashMap<>();
        data.put("count", count);
        return JsonResponse.success("获取成功", data);
    }

    @Override
    public JsonResponse<Object> markAllAsRead(Long userId) {
        LocalDateTime monthStart = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
        LocalDateTime monthEnd = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()).atTime(23, 59, 59);

        boolean success = this.update(
                new LambdaUpdateWrapper<WarningRecords>()
                        .eq(WarningRecords::getUserId, userId)
                        .eq(WarningRecords::getIsRead, false)
                        .in(WarningRecords::getWarningType, "RED", "YELLOW", "ANOMALY")
                        .between(WarningRecords::getCreatedAt, monthStart, monthEnd)
                        .set(WarningRecords::getIsRead, true)
        );

        return success ? JsonResponse.success("全部标记为已读") : JsonResponse.fail("更新已读状态失败");
    }

    public void createBudgetWarningRecord(Long userId) {
        Users user = usersMapper.selectById(userId);
        if (user == null || user.getMonthlyLimit() == null || user.getMonthlyLimit().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BudgetWarningResult result = buildBudgetWarningResult(user, userId);
        if ("GREEN".equals(result.warningLevel)) {
            return;
        }

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().plusDays(1).atStartOfDay();

        long count = this.count(
                new LambdaQueryWrapper<WarningRecords>()
                        .eq(WarningRecords::getUserId, userId)
                        .eq(WarningRecords::getWarningType, result.warningLevel)
                        .eq(WarningRecords::getWarningMsg, result.message)
                        .between(WarningRecords::getCreatedAt, todayStart, todayEnd)
        );

        if (count > 0) {
            return;
        }

        WarningRecords record = new WarningRecords();
        record.setUserId(userId);
        record.setWarningType(result.warningLevel);
        record.setTriggerAmount(result.currentSpent);
        record.setWarningMsg(result.message);
        record.setIsRead(false);
        record.setCreatedAt(LocalDateTime.now());
        record.setThresholdSnapshot(result.warningThreshold);
        this.save(record);
    }

    private void saveWarningRecord(Long userId, String warningType, BigDecimal triggerAmount, String warningMsg) {
        try {
            WarningRecords record = new WarningRecords();
            record.setUserId(userId);
            record.setWarningType(warningType);
            record.setTriggerAmount(triggerAmount);
            record.setWarningMsg(warningMsg);
            record.setIsRead(false);
            record.setCreatedAt(LocalDateTime.now());
            this.save(record);
        } catch (Exception ignored) {
        }
    }

    /**
     * 创建预算预警记录
     *
     * @param userId 用户ID
     */
    public void createAnomalyWarningRecord(Long userId, Bills bill) {
        if (bill == null || bill.getAnomalyType() == null || bill.getAnomalyScore() == null) {
            return;
        }

        if (bill.getOccurTime() == null
                || !bill.getOccurTime().toLocalDate().withDayOfMonth(1).equals(LocalDate.now().withDayOfMonth(1))) {
            return;
        }

        if (bill.getAnomalyScore().doubleValue() < 0.68) {
            return;
        }

        if (bill.getAnomalyReason() == null || bill.getAnomalyReason().isEmpty()) {
            return;
        }

        String warningMsg = "检测到异常消费：" + bill.getAnomalyReason();

        long count = this.count(
                new LambdaQueryWrapper<WarningRecords>()
                        .eq(WarningRecords::getUserId, userId)
                        .eq(WarningRecords::getWarningType, "ANOMALY")
                        .eq(WarningRecords::getWarningMsg, warningMsg)
                        .eq(WarningRecords::getTriggerAmount, bill.getAmount())
                        .between(
                                WarningRecords::getCreatedAt,
                                bill.getOccurTime().minusMinutes(1),
                                bill.getOccurTime().plusMinutes(1)
                        )
        );

        if (count > 0) {
            return;
        }

        saveWarningRecord(userId, "ANOMALY", bill.getAmount(), warningMsg);
    }
}
