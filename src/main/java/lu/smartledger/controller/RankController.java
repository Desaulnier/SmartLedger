package lu.smartledger.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.Resource;
import lu.smartledger.common.utls.JsonResponse;
import lu.smartledger.mapper.UsersMapper;
import lu.smartledger.model.domain.Users;
import lu.smartledger.model.dto.RankItemDTO;
import lu.smartledger.service.IRankService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rank")
public class RankController {

    @Resource
    private IRankService rankService;

    @Resource
    private UsersMapper usersMapper;

    @GetMapping("/leaderboard")
    public JsonResponse<List<RankItemDTO>> getLeaderboard(
            @RequestParam(defaultValue = "month") String period) {
        try {
            Long currentUserId = getCurrentUserId();
            if (currentUserId == null) {
                return JsonResponse.fail("用户未登录");
            }

            List<RankItemDTO> list = rankService.getLeaderboard(currentUserId, period);
            return JsonResponse.success("获取排行榜成功", list);
        } catch (Exception e) {
            return JsonResponse.fail("获取排行榜失败：" + e.getMessage());
        }
    }
    /**
     * 获取用户成就
     */
    @GetMapping("/achievements")
    public JsonResponse<List<Map<String, Object>>> getAchievements() {
        try {
            Long currentUserId = getCurrentUserId();
            if (currentUserId == null) {
                return JsonResponse.fail("用户未登录");
            }

            List<Map<String, Object>> list = rankService.getAchievements(currentUserId);
            return JsonResponse.success("获取成就成功", list);
        } catch (Exception e) {
            return JsonResponse.fail("获取成就失败：" + e.getMessage());
        }
    }

    /**
     * 获取个人健康分
     */
    @GetMapping("/me")
    public JsonResponse<RankItemDTO> getMyRank(
            @RequestParam(defaultValue = "month") String period) {
        try {
            Long currentUserId = getCurrentUserId();
            if (currentUserId == null) {
                return JsonResponse.fail("用户未登录");
            }

            RankItemDTO dto = rankService.getMyRank(currentUserId, period);
            return JsonResponse.success("获取个人健康分成功", dto);
        } catch (Exception e) {
            return JsonResponse.fail("获取个人健康分失败：" + e.getMessage());
        }
    }


    private Long getCurrentUserId() {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            Users user = usersMapper.selectOne(new QueryWrapper<Users>().eq("email", email));
            return user != null ? user.getId() : null;
        } catch (Exception e) {
            return null;
        }
    }
}