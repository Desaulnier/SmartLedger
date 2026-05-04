package lu.smartledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lu.smartledger.mapper.BillsMapper;
import lu.smartledger.mapper.UsersMapper;
import lu.smartledger.model.domain.Bills;
import lu.smartledger.model.domain.Users;
import lu.smartledger.model.dto.AllowanceUpdateDTO;
import lu.smartledger.model.dto.BudgetInfoDTO;
import lu.smartledger.model.dto.BudgetUpdateDTO;
import lu.smartledger.service.IBudgetService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BudgetServiceImpl implements IBudgetService {

    private final UsersMapper usersMapper;
    private final BillsMapper billsMapper;
    private final WarningRecordsServiceImpl warningRecordsService;
    /**
     * 获取用户预算信息
     *
     * @param userId 用户ID
     * @return 用户预算信息
     */
    @Override
    public List<Map<String, Object>> getWeeklyBreakdown(Long userId) {
        Users user = usersMapper.selectById(userId);
        BigDecimal monthlyBudget = user != null && user.getMonthlyLimit() != null
                ? user.getMonthlyLimit()
                : BigDecimal.ZERO;

        BigDecimal[] weekBudgets = splitWeeklyBudgets(monthlyBudget);

        LocalDate now = LocalDate.now();
        LocalDateTime monthStart = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = now.withDayOfMonth(now.lengthOfMonth()).atTime(23, 59, 59);

        List<Bills> bills = billsMapper.selectList(
                new LambdaQueryWrapper<Bills>()
                        .eq(Bills::getUserId, userId)
                        .eq(Bills::getBillType, "EXPENSE")
                        .between(Bills::getOccurTime, monthStart, monthEnd)
        );

        BigDecimal[] weekSpent = new BigDecimal[] {
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        };

        for (Bills bill : bills) {
            if (bill.getOccurTime() == null || bill.getAmount() == null) {
                continue;
            }

            int day = bill.getOccurTime().getDayOfMonth();
            int index;
            if (day <= 7) {
                index = 0;
            } else if (day <= 14) {
                index = 1;
            } else if (day <= 21) {
                index = 2;
            } else {
                index = 3;
            }

            weekSpent[index] = weekSpent[index].add(bill.getAmount());
        }

        int currentWeekIndex;
        int today = now.getDayOfMonth();
        if (today <= 7) {
            currentWeekIndex = 0;
        } else if (today <= 14) {
            currentWeekIndex = 1;
        } else if (today <= 21) {
            currentWeekIndex = 2;
        } else {
            currentWeekIndex = 3;
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Map<String, Object> item = new HashMap<>();
            BigDecimal remaining = weekBudgets[i].subtract(weekSpent[i]);
            if (remaining.compareTo(BigDecimal.ZERO) < 0) {
                remaining = BigDecimal.ZERO;
            }

            String status = "upcoming";
            if (i < currentWeekIndex) {
                status = "completed";
            } else if (i == currentWeekIndex) {
                status = "current";
            }

            item.put("week", "第 " + (i + 1) + " 周");
            item.put("budget", weekBudgets[i].setScale(2, RoundingMode.HALF_UP));
            item.put("spent", weekSpent[i].setScale(2, RoundingMode.HALF_UP));
            item.put("remaining", remaining.setScale(2, RoundingMode.HALF_UP));
            item.put("status", status);
            result.add(item);
        }

        return result;
    }

    private BigDecimal[] splitWeeklyBudgets(BigDecimal totalBudget) {
        if (totalBudget == null || totalBudget.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal[] {
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
            };
        }

        BigDecimal base = totalBudget.divide(BigDecimal.valueOf(4), 2, RoundingMode.DOWN);
        BigDecimal[] result = new BigDecimal[] {
                base,
                base,
                base,
                totalBudget.subtract(base.multiply(BigDecimal.valueOf(3)))
        };

        return result;
    }

    private BigDecimal calcCurrentMonthSpent(Long userId) {
        if (userId == null) {
            return BigDecimal.ZERO;
        }

        LocalDate now = LocalDate.now();
        LocalDateTime monthStart = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = now.withDayOfMonth(now.lengthOfMonth()).atTime(23, 59, 59);

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


    @Override
    public BudgetInfoDTO getBudgetInfo(Long userId) {
        Users user = usersMapper.selectById(userId);
        BudgetInfoDTO dto = new BudgetInfoDTO();

        if (user == null) {
            dto.setMonthlyAllowance(BigDecimal.ZERO);
            dto.setMonthlyBudget(BigDecimal.ZERO);
            dto.setWarningThreshold(80);
            dto.setEmergencyFund(BigDecimal.ZERO);
            dto.setDailySurvivalCost(BigDecimal.ZERO);
            dto.setCurrentSpent(BigDecimal.ZERO);
            dto.setBudgetRemaining(BigDecimal.ZERO);
            dto.setRemainingDays(0);
            dto.setDailyBudget(BigDecimal.ZERO);
            return dto;
        }

        BigDecimal monthlyAllowance = user.getMonthlyAllowance() != null ? user.getMonthlyAllowance() : BigDecimal.ZERO;
        BigDecimal monthlyBudget = user.getMonthlyLimit() != null ? user.getMonthlyLimit() : BigDecimal.ZERO;
        BigDecimal dailySurvivalCost = user.getDailySurvivalCost() != null ? user.getDailySurvivalCost() : BigDecimal.ZERO;
        BigDecimal emergencyFund = user.getEmergencyFund() != null ? user.getEmergencyFund() : BigDecimal.ZERO;
        BigDecimal currentSpent = calcCurrentMonthSpent(userId);

        Integer warningThreshold = 80;
        if (user.getWarningThreshold() != null) {
            BigDecimal raw = user.getWarningThreshold();
            if (raw.compareTo(BigDecimal.ONE) <= 0) {
                warningThreshold = raw.multiply(BigDecimal.valueOf(100)).intValue();
            } else {
                warningThreshold = raw.intValue();
            }
        }

        dto.setMonthlyAllowance(monthlyAllowance);
        dto.setMonthlyBudget(monthlyBudget);
        dto.setWarningThreshold(warningThreshold);
        dto.setEmergencyFund(emergencyFund);
        dto.setDailySurvivalCost(dailySurvivalCost);
        dto.setCurrentSpent(currentSpent);

        BigDecimal budgetRemaining = monthlyBudget.subtract(currentSpent);
        if (budgetRemaining.compareTo(BigDecimal.ZERO) < 0) {
            budgetRemaining = BigDecimal.ZERO;
        }
        dto.setBudgetRemaining(budgetRemaining);

        LocalDate today = LocalDate.now();
        LocalDate lastDayOfMonth = today.withDayOfMonth(today.lengthOfMonth());
        int remainingDays = (int) ChronoUnit.DAYS.between(today, lastDayOfMonth) + 1;
        dto.setRemainingDays(remainingDays);

        if (remainingDays > 0 && budgetRemaining.compareTo(BigDecimal.ZERO) > 0) {
            dto.setDailyBudget(budgetRemaining.divide(BigDecimal.valueOf(remainingDays), 2, RoundingMode.HALF_UP));
        } else {
            dto.setDailyBudget(BigDecimal.ZERO);
        }

        return dto;
    }

    @Override
    @Transactional
    public boolean updateAllowance(Long userId, AllowanceUpdateDTO dto) {
        Users user = usersMapper.selectById(userId);
        if (user == null) {
            return false;
        }

        user.setMonthlyAllowance(dto.getMonthlyAllowance());
        user.setDailySurvivalCost(dto.getDailySurvivalCost());
        user.setEmergencyFund(dto.getEmergencyFund());
        user.setUpdatedAt(LocalDateTime.now());

        return usersMapper.updateById(user) > 0;
    }

@Override
@Transactional
public boolean updateBudget(Long userId, BudgetUpdateDTO dto) {
    Users user = usersMapper.selectById(userId);
    if (user == null) {
        return false;
    }

    user.setMonthlyLimit(dto.getMonthlyBudget());
    user.setWeeklyBudget(dto.getMonthlyBudget().divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP));
    user.setWarningThreshold(BigDecimal.valueOf(dto.getWarningThreshold()));
    user.setUpdatedAt(LocalDateTime.now());

    boolean success = usersMapper.updateById(user) > 0;

    if (success) {
        warningRecordsService.createBudgetWarningRecord(userId);
    }

    return success;
}
}
