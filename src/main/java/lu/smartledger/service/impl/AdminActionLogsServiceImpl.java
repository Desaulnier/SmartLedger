package lu.smartledger.service.impl;

import lu.smartledger.model.domain.AdminActionLogs;
import lu.smartledger.mapper.AdminActionLogsMapper;
import lu.smartledger.service.IAdminActionLogsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 管理员操作日志表 服务实现类
 * </p>
 *
 * @author lu
 * @since 2026-04-11
 */
@Service
public class AdminActionLogsServiceImpl extends ServiceImpl<AdminActionLogsMapper, AdminActionLogs> implements IAdminActionLogsService {

}
