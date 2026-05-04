package lu.smartledger.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 超前消费预警记录表
 * </p>
 *
 * @author lu
 * @since 2026-04-11
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("warning_records")
@Schema( description = "超前消费预警记录表")
public class WarningRecords implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 预警记录ID
     */
    @Schema(defaultValue = "预警记录ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    @Schema(defaultValue = "用户ID")
    private Long userId;

    /**
     * 预警类型
     */
    @Schema(defaultValue = "预警类型")
    @TableField("warning_type")
    private String warningType;

    /**
     * 触发预警的消费金额
     */
    @TableField("trigger_amount")
    @Schema(defaultValue = "触发预警的消费金额")
    private BigDecimal triggerAmount;

    /**
     * 预警提示内容
     */
    @TableField("warning_msg")
    @Schema(defaultValue = "预警提示内容")
    private String warningMsg;

    /**
     * 是否已读
     */
    @TableField("is_read")
    @Schema(defaultValue = "是否已读")
    private Boolean isRead;

    /**
     * 预警触发时间
     */
    @TableField("created_at")
    @Schema(defaultValue = "预警触发时间")
    private LocalDateTime createdAt;

    /**
     * 阈值快照
     */
    @TableField("threshold_snapshot")
    @Schema(defaultValue = "阈值快照")
    private BigDecimal thresholdSnapshot;
}
