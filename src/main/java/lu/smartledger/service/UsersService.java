package lu.smartledger.service;

import lu.smartledger.model.domain.Users;

public interface UsersService {
    /**
     * 根据邮箱查询用户
     */
    Users geByEmail(String email);
    /**
     * 用户注册
     */
    boolean register(Users user);
    /**
     * 用户登录
     */
    Users login(String email, String password);

}
