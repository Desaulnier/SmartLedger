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
 * 用户资金账户表
 * </p>
 *
 * @author lu
 * @since 2026-04-11
 */
@Getter
@Setter
@ToString
@TableName("accounts")
@Accessors(chain = true)
@Schema(description = "用户资金账户表")
public class Accounts implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 账户ID（主键）
     */
    @Schema(defaultValue = "账户ID（主键）")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联用户ID（外键，关联users表）
     */
    @TableField("user_id")
    @Schema(defaultValue = "关联用户ID（外键，关联users表）")
    private Long userId;

    /**
     * 账户名称（如：微信零钱、工商银行卡）
     */
    @TableField("account_name")
    @Schema(defaultValue = "账户名称（如：微信零钱、工商银行卡）")
    private String accountName;

    /**
     * 账户类型：1=现金，2=银行卡，3=支付宝，4=微信，5=其他
     */
    @TableField("account_type")
    @Schema(defaultValue = "账户类型：1=现金，2=银行卡，3=支付宝，4=微信，5=其他")
    private Byte accountType;

    /**
     * 账户当前余额
     */
    @TableField("balance")
    @Schema(defaultValue = "账户当前余额")
    private BigDecimal balance;

    /**
     * 是否默认账户：0=否，1=是（新增账单默认选中）
     */
    @TableField("is_default")
    @Schema(defaultValue = "是否默认账户：0=否，1=是（新增账单默认选中）")
    private Byte isDefault;

    /**
     * 逻辑删除：0=未删除，1=已删除
     */
    @TableField("is_deleted")
    @Schema(defaultValue = "逻辑删除：0=未删除，1=已删除")
    private Byte isDeleted;

    /**
     * 创建时间
     */
    @Schema(defaultValue = "创建时间")
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Schema(defaultValue = "更新时间")
    @TableField("update_time")
    private LocalDateTime updateTime;
}
