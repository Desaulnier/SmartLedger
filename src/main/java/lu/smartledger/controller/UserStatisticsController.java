package lu.smartledger.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lu.smartledger.common.utls.JsonResponse;
import lu.smartledger.mapper.UsersMapper;
import lu.smartledger.model.domain.Users;
import lu.smartledger.model.dto.MonthlyAnalysisDTO;
import lu.smartledger.service.IBillsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/statistics")
public class UserStatisticsController {
    
    @Autowired
    private IBillsService billsService;

    @Autowired
    private UsersMapper usersMapper;

    /**
     * 获取当前用户的月度统计信息
     *
     * @param month 查询的月份，格式为 "yyyy-MM"，如 "2023-04" 表示查询四月份的统计信息
     * @return 包含月度统计信息的 JSON 响应
     */
    @GetMapping("/monthly")
    public JsonResponse getMonthlyStats(@RequestParam(required = false) String month) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return JsonResponse.fail("用户未登录");
            }

            if (month == null || month.isEmpty()) {
                month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            }

            Map<String, Object> stats = billsService.getMonthlyStatistics(userId, month);
            return JsonResponse.success(stats);
        } catch (Exception e) {
            return JsonResponse.fail("获取月度统计失败：" + e.getMessage());
        }
    }

    @GetMapping("/analysis/monthly")
    public JsonResponse getMonthlyAnalysis(@RequestParam(required = false) String month) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return JsonResponse.fail("用户未登录");
            }

            if (month == null || month.isEmpty()) {
                month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            }

            MonthlyAnalysisDTO analysis = billsService.getMonthlyAnalysis(userId, month);
            return JsonResponse.success(analysis);
        } catch (Exception e) {
            return JsonResponse.fail("获取月度分析失败：" + e.getMessage());
        }
    }

    /**
     * 获取智能消费建议（对应前端 /statistics/analysis/suggestions）
     * 返回：基于消费数据的个性化建议列表
     */
    @GetMapping("/analysis/suggestions")
    public JsonResponse getAnalysisSuggestions(@RequestParam(required = false) String month) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return JsonResponse.fail("用户未登录");
            }

            if (month == null || month.isEmpty()) {
                month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            }

            List<String> suggestions = billsService.getAnalysisSuggestions(userId, month);

            // 转换为前端期望的格式 [{title, content, icon}]
            List<Map<String, String>> formattedSuggestions = suggestions.stream()
                    .map(suggestion -> {
                        Map<String, String> map = new HashMap<>();
                        map.put("title", "消费建议");
                        map.put("content", suggestion);
                        map.put("icon", "💡");
                        return map;
                    })
                    .toList();

            return JsonResponse.success(formattedSuggestions);
        } catch (Exception e) {
            return JsonResponse.fail("获取消费建议失败：" + e.getMessage());
        }
    }

    /**
     * 获取属性分析（对应前端 /statistics/analysis/attribute）
     * 返回：基于消费数据的属性分析结果
     */
    @GetMapping("/analysis/attribute")
    public JsonResponse getAttributeAnalysis(@RequestParam(required = false) String month) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return JsonResponse.fail("用户未登录");
            }

            if (month == null || month.isEmpty()) {
                month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            }

            MonthlyAnalysisDTO analysis = billsService.getMonthlyAnalysis(userId, month);

            Map<String, Object> result = new HashMap<>();
            result.put("necessary", analysis.getNecessaryExpense());
            result.put("improve", analysis.getImproveExpense());
            result.put("desire", analysis.getDesireExpense());
            result.put("totalExpense", analysis.getTotalExpense());

            return JsonResponse.success(result);
        } catch (Exception e) {
            return JsonResponse.fail("获取属性分析失败：" + e.getMessage());
        }
    }

    /**
     * 获取消费趋势（对应前端 /statistics/analysis/trend）
     * 返回：基于消费数据的消费趋势结果
     */
    @GetMapping("/analysis/trend")
    public JsonResponse getTrendAnalysis(@RequestParam(required = false) String month) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return JsonResponse.fail("用户未登录");
            }

            if (month == null || month.isEmpty()) {
                month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            }

            List<Map<String, Object>> trendData = billsService.getDailyTrend(userId, month);
            return JsonResponse.success(trendData);
        } catch (Exception e) {
            return JsonResponse.fail("获取趋势分析失败：" + e.getMessage());
        }
    }
    private Long getCurrentUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Users user = usersMapper.selectOne(
                new QueryWrapper<Users>().eq("email", email)
        );
        return user != null ? user.getId() : null;
    }
}
