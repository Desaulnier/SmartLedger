package lu.smartledger.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 消费分类规则表
 * </p>
 *
 * @author lu
 * @since 2026-04-11
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("category_rules")
@Schema(description = "消费分类规则表")
public class CategoryRules implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 规则唯一ID
     */
    @Schema(defaultValue = "规则唯一ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联消费分类ID
     */
    @TableField("category_id")
    @Schema(defaultValue = "关联消费分类ID")
    private Long categoryId;

    /**
     * 关键字（如：地铁、外卖）
     */
    @TableField("keyword")
    @Schema(defaultValue = "关键字（如：地铁、外卖）")
    private String keyword;

    /**
     * 规则是否启用
     */
    @TableField("is_enabled")
    @Schema(defaultValue = "规则是否启用")
    private Boolean isEnabled;

    /**
     * 创建时间
     */
    @Schema(defaultValue = "创建时间")
    @TableField("created_at")
    private LocalDateTime createdAt;
}
