package lu.smartledger.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 预算更新DTO
 */
@Data
@Schema(description = "预算更新")
public class BudgetUpdateDTO {

    /**
     * 月度预算
     */
    @NotNull(message = "月度预算不能为空")
    @DecimalMin(value = "0.01", message = "月度预算必须大于0")
    @Schema(description = "月度预算", required = true)
    private BigDecimal monthlyBudget;

    /**
     * 预警阈值（百分比）
     */
    @NotNull(message = "预警阈值不能为空")
    @Min(value = 50, message = "预警阈值不能小于50%")
    @Max(value = 100, message = "预警阈值不能大于100%")
    @Schema(description = "预警阈值（百分比）", required = true)
    private Integer warningThreshold;
}