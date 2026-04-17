package lu.smartledger.controller;

import lu.smartledger.common.utls.JsonResponse;
import lu.smartledger.common.utls.JwtUtils;
import lu.smartledger.model.domain.Users;
import lu.smartledger.service.UsersService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/users")
public class UsersController {
    @Autowired
    private UsersService usersService;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/login")
    public JsonResponse login(@RequestBody Users loginUser){
        log.info("=== 收到登录请求 ===");
        log.info("邮箱: {}", loginUser.getEmail());
        
        try {
            Users user = usersService.login(loginUser.getEmail(), loginUser.getPassword());
            log.info("查询到的用户: {}", user);
            
            if (user == null) {
                return JsonResponse.fail("邮箱或密码不正确");
            }

            return switch (user.getStatus()) {
                case "ACTIVE" -> {
                    String token = jwtUtils.createToken(user.getEmail(), user.getRole());//生成JWT

                    Map<String, Object> resultMap = new HashMap<>();
                    resultMap.put("token", token);//返回JWT
                    resultMap.put("user", user);//返回用户信息

                    yield JsonResponse.success("登录成功", resultMap);
                }
                case "PENDING" -> JsonResponse.fail("账号审核中，请耐心等待管理员通过");
                case "DISABLED" -> JsonResponse.fail("该账号已被禁用，请联系客服处理");
                case "BANNED" -> JsonResponse.fail("账号因违规已被封禁，无法登录");
                default -> JsonResponse.fail("未知账号状态：" + user.getStatus());
            };
        } catch (Exception e) {
            log.error("=== 登录异常详情 ===", e);
            throw e;
        }
    }

}
