package lu.smartledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lu.smartledger.mapper.BillsMapper;
import lu.smartledger.mapper.CategoriesMapper;
import lu.smartledger.mapper.UsersMapper;
import lu.smartledger.model.domain.Bills;
import lu.smartledger.model.domain.Categories;
import lu.smartledger.model.domain.Users;
import lu.smartledger.model.dto.RankItemDTO;
import lu.smartledger.service.IRankService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
public class RankServiceImpl implements IRankService {

    @Resource
    private UsersMapper usersMapper;

    @Resource
    private BillsMapper billsMapper;

    @Resource
    private CategoriesMapper categoriesMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取排行榜
     *
     * @param currentUserId 当前用户ID
     * @param period        时间段（可选："week"、"month"）
     * @return 排行榜列表
     */
    @Override
    public List<RankItemDTO> getLeaderboard(Long currentUserId, String period) {
        String finalPeriod = (period == null || period.trim().isEmpty()) ? "month" : period.trim().toLowerCase();

        LocalDate today = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;

        if ("week".equals(finalPeriod)) {
            startDate = today.with(DayOfWeek.MONDAY);
            endDate = today.with(DayOfWeek.SUNDAY);
        } else {
            startDate = today.withDayOfMonth(1);
            endDate = today.withDayOfMonth(today.lengthOfMonth());
        }

        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.atTime(LocalTime.MAX);

        List<Users> users = usersMapper.selectList(
                new LambdaQueryWrapper<Users>()
                        .eq(Users::getRole, "USER")
                        .eq(Users::getStatus, "ACTIVE")
        );

        List<RankItemDTO> result = new ArrayList<>();
        for (Users user : users) {//跳过无账单用户
            RankMetrics metrics = buildRankMetrics(user, startTime, endTime);
            Integer score = calculateHealthScore(metrics);
            if (score == null) {
                continue;
            }

            GradeInfo gradeInfo = buildGradeInfo(score);

            RankItemDTO dto = new RankItemDTO();
            dto.setId(user.getId());
            dto.setName(user.getUsername() == null || user.getUsername().trim().isEmpty() ? "用户" + user.getId() : user.getUsername());
            dto.setAvatar(buildAvatar(user));
            dto.setGrade(gradeInfo.getGrade());
            dto.setGradeType(gradeInfo.getGradeType());
            dto.setScore(score);
            dto.setDesc(gradeInfo.getDesc());
            dto.setIsMe(user.getId().equals(currentUserId));
            dto.setAvatarUrl(user.getAvatarUrl());

            result.add(dto);
        }

        result.sort(Comparator.comparing(RankItemDTO::getScore).reversed()
                .thenComparing(RankItemDTO::getId));

        if (result.size() > 5) {
            return result.subList(0, 5);
        }
        return result;
    }

    /**
     * 获取当前用户的排行榜信息
     *
     * @param currentUserId 当前用户ID
     * @param period        时间段（可选："week"、"month"）
     * @return 当前用户的排行榜信息
     */
    @Override
    public RankItemDTO getMyRank(Long currentUserId, String period) {
        if (currentUserId == null) {
            return null;
        }

        String finalPeriod = (period == null || period.trim().isEmpty()) ? "month" : period.trim().toLowerCase();

        LocalDate today = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;

        if ("week".equals(finalPeriod)) {
            startDate = today.with(DayOfWeek.MONDAY);
            endDate = today.with(DayOfWeek.SUNDAY);
        } else {
            startDate = today.withDayOfMonth(1);
            endDate = today.withDayOfMonth(today.lengthOfMonth());
        }

        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.atTime(LocalTime.MAX);

        Users user = usersMapper.selectById(currentUserId);
        if (user == null || !"USER".equals(user.getRole()) || !"ACTIVE".equals(user.getStatus())) {
            return null;
        }

        RankMetrics metrics = buildRankMetrics(user, startTime, endTime);
        Integer score = calculateHealthScore(metrics);
        if (score == null) {
            return null;
        }

        GradeInfo gradeInfo = buildGradeInfo(score);

        RankItemDTO dto = new RankItemDTO();
        dto.setId(user.getId());
        dto.setName(user.getUsername() == null || user.getUsername().trim().isEmpty() ? "用户" + user.getId() : user.getUsername());
        dto.setAvatar(buildAvatar(user));
        dto.setGrade(gradeInfo.getGrade());
        dto.setGradeType(gradeInfo.getGradeType());
        dto.setScore(score);
        dto.setDesc(gradeInfo.getDesc());
        dto.setIsMe(true);
        dto.setAvatarUrl(user.getAvatarUrl());

        return dto;
    }


