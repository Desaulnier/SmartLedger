package lu.smartledger.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lu.smartledger.common.utls.JsonResponse;
import lu.smartledger.mapper.UsersMapper;
import lu.smartledger.model.domain.Users;
import lu.smartledger.service.IWarningRecordsService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/warningRecords")
@RequiredArgsConstructor
public class WarningRecordsController {

    private final IWarningRecordsService warningRecordsService;
    private final UsersMapper usersMapper;

    @GetMapping("/budget-warning")
    public JsonResponse<Object> getBudgetWarning() {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return JsonResponse.fail("用户不存在");
            }
            return warningRecordsService.getBudgetWarning(userId);
        } catch (Exception e) {
            return JsonResponse.fail("获取预算预警失败：" + e.getMessage());
        }
    }

    @PostMapping("/check")
    public JsonResponse<Object> checkConsumptionWarning(@RequestBody Map<String, Object> params) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return JsonResponse.fail("用户不存在");
            }
            return warningRecordsService.checkConsumptionWarning(userId, params);
        } catch (Exception e) {
            return JsonResponse.fail("消费预警检查失败：" + e.getMessage());
        }
    }

    @GetMapping("/records")
    public JsonResponse<Object> getWarningRecords() {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return JsonResponse.fail("用户不存在");
            }
            return warningRecordsService.getWarningRecords(userId);
        } catch (Exception e) {
            return JsonResponse.fail("获取预警记录失败：" + e.getMessage());
        }
    }

    @GetMapping("/unread-count")
    public JsonResponse<Object> getUnreadCount() {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return JsonResponse.fail("用户不存在");
            }
            return warningRecordsService.getUnreadCount(userId);
        } catch (Exception e) {
            return JsonResponse.fail("获取未读数量失败：" + e.getMessage());
        }
    }

    @PutMapping("/read-all")
    public JsonResponse<Object> markAllAsRead() {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return JsonResponse.fail("用户不存在");
            }
            return warningRecordsService.markAllAsRead(userId);
        } catch (Exception e) {
            return JsonResponse.fail("更新已读状态失败：" + e.getMessage());
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
