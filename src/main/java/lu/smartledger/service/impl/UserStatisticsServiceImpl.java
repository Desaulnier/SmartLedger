package lu.smartledger.service.impl;

import lu.smartledger.model.domain.UserStatistics;
import lu.smartledger.mapper.UserStatisticsMapper;
import lu.smartledger.service.IUserStatisticsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 用户消费统计表 服务实现类
 * </p>
 *
 * @author lu
 * @since 2026-04-11
 */
@Service
public class UserStatisticsServiceImpl extends ServiceImpl<UserStatisticsMapper, UserStatistics> implements IUserStatisticsService {

}
