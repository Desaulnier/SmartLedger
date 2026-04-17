package lu.smartledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lu.smartledger.model.domain.WarningRecords;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 超前消费预警记录表 Mapper 接口
 * </p>
 *
 * @author lu
 * @since 2026-04-11
 */
@Mapper
public interface WarningRecordsMapper extends BaseMapper<WarningRecords> {

}