    /**
     * 获取用户成就
     *
     * @param currentUserId 当前用户ID
     * @return 用户成就列表
     */
    @Override
    public List<Map<String, Object>> getAchievements(Long currentUserId) {
        Users user = usersMapper.selectById(currentUserId);
        if (user == null) {
            return new ArrayList<>();
        }

        settleLastMonthAchievements(user);

        List<Map<String, Object>> unlockedList = parseAchievementData(user.getAchievementData());

        List<Map<String, Object>> result = new ArrayList<>();
        result.add(buildAchievementView("SAVE_MASTER", "💰", "存钱小能手", "储蓄率达到 30%", unlockedList));
        result.add(buildAchievementView("SUPER_BIG_EATER", "🍜", "超级大饭桶", "食堂占比 ≥70%", unlockedList));
        result.add(buildAchievementView("MONTH_S", "🎖", "月度 S 级", "保持一个月健康分 S 级", unlockedList));
        result.add(buildAchievementView("BUDGET_MASTER", "📊", "预算控制者", "预算使用率 < 80%", unlockedList));

        return result;
    }

    /**
     * 构建排行榜指标
     */
    private Map<String, Object> buildAchievement(String icon, String name, String desc, boolean unlocked) {
        Map<String, Object> item = new HashMap<>();
        item.put("icon", icon);
        item.put("name", name);
        item.put("desc", desc);
        item.put("unlocked", unlocked);
        return item;
    }

    /**
     * 获取分类名称
     */
    private String getCategoryName(Long categoryId, Map<Long, String> cache) {
        if (categoryId == null) {
            return "";
        }

        if (cache.containsKey(categoryId)) {
            return cache.get(categoryId);
        }

        Categories category = categoriesMapper.selectById(categoryId);
        String name = category != null && category.getName() != null ? category.getName() : "";
        cache.put(categoryId, name);
        return name;
    }

