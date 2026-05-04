package lu.smartledger.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 生活费更新DTO
 */
@Data
@Schema(description = "生活费更新")
public class AllowanceUpdateDTO {

    /**
     * 月度生活费
     */
    @NotNull(message = "月度生活费不能为空")
    @DecimalMin(value = "0.01", message = "月度生活费必须大于0")
    @Schema(description = "月度生活费", required = true)
    private BigDecimal monthlyAllowance;

    /**
     * 每日生存保障费用
     */
    @NotNull(message = "每日生存保障费用不能为空")
    @DecimalMin(value = "0.00", message = "每日生存保障费用不能为负数")
    @Schema(description = "每日生存保障费用", required = true)
    private BigDecimal dailySurvivalCost;

    /**
     * 应急资金
     */
    @NotNull(message = "应急资金不能为空")
    @DecimalMin(value = "0.00", message = "应急资金不能为负数")
    @Schema(description = "应急资金", required = true)
    private BigDecimal emergencyFund;
}