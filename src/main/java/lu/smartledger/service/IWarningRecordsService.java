package lu.smartledger.service;

import lu.smartledger.common.utls.JsonResponse;
import lu.smartledger.model.domain.WarningRecords;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 超前消费预警记录表 服务类
 * </p>
 *
 * @author lu
 * @since 2026-04-11
 */
public interface IWarningRecordsService extends IService<WarningRecords> {
    JsonResponse<Object> getBudgetWarning(Long userId);
}
