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
import java.time.LocalDateTime;

/**
 * <p>
 * 消费分类表
 * </p>
 *
 * @author lu
 * @since 2026-04-11
 */
@Data
@Accessors(chain = true)
@TableName("categories")
@Schema(description = "消费分类表")
public class Categories implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消费类别唯一ID
     */
    @Schema(defaultValue = "消费类别唯一ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 分类名称（如：餐饮、工资）
     */
    @TableField("name")
    @Schema(defaultValue = "分类名称（如：餐饮、工资）")
    private String name;

    /**
     * 分类类型（收入 / 支出）
     */
    @TableField("type")
    @Schema(defaultValue = "分类类型（收入 / 支出）")
    private String type;

    /**
     * 是否为系统默认分类（1 是，0 否）
     */
    @TableField("is_default")
    @Schema(defaultValue = "是否为系统默认分类（1 是，0 否）")
    private Boolean isDefault;

    /**
     * 创建时间
     */
    @Schema(defaultValue = "创建时间")
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 默认消费属性：1=必需, 2=改善, 3=欲望
     */
    @TableField("default_attribute")
    @Schema(defaultValue = "默认消费属性：1=必需, 2=改善, 3=欲望")
    private Byte defaultAttribute;

    /**
     * 默认消费维度：1=生存必需, 2=生活改善, 3=欲望消费
     */
    @TableField("default_type")
    @Schema(defaultValue = "默认消费维度：1=生存必需, 2=生活改善, 3=欲望消费")
    private Byte defaultType;
}
