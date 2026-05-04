package lu.smartledger.service.impl;

import ch.qos.logback.core.util.StringUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lu.smartledger.common.utls.JsonResponse;
import lu.smartledger.mapper.UsersMapper;
import lu.smartledger.model.domain.Accounts;
import lu.smartledger.model.domain.RegisterRequest;
import lu.smartledger.model.domain.Users;
import lu.smartledger.model.dto.ChangePasswordRequest;
import lu.smartledger.model.dto.ProfileUpdateRequest;
import lu.smartledger.model.dto.ResetPasswordRequest;
import lu.smartledger.service.IAccountsService;
import lu.smartledger.service.UsersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class UsersServiceImpl implements UsersService {
    @Autowired
    private UsersMapper usersMapper;//依赖注入：把UsersMapper（数据库操作层）注入进来，才能操作数据库

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IAccountsService accountsService;
    @Override
    public Users geByEmail(String email) {//查询用户
        return usersMapper.selectOne(new QueryWrapper<Users>().eq("email", email));
    }

    @Override
    public boolean register(RegisterRequest request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new RuntimeException("用户名不能为空");
        }
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new RuntimeException("邮箱不能为空");
        }
        if (request.getPhone() == null || request.getPhone().trim().isEmpty()) {
            throw new RuntimeException("手机号不能为空");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new RuntimeException("密码不能为空");
        }
        if (request.getConfirmPassword() == null || request.getConfirmPassword().trim().isEmpty()) {
            throw new RuntimeException("确认密码不能为空");
        }
        if (request.getCode() == null || request.getCode().trim().isEmpty()) {
            throw new RuntimeException("验证码不能为空");
        }

        String email = request.getEmail().trim();

        Users existUser = usersMapper.selectOne(new QueryWrapper<Users>().eq("email", email));
        if (existUser != null) {
            throw new RuntimeException("该邮箱已被注册");
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("两次输入的密码不一致");
        }

        String redisKey = "register:code:" + email;
        String redisCode = stringRedisTemplate.opsForValue().get(redisKey);

        if (redisCode == null) {
            throw new RuntimeException("验证码已过期，请重新获取");
        }

        System.out.println("前端输入验证码: " + request.getCode().trim());
        System.out.println("Redis中的验证码: " + redisCode);

        if (!redisCode.equals(request.getCode().trim())) {
            throw new RuntimeException("验证码错误");
        }

        Users user = new Users();
        user.setUsername(request.getUsername().trim());
        user.setEmail(email);
        user.setPhone(request.getPhone().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setIsWarningEnabled(true);
        user.setMonthlyLimit(BigDecimal.ZERO);
        user.setWarningThreshold(new BigDecimal("0.80"));
        user.setCurrentSpent(BigDecimal.ZERO);
        user.setWeeklyBudget(BigDecimal.ZERO);
        user.setEmergencyFund(BigDecimal.ZERO);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setMonthlyAllowance(BigDecimal.ZERO);
        user.setMonthlyLimit(BigDecimal.ZERO);
        user.setDailySurvivalCost(BigDecimal.ZERO);
        user.setWeeklyBudget(BigDecimal.ZERO);
        user.setEmergencyFund(BigDecimal.ZERO);

        int rows = usersMapper.insert(user);
        if (rows <= 0) {
            throw new RuntimeException("用户保存失败");
        }

        Accounts defaultAccount = new Accounts();
        defaultAccount.setUserId(user.getId());
        defaultAccount.setAccountName("默认账本");
        defaultAccount.setAccountType((byte) 5);
        defaultAccount.setBalance(BigDecimal.ZERO);
        defaultAccount.setIsDefault((byte) 1);
        defaultAccount.setIsDeleted((byte) 0);
        defaultAccount.setCreateTime(LocalDateTime.now());
        defaultAccount.setUpdateTime(LocalDateTime.now());

        accountsService.save(defaultAccount);

        stringRedisTemplate.delete(redisKey);
        return true;
    }


    @Override
    public Users login(String email, String password) {
        Users user = usersMapper.selectOne(new QueryWrapper<Users>().eq("email", email));
        if(user == null) return null;
        if(!passwordEncoder.matches(password, user.getPassword())) return null;
        return user;
    }

    @Override
    public Page<Users> getAdminUserList(Integer pageNum, Integer pageSize, String status, String role, String keyword) {
        // 防止分页参数为null
        pageNum = pageNum == null ? 1 : pageNum;
        pageSize = pageSize == null ? 10 : pageSize;

        Page<Users> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Users> wrapper = new LambdaQueryWrapper<>();

        // 按状态筛选
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Users::getStatus, status);
        }

        // 按角色筛选
        if (role != null && !role.isEmpty()) {
            wrapper.eq(Users::getRole, role);
        }

        // 按关键词搜索（用户名或邮箱）
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Users::getUsername, keyword).or().like(Users::getEmail, keyword));
        }

        // 按创建时间倒序
        wrapper.orderByDesc(Users::getCreatedAt);

        return usersMapper.selectPage(page, wrapper);
    }

    /**
     * 重置密码
     */
    @Override
    public boolean resetPassword(ResetPasswordRequest request) {
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new RuntimeException("邮箱不能为空");
        }
        if (request.getCode() == null || request.getCode().trim().isEmpty()) {
            throw new RuntimeException("验证码不能为空");
        }
        if (request.getNewPassword() == null || request.getNewPassword().trim().isEmpty()) {
            throw new RuntimeException("新密码不能为空");
        }

        String email = request.getEmail().trim();

        Users user = usersMapper.selectOne(new QueryWrapper<Users>().eq("email", email));
        if (user == null) {
            throw new RuntimeException("该邮箱尚未注册");
        }

        String redisKey = "reset:code:" + email;
        String redisCode = stringRedisTemplate.opsForValue().get(redisKey);

        if (redisCode == null) {
            throw new RuntimeException("验证码已过期，请重新获取");
        }

        if (!redisCode.equals(request.getCode().trim())) {
            throw new RuntimeException("验证码错误");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword().trim()));
        user.setUpdatedAt(LocalDateTime.now());

        int rows = usersMapper.updateById(user);
        if (rows <= 0) {
            throw new RuntimeException("密码重置失败");
        }

        stringRedisTemplate.delete(redisKey);
        return true;
    }
    @Override
    public Users getCurrentUserProfile(String email) {
        Users user = usersMapper.selectOne(new QueryWrapper<Users>().eq("email", email));
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setPassword(null);
        return user;
    }

    @Override
    public boolean updateCurrentUserProfile(String email, ProfileUpdateRequest request) {
        Users user = usersMapper.selectOne(new QueryWrapper<Users>().eq("email", email));
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new RuntimeException("用户名不能为空");
        }

        String phone = request.getPhone() == null ? "" : request.getPhone().trim();
        if (!phone.isEmpty() && !phone.matches("^1[3-9]\\d{9}$")) {
            throw new RuntimeException("手机号格式不正确");
        }

        user.setUsername(request.getUsername().trim());
        user.setPhone(phone);
        user.setAvatarUrl(request.getAvatarUrl());
        user.setUpdatedAt(LocalDateTime.now());

        return usersMapper.updateById(user) > 0;
    }

    @Override
    public boolean changeCurrentUserPassword(String email, ChangePasswordRequest request) {
        Users user = usersMapper.selectOne(new QueryWrapper<Users>().eq("email", email));
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (request.getOldPassword() == null || request.getOldPassword().trim().isEmpty()) {
            throw new RuntimeException("旧密码不能为空");
        }
        if (request.getNewPassword() == null || request.getNewPassword().trim().isEmpty()) {
            throw new RuntimeException("新密码不能为空");
        }
        if (request.getConfirmPassword() == null || request.getConfirmPassword().trim().isEmpty()) {
            throw new RuntimeException("确认密码不能为空");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("两次输入的新密码不一致");
        }
        if (request.getNewPassword().length() < 6 || request.getNewPassword().length() > 20) {
            throw new RuntimeException("新密码长度需在 6 到 20 位之间");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("旧密码不正确");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword().trim()));
        user.setUpdatedAt(LocalDateTime.now());
        return usersMapper.updateById(user) > 0;
    }
}
