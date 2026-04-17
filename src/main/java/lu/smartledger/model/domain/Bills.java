package lu.smartledger.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;//一个标记接口
// JVM 会说："哦，这个对象可以被序列化！"
// 然后自动提供序列化能力，即让对象可以在各层之间被使用
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("bills")
@Schema(description = "账单表")
public class Bills implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    @Schema(description = "账单id")
    private Long id;

    @Schema(description = "用户id")
    private Long userId;

    @Schema(description = "账单金额")
    private BigDecimal amount;

    @Schema(description = "账单类型：INCOM / EXPENSE")
    private String billType;

    @Schema(description = "分类id")
    private Long categoryId;

    @Schema(description = "账单来源：MANUAL / IMPORT / OCR")
    private String source; // MANUAL / IMPORT / OCR

    @Schema(description = "导入记录id（可空）")
    private Long importRecordId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "账单日期")
    private Date billDate; // 账单日期
    @Schema(description = "异常得分")
    private BigDecimal anomalyScore; // 异常得分 (孤立森林算法用)
    @Schema(description = "异常类型")
    private String anomalyType;      // 异常类型 (金额异常/频率异常)
    @Schema(description = "异常原因")
    private String anomalyReason;    // 异常原因

    public Bills() {}

}
