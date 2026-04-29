package lu.smartledger.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lu.smartledger.common.utls.JsonResponse;
import lu.smartledger.mapper.UsersMapper;
import lu.smartledger.model.domain.Users;
import lu.smartledger.service.IBillsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/statistics")
public class UserStatisticsController {
    
    @Autowired
    private IBillsService billsService;

    @Autowired
    private UsersMapper usersMapper;

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

    private Long getCurrentUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Users user = usersMapper.selectOne(
                new QueryWrapper<Users>().eq("email", email)
        );
        return user != null ? user.getId() : null;
    }
}
