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
import java.time.LocalDateTime;

/**
 * <p>
 * 管理员操作日志表
 * </p>
 *
 * @author lu
 * @since 2026-04-11
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("admin_action_logs")
@Schema(description = "管理员操作日志表")
public class AdminActionLogs implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 日志唯一ID
     */
    @Schema(defaultValue = "日志唯一ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 管理员ID，外键关联 users 表（仅 role=ADMIN）
     */
    @TableField("admin_id")
    @Schema(defaultValue = "管理员ID，外键关联 users 表（仅 role=ADMIN）")
    private Long adminId;

    /**
     * 操作类型（用户管理 / 账单管理 / 系统设置）
     */
    @TableField("action_type")
    @Schema(defaultValue = "操作类型（用户管理 / 账单管理 / 系统设置）")
    private String actionType;

    /**
     * 操作对象 ID（如用户 ID / 账单 ID）
     */
    @TableField("target_id")
    @Schema(defaultValue = "操作对象 ID（如用户 ID / 账单 ID）")
    private Long targetId;

    /**
     * 操作描述（如禁用用户ID:1001账号）
     */
    @TableField("action_desc")
    @Schema(defaultValue = "操作描述（如禁用用户ID:1001账号）")
    private String actionDesc;

    /**
     * 操作IP地址（可选）
     */
    @TableField("ip_address")
    @Schema(defaultValue = "操作IP地址（可选）")
    private String ipAddress;

    /**
     * 操作时间
     */
    @Schema(defaultValue = "操作时间")
    @TableField("created_at")
    private LocalDateTime createdAt;
}
