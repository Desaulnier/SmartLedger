package lu.smartledger.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("users")
@Schema(description = "系统用户表")
public class Users implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 主键ID
     * @TableId：指定主键字段，type=IdType.AUTO表示自增（和数据库表的AUTO_INCREMENT对应）
     */
    @TableId(type = IdType.AUTO)
    @Schema(description = "用户id")
    private Long id;

    @Schema(description = "用户邮箱（登录账号）")
    private String email;

    @Schema(description = "加密后的用户密码")
    private String password;

    @Schema(description = "用户昵称")
    private String username;
    @Schema(description = "用户手机号")
    private String phone;

    @Schema(description = "用户头像链接")
    private String avatarUrl;

    @Schema(description = "用户角色：USER / ADMIN")
    private String role;   // USER / ADMIN
    @Schema(description = "用户状态")
    private String status; // PENDING / ACTIVE / DISABLED / BANNED

    @Schema(description = "是否启用超前消费预警")
    private Boolean isWarningEnabled;

    @Schema(description = "月生活费")
    private BigDecimal monthlyAllowance;

    @Schema(description = "月度预算")
    private BigDecimal monthlyLimit;

    @Schema(description = "每日生存保障")
    private BigDecimal dailySurvivalCost;

    @Schema(description = "预警阈值比例")
    private BigDecimal warningThreshold;

    @Schema(description = "当月已消费金额")
    private BigDecimal currentSpent;

    @Schema(description = "限额所属月份")
    private String limitMonth;

    @Schema(description = "周预算")
    private BigDecimal weeklyBudget;

    @Schema(description = "应急资金")
    private BigDecimal emergencyFund;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "已获得成就JSON")
    private String achievementData;

    @Schema(description = "成就上次结算月份")
    private String achievementLastSettleMonth;

    @Schema(description = "当前账户ID")
    private Long currentAccountId;


    public Users(){}//调用接口时先创造这样一个对象方便输入数据
}
