package lu.smartledger.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lu.smartledger.model.domain.RegisterRequest;
import lu.smartledger.model.domain.Users;
import lu.smartledger.model.dto.ChangePasswordRequest;
import lu.smartledger.model.dto.ProfileUpdateRequest;
import lu.smartledger.model.dto.ResetPasswordRequest;

public interface UsersService {
    /**
     * 根据邮箱查询用户
     */
    Users geByEmail(String email);
    /**
     * 用户注册
     */
    boolean register(RegisterRequest request);
    /**
     * 用户登录
     */
    Users login(String email, String password);

    Page<Users> getAdminUserList(Integer pageNum, Integer pageSize, String status, String role, String keyword);
    boolean resetPassword(ResetPasswordRequest request);
    Users getCurrentUserProfile(String email);
    boolean updateCurrentUserProfile(String email, ProfileUpdateRequest request);
    boolean changeCurrentUserPassword(String email, ChangePasswordRequest request);
}
