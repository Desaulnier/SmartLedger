package lu.smartledger.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lu.smartledger.common.utls.JsonResponse;
import lu.smartledger.common.utls.JwtUtils;
import lu.smartledger.mapper.UsersMapper;
import lu.smartledger.model.domain.RegisterRequest;
import lu.smartledger.model.domain.Users;
import lu.smartledger.model.dto.ChangePasswordRequest;
import lu.smartledger.model.dto.ProfileUpdateRequest;
import lu.smartledger.model.dto.ResetPasswordRequest;
import lu.smartledger.service.UsersService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;


@Slf4j
@RestController
@RequestMapping("/users")
public class UsersController {
    @Autowired
    private UsersService usersService;

    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private UsersMapper usersMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private JavaMailSender mailSender;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");//邮箱正则表达式
    @Autowired
    private JavaMailSender javaMailSender;

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

                    user.setPassword(null);
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

    @GetMapping("/send-code")
    public JsonResponse sendCode(@RequestParam String email){
        if(email == null || email.trim().isEmpty()) return JsonResponse.fail("邮箱不能为空");
        email = email.trim();

        if(!EMAIL_PATTERN.matcher(email).matches()) return JsonResponse.fail("邮箱格式不正确");
        Users exitUsers = usersService.geByEmail(email);
        if(exitUsers != null) return JsonResponse.fail("该邮箱已注册");

        String code = String.format("%06d", (int)(Math.random() * 1000000));

        String rediskey = "register:code:" + email;
        redisTemplate.opsForValue().set(rediskey, code, 5, TimeUnit.MINUTES);
        log.info("邮箱: {}，验证码已存入 Redis，验证码: {}", email, code);
        try{
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("2055346449@qq.com");
            message.setTo(email);
            message.setSubject("注册验证码");
            message.setText("您的注册验证码为：" + code + ",5分钟内有效");

            javaMailSender.send(message);
            log.info("已发送验证码到邮箱: {}", email);
            return JsonResponse.success("验证码已发送");
        }catch (Exception e){
            log.error("发送验证码异常", e);
            return JsonResponse.fail("发送验证码异常");
        }
    }
    /**
     * 获取验证码
     */
    @GetMapping("/info")
    public JsonResponse getBudgetInfo() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();//获取当前登录用户的邮箱
        Users user = usersMapper.selectOne(
                new QueryWrapper<Users>()
                        .eq("email", email)
        );
        if (user == null) return JsonResponse.fail("用户不存在");

        BigDecimal limit = user.getMonthlyLimit() != null ? user.getMonthlyLimit() : BigDecimal.ZERO;
        BigDecimal spent = user.getCurrentSpent() != null ? user.getCurrentSpent() : BigDecimal.ZERO;
        BigDecimal remaining = limit.subtract(spent);

        Map<String, Object> result = new HashMap<>();
        result.put("monthlyLimit", limit);
        result.put("currentSpent", spent);
        result.put("remaining", remaining);
        result.put("emergencyFund", user.getEmergencyFund() != null ? user.getEmergencyFund() : BigDecimal.ZERO);

        return JsonResponse.success(result);
    }

    @GetMapping("/admin/list")
    public JsonResponse getAdminUserList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword) {
        
        try {
            Page<Users> result = usersService.getAdminUserList(pageNum, pageSize, status, role, keyword);
            return JsonResponse.success(result);
        } catch (Exception e) {
            return JsonResponse.fail("获取用户列表失败：" + e.getMessage());
        }
    }

    @PostMapping("/register")
    public JsonResponse register(@RequestBody RegisterRequest registerRequest) {
       try{
           boolean success = usersService.register(registerRequest);
           if( success){
               return JsonResponse.success("注册成功");
           }
           return JsonResponse.fail("注册失败");
       }catch (RuntimeException e){
           return JsonResponse.fail(e.getMessage());
       }
    }
    /**
     * 发送重置密码验证码
     * */
    @GetMapping("/send-reset-code")
    public JsonResponse sendResetCode(@RequestParam String email) {
        if (email == null || email.trim().isEmpty()) {
            return JsonResponse.fail("邮箱不能为空");
        }

        email = email.trim();

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return JsonResponse.fail("邮箱格式不正确");
        }

        Users existUser = usersService.geByEmail(email);
        if (existUser == null) {
            return JsonResponse.fail("该邮箱尚未注册");
        }

        String code = String.format("%06d", (int) (Math.random() * 1000000));
        String redisKey = "reset:code:" + email;

        redisTemplate.opsForValue().set(redisKey, code, 5, TimeUnit.MINUTES);
        log.info("邮箱: {}，重置密码验证码已存入 Redis，验证码: {}", email, code);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("2055346449@qq.com");
            message.setTo(email);
            message.setSubject("找回密码验证码");
            message.setText("您的密码重置验证码为：" + code + "，5分钟内有效。");

            javaMailSender.send(message);
            log.info("已发送重置密码验证码到邮箱: {}", email);
            return JsonResponse.success("验证码已发送");
        } catch (Exception e) {
            log.error("发送重置密码验证码异常", e);
            return JsonResponse.fail("发送验证码异常");
        }
    }
    @PostMapping("/reset-password")//重置密码
    public JsonResponse resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            boolean success = usersService.resetPassword(request);
            if (success) {
                return JsonResponse.success("密码重置成功");
            }
            return JsonResponse.fail("密码重置失败");
        } catch (RuntimeException e) {
            return JsonResponse.fail(e.getMessage());
        }
    }
    /**
     * 获取当前用户的个人资料
     */
    @GetMapping("/profile")
    public JsonResponse getProfile() {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            Users user = usersService.getCurrentUserProfile(email);
            return JsonResponse.success(user);
        } catch (RuntimeException e) {
            return JsonResponse.fail(e.getMessage());
        }
    }

    /**
     * 更新当前用户的个人资料
     */
    @PutMapping("/profile")
    public JsonResponse updateProfile(@RequestBody ProfileUpdateRequest request) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            boolean success = usersService.updateCurrentUserProfile(email, request);
            if (success) {
                return JsonResponse.success("个人资料更新成功");
            }
            return JsonResponse.fail("个人资料更新失败");
        } catch (RuntimeException e) {
            return JsonResponse.fail(e.getMessage());
        }
    }

    @PutMapping("/password")
    public JsonResponse changePassword(@RequestBody ChangePasswordRequest request) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            boolean success = usersService.changeCurrentUserPassword(email, request);
            if (success) {
                return JsonResponse.success("密码修改成功");
            }
            return JsonResponse.fail("密码修改失败");
        } catch (RuntimeException e) {
            return JsonResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/avatar")
    public JsonResponse uploadAvatar(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return JsonResponse.fail("上传文件不能为空");
            }

            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return JsonResponse.fail("只能上传图片文件");
            }

            // 固定D盘目录 + 自动判断创建
            File avatarDir = new File("D:/Graduation Project/SmartLedger/file/avatar");
            if (!avatarDir.exists()) {
                avatarDir.mkdirs();
            }

            String originalFilename = file.getOriginalFilename();
            String suffix = ".png";
            if (originalFilename != null && originalFilename.contains(".")) {
                suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String fileName = "avatar_" + UUID.randomUUID().toString().replace("-", "") + suffix;
            File targetFile = new File(avatarDir, fileName);
            file.transferTo(targetFile);

            // 直接返回完整后端地址，前端不会404！
            return JsonResponse.success("头像上传成功", "http://localhost:8082/file/avatar/" + fileName);
        } catch (IOException e) {
            log.error("头像上传失败", e);
            return JsonResponse.fail("头像上传失败：" + e.getMessage());
        }
    }
}
