package lu.smartledger.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 账单表
 * </p>
 *
 * @author lu
 * @since 2026-04-24
 */
@Data
@TableName("bills")
@Accessors(chain = true)
@Schema(description = "账单表")
public class Bills implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 账单唯一ID
     */
    @Schema(defaultValue = "账单唯一ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     */
    @TableField("user_id")
    @Schema(defaultValue = "所属用户ID")
    private Long userId;

    /**
     * 关联账户ID（外键，关联accounts表）
     */
    @TableField("account_id")
    @Schema(defaultValue = "关联账户ID（外键，关联accounts表）")
    private Long accountId;

    /**
     * 账单金额
     */
    @TableField("amount")
    @Schema(defaultValue = "账单金额")
    private BigDecimal amount;

    /**
     * 账单类型（收入 / 支出）
     */
    @TableField("bill_type")
    @Schema(defaultValue = "账单类型（收入 / 支出）")
    private String billType;

    /**
     * 消费分类ID
     */
    @TableField("category_id")
    @Schema(defaultValue = "消费分类ID")
    private Long categoryId;

    /**
     * 账单发生时间
     */
    @TableField("occur_time")
    @Schema(defaultValue = "账单发生时间")
    private LocalDateTime occurTime;

    /**
     * 账单来源
     */
    @TableField("source")
    @Schema(defaultValue = "账单来源")
    private String source;

    /**
     * 外键，关联账单导入记录ID（若为导入账单）
     */
    @TableField("import_record_id")
    @Schema(defaultValue = "外键，关联账单导入记录ID（若为导入账单）")
    private Long importRecordId;

    /**
     * 账单备注
     */
    @TableField("remark")
    @Schema(defaultValue = "账单备注")
    private String remark;

    /**
     * 创建时间
     */
    @Schema(defaultValue = "创建时间")
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Schema(defaultValue = "更新时间")
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 收入来源
     */
    @Schema(defaultValue = "收入来源")
    @TableField("income_source")
    private String incomeSource;

    /**
     * 支出方式
     */
    @Schema(defaultValue = "支出方式")
    @TableField("expense_method")
    private String expenseMethod;

    /**
     * 消费属性：1=生存必需, 2=改善生活, 3=欲望消费
     */
    @TableField("consumption_attribute")
    @Schema(defaultValue = "消费属性：1=生存必需, 2=改善生活, 3=欲望消费")
    private Byte consumptionAttribute;

    /**
     * 记录消费发生时系统给出的决策建议（红/黄/绿灯状态）
     */
    @TableField("pre_decision_advice")
    @Schema(defaultValue = "记录消费发生时系统给出的决策建议（红/黄/绿灯状态）")
    private String preDecisionAdvice;

    /**
     * 异常得分(0-1)，由孤立森林算法计算
     */
    @TableField("anomaly_score")
    @Schema(defaultValue = "异常得分(0-1)，由孤立森林算法计算")
    private BigDecimal anomalyScore;

    /**
     * 异常类型（如：金额异常、频率异常等）
     */
    @TableField("anomaly_type")
    @Schema(defaultValue = "异常类型（如：金额异常、频率异常等）")
    private String anomalyType;

    /**
     * 异常原因详细描述
     */
    @Schema(defaultValue = "异常原因详细描述")
    @TableField("anomaly_reason")
    private String anomalyReason;
    /**
     * 是否异常
     */
    @Schema(defaultValue = "是否异常")
    @TableField("is_abnormal")
    private Boolean isAbnormal;
}
