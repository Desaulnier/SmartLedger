package lu.smartledger.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lu.smartledger.common.utls.JsonResponse;
import lu.smartledger.mapper.UsersMapper;
import lu.smartledger.model.domain.Users;
import lu.smartledger.model.dto.AllowanceUpdateDTO;
import lu.smartledger.model.dto.BudgetInfoDTO;
import lu.smartledger.model.dto.BudgetUpdateDTO;
import lu.smartledger.service.IBudgetService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 预算管理 前端控制器
 * </p>
 *
 * @author lu
 * @since 2026-05-03
 */
@Tag(name = "预算管理")
@RestController
@RequestMapping("/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final IBudgetService budgetService;
    private final UsersMapper usersMapper;

    @Operation(summary = "获取预算信息")
    @GetMapping("/info")
    public JsonResponse<BudgetInfoDTO> getBudgetInfo() {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            Long userId = getUserIdByEmail(email);
            BudgetInfoDTO budgetInfo = budgetService.getBudgetInfo(userId);
            return JsonResponse.success("获取成功", budgetInfo);
        } catch (Exception e) {
            return JsonResponse.fail("获取预算信息失败：" + e.getMessage());
        }
    }

    /**
     * 更新生活费设置
     */
    @Operation(summary = "更新生活费设置")
    @PutMapping("/allowance")
    public JsonResponse<String> updateAllowance(@Validated @RequestBody AllowanceUpdateDTO dto) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            Long userId = getUserIdByEmail(email);
            boolean success = budgetService.updateAllowance(userId, dto);
            if (success) {
                return JsonResponse.success("生活费设置更新成功");
            } else {
                return JsonResponse.fail("生活费设置更新失败");
            }
        } catch (Exception e) {
            return JsonResponse.fail("更新生活费设置失败：" + e.getMessage());
        }
    }

    /**
     * 更新预算设置
     */
    @Operation(summary = "更新预算设置")
    @PutMapping
    public JsonResponse<String> updateBudget(@Validated @RequestBody BudgetUpdateDTO dto) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            Long userId = getUserIdByEmail(email);
            boolean success = budgetService.updateBudget(userId, dto);
            if (success) {
                return JsonResponse.success("预算设置更新成功");
            } else {
                return JsonResponse.fail("预算设置更新失败");
            }
        } catch (Exception e) {
            return JsonResponse.fail("更新预算设置失败：" + e.getMessage());
        }
    }

    // 临时方法，需要实现获取userId的逻辑
    private Long getUserIdByEmail(String email) {
        Users user = usersMapper.selectOne(
            new QueryWrapper<Users>().eq("email", email)
        );
        return user != null ? user.getId() : null;
    }
    @GetMapping("/weekly-breakdown")
    public JsonResponse<Object> getWeeklyBreakdown() {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            Long userId = getUserIdByEmail(email);
            return JsonResponse.success("获取成功", budgetService.getWeeklyBreakdown(userId));
        } catch (Exception e) {
            return JsonResponse.fail("获取周预算拆解失败：" + e.getMessage());
        }
    }
}