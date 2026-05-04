package lu.smartledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lu.smartledger.common.utls.JsonResponse;
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

    @Override
    public JsonResponse<Object> getBudgetWarning(Long userId) {
        Users user = usersMapper.selectById(userId);
        if (user == null) {
            return JsonResponse.fail("用户不存在");
        }

        if (user.getMonthlyLimit() == null || user.getMonthlyLimit().compareTo(BigDecimal.ZERO) <= 0) {
            return JsonResponse.fail("请先设置月度预算");
        }

        BigDecimal monthlyLimit = user.getMonthlyLimit();
        BigDecimal currentSpent = user.getCurrentSpent() == null ? BigDecimal.ZERO : user.getCurrentSpent();

        BigDecimal thresholdPercent = user.getWarningThreshold() == null
                ? BigDecimal.valueOf(80)
                : user.getWarningThreshold();

        if (thresholdPercent.compareTo(BigDecimal.ONE) <= 0) {
            thresholdPercent = thresholdPercent.multiply(BigDecimal.valueOf(100));
        }

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

        BigDecimal usageRate = currentSpent.divide(monthlyLimit, 4, RoundingMode.HALF_UP);
        BigDecimal thresholdRate = thresholdPercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

        String warningLevel;
        String message;

        if (predictTotal.compareTo(monthlyLimit) > 0 || usageRate.compareTo(BigDecimal.ONE) >= 0) {
            warningLevel = "RED";
            message = "按当前消费趋势，本月存在明显超支风险";
        } else if (usageRate.compareTo(thresholdRate) >= 0) {
            warningLevel = "YELLOW";
            message = "当前消费已达到预警阈值，请注意控制支出";
        } else {
            warningLevel = "GREEN";
            message = "当前消费状态正常，预算风险较低";
        }

        Map<String, Object> data = new HashMap<>();
        data.put("warningLevel", warningLevel);
        data.put("message", message);
        data.put("monthlyLimit", monthlyLimit);
        data.put("currentSpent", currentSpent);
        data.put("warningThreshold", thresholdPercent);
        data.put("usageRate", usageRate.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP));
        data.put("predictTotal", predictTotal);
        data.put("predictOver", predictOver);
        data.put("remainingDays", lastDay - dayOfMonth + 1);
        
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
        BigDecimal currentSpent = user.getCurrentSpent() == null ? BigDecimal.ZERO : user.getCurrentSpent();

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

        BigDecimal thresholdPercent = user.getWarningThreshold() == null
                ? BigDecimal.valueOf(80)
                : user.getWarningThreshold();

        if (thresholdPercent.compareTo(BigDecimal.ONE) <= 0) {
            thresholdPercent = thresholdPercent.multiply(BigDecimal.valueOf(100));
        }

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
            icon = "🚫";
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
            icon = "💸";
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

    /**
     * 获取预算预警记录
     * */
    @Override
    public JsonResponse<Object> getWarningRecords(Long userId) {
        // 获取本月的开始和结束时间
        LocalDateTime monthStart = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
        LocalDateTime monthEnd = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()).atTime(23, 59, 59);

        List<WarningRecords> records = this.list(
                new LambdaQueryWrapper<WarningRecords>()
                        .eq(WarningRecords::getUserId, userId)
                        .in(WarningRecords::getWarningType, "RED", "YELLOW", "ANOMALY")
                        // 核心：只查询本月的记录
                        .between(WarningRecords::getCreatedAt, monthStart, monthEnd)
                        .orderByDesc(WarningRecords::getCreatedAt)
                        .last("limit 10")
        );

        return JsonResponse.success("获取成功", records);
    }

    /**
     *  获取未读的预算预警记录数量
     * @param userId
     * @return
     */
    @Override
    public JsonResponse<Object> getUnreadCount(Long userId) {
        long count = this.count(
                new LambdaQueryWrapper<WarningRecords>()
                        .eq(WarningRecords::getUserId, userId)
                        .eq(WarningRecords::getIsRead, false)
                        .in(WarningRecords::getWarningType, "RED", "YELLOW", "ANOMALY")
        );

        Map<String, Object> data = new HashMap<>();
        data.put("count", count);
        return JsonResponse.success("获取成功", data);
    }

    /**
     *  将所有预算预警记录标记为已读
     * @param userId
     * @return
     */
    @Override
    public JsonResponse<Object> markAllAsRead(Long userId) {
        boolean success = this.update(
                new LambdaUpdateWrapper<WarningRecords>()
                        .eq(WarningRecords::getUserId, userId)
                        .eq(WarningRecords::getIsRead, false)
                        .in(WarningRecords::getWarningType, "RED", "YELLOW", "ANOMALY")
                        .set(WarningRecords::getIsRead, true)
        );

        return success ? JsonResponse.success("全部标记为已读") : JsonResponse.fail("更新已读状态失败");
    }

    /**
     * 创建预算预警记录
     * @param userId
     */
    public void createBudgetWarningRecord(Long userId) {
        Users user = usersMapper.selectById(userId);
        if (user == null || user.getMonthlyLimit() == null || user.getMonthlyLimit().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal monthlyLimit = user.getMonthlyLimit();
        BigDecimal currentSpent = user.getCurrentSpent() == null ? BigDecimal.ZERO : user.getCurrentSpent();
        BigDecimal thresholdPercent = user.getWarningThreshold() == null ? BigDecimal.valueOf(80) : user.getWarningThreshold();

        if (thresholdPercent.compareTo(BigDecimal.ONE) <= 0) {
            thresholdPercent = thresholdPercent.multiply(BigDecimal.valueOf(100));
        }

        BigDecimal usageRate = currentSpent.divide(monthlyLimit, 4, RoundingMode.HALF_UP);
        BigDecimal thresholdRate = thresholdPercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

        String warningType = null;
        String warningMsg = null;

        // 超支 → 红色预警
        if (currentSpent.compareTo(monthlyLimit) > 0 || usageRate.compareTo(BigDecimal.ONE) >= 0) {
            warningType = "RED";
            warningMsg = "预算已超支，请立即控制消费！";
        }
        // 达到阈值 → 黄色预警
        else if (usageRate.compareTo(thresholdRate) >= 0) {
            warningType = "YELLOW";
            warningMsg = "消费已达预警阈值，请注意节约！";
        }

        if (warningType == null) {
            return;
        }

        // 防刷屏：检查今天是否已经有同类型预警
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().plusDays(1).atStartOfDay();

        long count = this.count(
                new LambdaQueryWrapper<WarningRecords>()
                        .eq(WarningRecords::getUserId, userId)
                        .eq(WarningRecords::getWarningType, warningType)
                        .eq(WarningRecords::getWarningMsg, warningMsg)
                        .between(WarningRecords::getCreatedAt, todayStart, todayEnd)
        );

        // 今天已经有同类型预警，就不重复生成
        if (count > 0) {
            return;
        }

        // 保存新的预警记录
        WarningRecords record = new WarningRecords();
        record.setUserId(userId);
        record.setWarningType(warningType);
        record.setTriggerAmount(currentSpent);
        record.setWarningMsg(warningMsg);
        record.setIsRead(false);
        record.setCreatedAt(LocalDateTime.now());
        record.setThresholdSnapshot(thresholdPercent);
        this.save(record);
    }
    /**
     * 保存预算预警记录
     * @param userId
     * @param warningType
     * @param triggerAmount
     * @param warningMsg
     */
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
     * 创建异常消费预警记录
     * @param userId
     * @param bill
     */
    public void createAnomalyWarningRecord(Long userId, Bills bill) {
        if (bill == null || bill.getAnomalyType() == null || bill.getAnomalyScore() == null) {
            return;
        }

        if (bill.getAnomalyScore().doubleValue() < 0.68) {
            return;
        }

        if (bill.getAnomalyReason() == null || bill.getAnomalyReason().isEmpty()) {
            return;
        }

        WarningRecords lastRecord = this.getOne(
                new LambdaQueryWrapper<WarningRecords>()
                        .eq(WarningRecords::getUserId, userId)
                        .eq(WarningRecords::getWarningType, "ANOMALY")
                        .orderByDesc(WarningRecords::getCreatedAt)
                        .last("limit 1")
        );

        if (lastRecord != null
                && lastRecord.getCreatedAt() != null
                && lastRecord.getCreatedAt().toLocalDate().equals(LocalDate.now())) {
            return;
        }

        String warningMsg = "检测到异常消费：" + bill.getAnomalyReason();
        saveWarningRecord(userId, "ANOMALY", bill.getAmount(), warningMsg);
    }
}