    /**
     * 判断是否是食堂类
     * */
    private boolean isCanteenCategory(String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            return false;
        }
        return categoryName.contains("食堂")
                || categoryName.contains("餐饮")
                || categoryName.contains("就餐");
    }

    /**
     * 构建排行榜指标
     */
    private RankMetrics buildRankMetrics(Users user, LocalDateTime startTime, LocalDateTime endTime) {
        List<Bills> bills = billsMapper.selectList(
                new LambdaQueryWrapper<Bills>()
                        .eq(Bills::getUserId, user.getId())
                        .eq(Bills::getBillType, "EXPENSE")
                        .ge(Bills::getOccurTime, startTime)
                        .le(Bills::getOccurTime, endTime)
        );

        BigDecimal totalExpense = BigDecimal.ZERO;
        BigDecimal necessaryExpense = BigDecimal.ZERO;
        BigDecimal desireExpense = BigDecimal.ZERO;

        int abnormalCount = 0;
        BigDecimal anomalyScoreTotal = BigDecimal.ZERO;

        for (Bills bill : bills) {
            BigDecimal amount = bill.getAmount() == null ? BigDecimal.ZERO : bill.getAmount().abs();
            totalExpense = totalExpense.add(amount);

            Byte attribute = bill.getConsumptionAttribute();
            if (attribute != null) {
                if (attribute == 1) {
                    necessaryExpense = necessaryExpense.add(amount);
                } else if (attribute == 3) {
                    desireExpense = desireExpense.add(amount);
                }
            }

            if (Boolean.TRUE.equals(bill.getIsAbnormal())) {
                abnormalCount++;
                anomalyScoreTotal = anomalyScoreTotal.add(
                        bill.getAnomalyScore() == null ? BigDecimal.ZERO : bill.getAnomalyScore()
                );
            }
        }

        int necessaryRatio = 0;
        int desireRatio = 0;
        if (totalExpense.compareTo(BigDecimal.ZERO) > 0) {
            necessaryRatio = necessaryExpense.multiply(BigDecimal.valueOf(100))
                    .divide(totalExpense, 0, BigDecimal.ROUND_HALF_UP)
                    .intValue();

            desireRatio = desireExpense.multiply(BigDecimal.valueOf(100))
                    .divide(totalExpense, 0, BigDecimal.ROUND_HALF_UP)
                    .intValue();
        }

        int budgetUsage = 0;
        if (user.getMonthlyLimit() != null && user.getMonthlyLimit().compareTo(BigDecimal.ZERO) > 0) {
            budgetUsage = totalExpense.multiply(BigDecimal.valueOf(100))
                    .divide(user.getMonthlyLimit(), 0, BigDecimal.ROUND_HALF_UP)
                    .intValue();
        }

        double avgAnomalyScore = 0;
        if (abnormalCount > 0) {
            avgAnomalyScore = anomalyScoreTotal
                    .divide(BigDecimal.valueOf(abnormalCount), 4, BigDecimal.ROUND_HALF_UP)
                    .doubleValue();
        }

        RankMetrics metrics = new RankMetrics();
        metrics.setExpenseCount(bills.size());
        metrics.setBudgetUsage(budgetUsage);
        metrics.setNecessaryRatio(necessaryRatio);
        metrics.setDesireRatio(desireRatio);
        metrics.setAbnormalCount(abnormalCount);
        metrics.setAvgAnomalyScore(avgAnomalyScore);
        return metrics;
    }

    /**
     * 计算健康分
     */
    private Integer calculateHealthScore(RankMetrics metrics) {
        if (metrics.getExpenseCount() <= 0) {
            return null;
        }

        double score = 100;

        score -= Math.max(0, metrics.getBudgetUsage() - 60) * 0.35;
        if (metrics.getBudgetUsage() >= 100) {
            score -= 10;
        }
        if (metrics.getBudgetUsage() >= 120) {
            score -= 8;
        }

        score -= metrics.getDesireRatio() * 0.20;
        score += Math.min(metrics.getNecessaryRatio(), 70) * 0.06;

        score -= metrics.getAbnormalCount() * 6.0;
        score -= metrics.getAvgAnomalyScore() * 20.0;

        score = Math.max(0, Math.min(100, score));
        return (int) Math.round(score);
    }
    /**
     * 构建等级信息
     */
    private GradeInfo buildGradeInfo(Integer score) {
        GradeInfo info = new GradeInfo();

        if (score >= 90) {
            info.setGrade("S");
            info.setGradeType("success");
            info.setDesc("消费达人");
        } else if (score >= 75) {
            info.setGrade("A");
            info.setGradeType("success");
            info.setDesc("理性消费");
        } else if (score >= 60) {
            info.setGrade("B");
            info.setGradeType("warning");
            info.setDesc("可提升中");
        } else {
            info.setGrade("C");
            info.setGradeType("danger");
            info.setDesc("需要注意");
        }

        return info;
    }

    private String buildAvatar(Users user) {
        String name = user.getUsername();
        if (name == null || name.trim().isEmpty()) {
            return "用";
        }
        return name.substring(0, 1);
    }

    private static class RankMetrics {
        private int expenseCount;
        private int budgetUsage;
        private int necessaryRatio;
        private int desireRatio;
        private int abnormalCount;
        private double avgAnomalyScore;

        public int getExpenseCount() {
            return expenseCount;
        }

        public void setExpenseCount(int expenseCount) {
            this.expenseCount = expenseCount;
        }

        public int getBudgetUsage() {
            return budgetUsage;
        }

        public void setBudgetUsage(int budgetUsage) {
            this.budgetUsage = budgetUsage;
        }

        public int getNecessaryRatio() {
            return necessaryRatio;
        }

        public void setNecessaryRatio(int necessaryRatio) {
            this.necessaryRatio = necessaryRatio;
        }

        public int getDesireRatio() {
            return desireRatio;
        }

        public void setDesireRatio(int desireRatio) {
            this.desireRatio = desireRatio;
        }

        public int getAbnormalCount() {
            return abnormalCount;
        }

        public void setAbnormalCount(int abnormalCount) {
            this.abnormalCount = abnormalCount;
        }

        public double getAvgAnomalyScore() {
            return avgAnomalyScore;
        }

        public void setAvgAnomalyScore(double avgAnomalyScore) {
            this.avgAnomalyScore = avgAnomalyScore;
        }
    }

    private static class GradeInfo {
        private String grade;
        private String gradeType;
        private String desc;

        public String getGrade() {
            return grade;
        }

        public void setGrade(String grade) {
            this.grade = grade;
        }

        public String getGradeType() {
            return gradeType;
        }

        public void setGradeType(String gradeType) {
            this.gradeType = gradeType;
        }

        public String getDesc() {
            return desc;
        }

        public void setDesc(String desc) {
            this.desc = desc;
        }
    }

    /**
     * 结算上月成就
     */
    private void settleLastMonthAchievements(Users user) {
        String lastMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1).toString().substring(0, 7);

        if (lastMonth.equals(user.getAchievementLastSettleMonth())) {
            return;
        }

        List<Map<String, Object>> unlockedList = parseAchievementData(user.getAchievementData());

        if (!containsAchievement(unlockedList, "SAVE_MASTER") && checkSaveMaster(user.getId(), lastMonth)) {
            unlockedList.add(buildAchievementRecord("SAVE_MASTER", "存钱小能手", "储蓄率达到 30%", lastMonth));
        }

        if (!containsAchievement(unlockedList, "SUPER_BIG_EATER") && checkSuperBigEater(user.getId(), lastMonth)) {
            unlockedList.add(buildAchievementRecord("SUPER_BIG_EATER", "超级大饭桶", "食堂占比 ≥70%", lastMonth));
        }

        if (!containsAchievement(unlockedList, "MONTH_S") && checkMonthS(user.getId(), lastMonth)) {
            unlockedList.add(buildAchievementRecord("MONTH_S", "月度 S 级", "月度健康分达到 S 级", lastMonth));
        }

        if (!containsAchievement(unlockedList, "BUDGET_MASTER") && checkBudgetMaster(user.getId(), lastMonth)) {
            unlockedList.add(buildAchievementRecord("BUDGET_MASTER", "预算控制者", "预算使用率 < 80%", lastMonth));
        }

        try {
            user.setAchievementData(objectMapper.writeValueAsString(unlockedList));
            user.setAchievementLastSettleMonth(lastMonth);
            usersMapper.updateById(user);
        } catch (Exception e) {
            throw new RuntimeException("成就结算失败：" + e.getMessage());
        }
    }

    /**
     * 解析成就数据
     * */
    private List<Map<String, Object>> parseAchievementData(String achievementData) {
        if (achievementData == null || achievementData.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(
                    achievementData,
                    new TypeReference<List<Map<String, Object>>>() {}
            );
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private boolean containsAchievement(List<Map<String, Object>> unlockedList, String code) {
        return unlockedList.stream().anyMatch(item -> code.equals(item.get("code")));
    }

    private Map<String, Object> buildAchievementRecord(String code, String name, String desc, String achieveMonth) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("name", name);
        item.put("desc", desc);
        item.put("achieveMonth", achieveMonth);
        return item;
    }

    private Map<String, Object> buildAchievementView(String code, String icon, String name, String desc, List<Map<String, Object>> unlockedList) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("icon", icon);
        item.put("name", name);
        item.put("desc", desc);
        item.put("unlocked", containsAchievement(unlockedList, code));
        return item;
    }
    private boolean checkSaveMaster(Long userId, String monthStr) {
        LocalDateTime startTime = LocalDate.parse(monthStr + "-01").atStartOfDay();
        LocalDate lastDay = LocalDate.parse(monthStr + "-01").withDayOfMonth(LocalDate.parse(monthStr + "-01").lengthOfMonth());
        LocalDateTime endTime = lastDay.atTime(LocalTime.MAX);

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;

        List<Bills> incomeBills = billsMapper.selectList(
                new LambdaQueryWrapper<Bills>()
                        .eq(Bills::getUserId, userId)
                        .eq(Bills::getBillType, "INCOME")
                        .ge(Bills::getOccurTime, startTime)
                        .le(Bills::getOccurTime, endTime)
        );

        List<Bills> expenseBills = billsMapper.selectList(
                new LambdaQueryWrapper<Bills>()
                        .eq(Bills::getUserId, userId)
                        .eq(Bills::getBillType, "EXPENSE")
                        .ge(Bills::getOccurTime, startTime)
                        .le(Bills::getOccurTime, endTime)
        );

        for (Bills bill : incomeBills) {
            totalIncome = totalIncome.add(bill.getAmount() == null ? BigDecimal.ZERO : bill.getAmount().abs());
        }
        for (Bills bill : expenseBills) {
            totalExpense = totalExpense.add(bill.getAmount() == null ? BigDecimal.ZERO : bill.getAmount().abs());
        }

        if (totalIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        BigDecimal balance = totalIncome.subtract(totalExpense);
        BigDecimal savingRate = balance.divide(totalIncome, 4, RoundingMode.HALF_UP);
        return savingRate.compareTo(new BigDecimal("0.30")) >= 0;
    }

    private boolean checkSuperBigEater(Long userId, String monthStr) {
        LocalDateTime startTime = LocalDate.parse(monthStr + "-01").atStartOfDay();
        LocalDate lastDay = LocalDate.parse(monthStr + "-01").withDayOfMonth(LocalDate.parse(monthStr + "-01").lengthOfMonth());
        LocalDateTime endTime = lastDay.atTime(LocalTime.MAX);

        List<Bills> expenseBills = billsMapper.selectList(
                new LambdaQueryWrapper<Bills>()
                        .eq(Bills::getUserId, userId)
                        .eq(Bills::getBillType, "EXPENSE")
                        .ge(Bills::getOccurTime, startTime)
                        .le(Bills::getOccurTime, endTime)
        );

        BigDecimal totalExpense = BigDecimal.ZERO;
        BigDecimal canteenExpense = BigDecimal.ZERO;
        Map<Long, String> categoryNameCache = new HashMap<>();

        for (Bills bill : expenseBills) {
            BigDecimal amount = bill.getAmount() == null ? BigDecimal.ZERO : bill.getAmount().abs();
            totalExpense = totalExpense.add(amount);

            String categoryName = getCategoryName(bill.getCategoryId(), categoryNameCache);
            if (isCanteenCategory(categoryName)) {
                canteenExpense = canteenExpense.add(amount);
            }
        }

        if (totalExpense.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        BigDecimal canteenRate = canteenExpense.divide(totalExpense, 4, RoundingMode.HALF_UP);
        return canteenRate.compareTo(new BigDecimal("0.70")) >= 0;
    }

    private boolean checkMonthS(Long userId, String monthStr) {
    Users user = usersMapper.selectById(userId);
    if (user == null) {
        return false;
    }

    LocalDateTime startTime = LocalDate.parse(monthStr + "-01").atStartOfDay();
    LocalDate lastDay = LocalDate.parse(monthStr + "-01")
            .withDayOfMonth(LocalDate.parse(monthStr + "-01").lengthOfMonth());
    LocalDateTime endTime = lastDay.atTime(LocalTime.MAX);

    List<Bills> expenseBills = billsMapper.selectList(
            new LambdaQueryWrapper<Bills>()
                    .eq(Bills::getUserId, userId)
                    .eq(Bills::getBillType, "EXPENSE")
                    .ge(Bills::getOccurTime, startTime)
                    .le(Bills::getOccurTime, endTime)
    );

    if (expenseBills == null || expenseBills.isEmpty()) {
        return false;
    }

    RankMetrics metrics = buildRankMetrics(user, startTime, endTime);
    int healthScore = calculateHealthScore(metrics);
    return healthScore >= 90;
}

    private boolean checkBudgetMaster(Long userId, String monthStr) {
        Users user = usersMapper.selectById(userId);
        if (user == null || user.getMonthlyLimit() == null || user.getMonthlyLimit().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        LocalDateTime startTime = LocalDate.parse(monthStr + "-01").atStartOfDay();
        LocalDate lastDay = LocalDate.parse(monthStr + "-01").withDayOfMonth(LocalDate.parse(monthStr + "-01").lengthOfMonth());
        LocalDateTime endTime = lastDay.atTime(LocalTime.MAX);

        List<Bills> expenseBills = billsMapper.selectList(
                new LambdaQueryWrapper<Bills>()
                        .eq(Bills::getUserId, userId)
                        .eq(Bills::getBillType, "EXPENSE")
                        .ge(Bills::getOccurTime, startTime)
                        .le(Bills::getOccurTime, endTime)
        );

        if (expenseBills == null || expenseBills.isEmpty()) {
    return false;
}

BigDecimal totalExpense = BigDecimal.ZERO;
for (Bills bill : expenseBills) {
    totalExpense = totalExpense.add(bill.getAmount() == null ? BigDecimal.ZERO : bill.getAmount().abs());
}

BigDecimal usageRate = totalExpense.divide(user.getMonthlyLimit(), 4, RoundingMode.HALF_UP);
return usageRate.compareTo(new BigDecimal("0.80")) < 0;
    }

}
