package lu.smartledger.service;

import com.baomidou.mybatisplus.extension.service.IService;
import lu.smartledger.common.utls.JsonResponse;
import lu.smartledger.model.domain.WarningRecords;

import java.util.Map;

public interface IWarningRecordsService extends IService<WarningRecords> {

    JsonResponse<Object> getBudgetWarning(Long userId);

    JsonResponse<Object> checkConsumptionWarning(Long userId, Map<String, Object> params);

    JsonResponse<Object> getWarningRecords(Long userId);

    JsonResponse<Object> getUnreadCount(Long userId);

    JsonResponse<Object> markAllAsRead(Long userId);
}
