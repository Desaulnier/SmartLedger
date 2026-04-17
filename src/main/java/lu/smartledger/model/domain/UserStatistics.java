package lu.smartledger.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户消费统计表
 * </p>
 *
 * @author lu
 * @since 2026-04-11
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("user_statistics")
@Schema(description = "用户消费统计表")
public class UserStatistics implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 统计记录ID
     */
    @Schema(defaultValue = "统计记录ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    @Schema(defaultValue = "用户ID")
    private Long userId;

    /**
     * 统计月份（YYYY-MM）
     */
    @TableField("stat_month")
    @Schema(defaultValue = "统计月份（YYYY-MM）")
    private String statMonth;

    /**
     * 总收入
     */
    @Schema(defaultValue = "总收入")
    @TableField("total_income")
    private BigDecimal totalIncome;

    /**
     * 总支出
     */
    @Schema(defaultValue = "总支出")
    @TableField("total_expense")
    private BigDecimal totalExpense;

    /**
     * 最后更新时间
     */
    @TableField("updated_at")
    @Schema(defaultValue = "最后更新时间")
    private LocalDateTime updatedAt;
}
