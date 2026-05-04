package lu.smartledger.service;

import lu.smartledger.model.dto.AllowanceUpdateDTO;
import lu.smartledger.model.dto.BudgetInfoDTO;
import lu.smartledger.model.dto.BudgetUpdateDTO;

import java.util.List;
import java.util.Map;

/**
 * 预算管理服务接口
 * 数据源：Users表（保持与WarningRecords和其他模块的一致性）
 * 不使用独立的Budgets表，避免数据冗余
 */
public interface IBudgetService {

    /**
     * 获取用户预算信息（从Users表读取）
     * @param userId 用户ID
     * @return 预算信息
     */
    BudgetInfoDTO getBudgetInfo(Long userId);

    /**
     * 更新生活费设置（更新Users表）
     * @param userId 用户ID
     * @param dto 生活费更新数据
     * @return 是否成功
     */
    boolean updateAllowance(Long userId, AllowanceUpdateDTO dto);

    /**
     * 更新预算设置（更新Users表）
     * @param userId 用户ID
     * @param dto 预算更新数据
     * @return 是否成功
     */
    boolean updateBudget(Long userId, BudgetUpdateDTO dto);
    List<Map<String, Object>> getWeeklyBreakdown(Long userId);
}