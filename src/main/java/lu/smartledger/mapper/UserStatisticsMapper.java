package lu.smartledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lu.smartledger.model.domain.UserStatistics;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 用户消费统计表 Mapper 接口
 * </p>
 *
 * @author lu
 * @since 2026-04-11
 */
@Mapper
public interface UserStatisticsMapper extends BaseMapper<UserStatistics> {

}
