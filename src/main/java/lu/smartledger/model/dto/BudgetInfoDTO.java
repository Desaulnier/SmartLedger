package lu.smartledger.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 预算信息DTO
 */
@Data
@Schema(description = "预算信息")
public class BudgetInfoDTO {

    /**
     * 月度生活费
     */
    @Schema(description = "月度生活费")
    private BigDecimal monthlyAllowance;

    /**
     * 月度预算
     */
    @Schema(description = "月度预算")
    private BigDecimal monthlyBudget;

    /**
     * 预警阈值
     */
    @Schema(description = "预警阈值")
    private Integer warningThreshold;

    /**
     * 每日生存保障费用
     */
    @Schema(description = "每日生存保障费用")
    private BigDecimal dailySurvivalCost;

    /**
     * 应急资金
     */
    @Schema(description = "应急资金")
    private BigDecimal emergencyFund;

    /**
     * 本月已消费金额
     */
    @Schema(description = "本月已消费金额")
    private BigDecimal currentSpent;

    /**
     * 预算剩余金额
     */
    @Schema(description = "预算剩余金额")
    private BigDecimal budgetRemaining;

    /**
     * 剩余天数
     */
    @Schema(description = "剩余天数")
    private Integer remainingDays;

    /**
     * 每日可用预算
     */
    @Schema(description = "每日可用预算")
    private BigDecimal dailyBudget;
}