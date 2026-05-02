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
 * 账单导入记录表
 * </p>
 *
 * @author lu
 * @since 2026-04-11
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("bill_import_records")
@Schema(description = "账单导入记录表")
public class BillImportRecords implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 导入记录唯一ID
     */
    @Schema(defaultValue = "导入记录唯一ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID，外键关联用户表
     */
    @TableField("user_id")
    @Schema(defaultValue = "用户ID，外键关联用户表")
    private Long userId;

    /**
     * 导入的账单文件名
     */
    @TableField("file_name")
    @Schema(defaultValue = "导入的账单文件名")
    private String fileName;

    /**
     * 文件类型
     */
    @TableField("file_type")
    @Schema(defaultValue = "文件类型")
    private String fileType;

    /**
     * 导入账单的总行数
     */
    @TableField("total_count")
    @Schema(defaultValue = "导入账单的总行数")
    private Integer totalCount;

    /**
     * 成功解析并导入的行数
     */
    @TableField("success_count")
    @Schema(defaultValue = "成功解析并导入的行数")
    private Integer successCount;

    /**
     * 失败原因，记录导入失败的详情
     */
    @TableField("fail_reason")
    @Schema(defaultValue = "失败原因，记录导入失败的详情")
    private String failReason;

    /**
     * 导入时间
     */
    @Schema(defaultValue = "导入时间")
    @TableField("import_time")
    private LocalDateTime importTime;
}
