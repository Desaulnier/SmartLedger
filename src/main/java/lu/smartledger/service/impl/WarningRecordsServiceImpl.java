package lu.smartledger.service.impl;

import lombok.RequiredArgsConstructor;
import lu.smartledger.common.utls.JsonResponse;
import lu.smartledger.mapper.UsersMapper;
import lu.smartledger.model.domain.Users;
import lu.smartledger.model.domain.WarningRecords;
import lu.smartledger.mapper.WarningRecordsMapper;
import lu.smartledger.service.IWarningRecordsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * 超前消费预警记录表 服务实现类
 * </p>
 *
 * @author lu
 * @since 2026-04-11
 */
@Service
@RequiredArgsConstructor
public class WarningRecordsServiceImpl extends ServiceImpl<WarningRecordsMapper, WarningRecords> implements IWarningRecordsService {

    private final UsersMapper usersMapper;

    /**
     * 功能定位：预算风险预测 + 红绿灯警告
     * 算法：阈值规则 + 线性预测
     */
    @Override
    public JsonResponse<Object> getBudgetWarning(Long userId) {
        Users user = usersMapper.selectById(userId);
        if (user == null || user.getMonthlyLimit() == null) {
            return JsonResponse.fail("请先设置月度预算");
        }

        BigDecimal monthlyLimit = user.getMonthlyLimit();
        BigDecimal currentSpent = user.getCurrentSpent() == null ? BigDecimal.ZERO : user.getCurrentSpent();

        //时间计算（LocalDate 处理月末天数，避免错误）
        LocalDate now = LocalDate.now();
        int dayOfMonth = now.getDayOfMonth();
        int lastDay = now.with(TemporalAdjusters.lastDayOfMonth()).getDayOfMonth();
        double progress = (double) dayOfMonth / lastDay;

        //线性预测：预测本月最终花费
        BigDecimal predictTotal = currentSpent.divide(BigDecimal.valueOf(progress), 2, BigDecimal.ROUND_HALF_UP);
        BigDecimal overAmount = predictTotal.subtract(monthlyLimit);
        if (overAmount.compareTo(BigDecimal.ZERO) < 0) {
            overAmount = BigDecimal.ZERO;
        }

        // 4. 阈值规则 → 红绿灯警告
        String level;
        String msg;
        double usage = currentSpent.divide(monthlyLimit, 4, BigDecimal.ROUND_HALF_UP).doubleValue();

        if (usage >= 0.9) {
            level = "RED";    // 红灯：严重超支风险
            msg = "已超预算90%，立即控制消费";
        } else if (usage >= 0.7) {
            level = "YELLOW"; // 黄灯：警告
            msg = "消费较快，请注意控制支出";
        } else {
            level = "GREEN";  // 绿灯：安全
            msg = "消费状态健康，继续保持";
        }

        // 封装返回
        Map<String, Object> data = new HashMap<>();
        data.put("warningLevel", level);
        data.put("message", msg);
        data.put("monthlyLimit", monthlyLimit);
        data.put("currentSpent", currentSpent);
        data.put("usageRate", usage);
        data.put("predictTotal", predictTotal);
        data.put("predictOver", overAmount);

        return JsonResponse.success("获取成功", data);
    }
}
