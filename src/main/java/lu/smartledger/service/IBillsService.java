package lu.smartledger.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lu.smartledger.common.utls.JsonResponse;
import com.baomidou.mybatisplus.extension.service.IService;
import lu.smartledger.model.domain.Bills;
import lu.smartledger.model.dto.MonthlyAnalysisDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 账单表 服务类
 * </p>
 *
 * @author lu
 * @since 2026-04-14
 */
public interface IBillsService extends IService<Bills> {

    JsonResponse<Map<String, Object>> parseBillFile(MultipartFile file);

    public void confirmImport(Long importRecordId, List<Map<String, Object>> billList, Long userId);

    public void saveBills(Bills bill, Long userId);

    Page<Bills> getUserBillList(Long userId, Integer pageNum, Integer pageSize, String type, String category, String startDate, String endDate, String keyword);

    Map<String, Object> getMonthlyStatistics(Long userId, String month);

    void deleteBills(Long billId, Long userId);

    void updateBill(Bills bill, Long userId);
    // 月度消费分析
    MonthlyAnalysisDTO getMonthlyAnalysis(Long userId, String monthStr);
    // 智能消费建议
    List<String> getAnalysisSuggestions(Long userId, String monthStr);
    Page<Bills> getAbnormalBillList(Long userId, Integer pageNum, Integer pageSize);
    List<Map<String, Object>> getDailyTrend(Long userId, String monthStr);
}
