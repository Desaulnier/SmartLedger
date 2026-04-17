package lu.smartledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lu.smartledger.model.domain.AdminActionLogs;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 管理员操作日志表 Mapper 接口
 * </p>
 *
 * @author lu
 * @since 2026-04-11
 */
@Mapper
public interface AdminActionLogsMapper extends BaseMapper<AdminActionLogs> {

}
