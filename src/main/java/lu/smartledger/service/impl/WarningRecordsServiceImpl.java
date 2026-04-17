package lu.smartledger.service.impl;

import lu.smartledger.model.domain.WarningRecords;
import lu.smartledger.mapper.WarningRecordsMapper;
import lu.smartledger.service.IWarningRecordsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 超前消费预警记录表 服务实现类
 * </p>
 *
 * @author lu
 * @since 2026-04-11
 */
@Service
public class WarningRecordsServiceImpl extends ServiceImpl<WarningRecordsMapper, WarningRecords> implements IWarningRecordsService {

}
