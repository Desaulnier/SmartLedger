package lu.smartledger.model.domain;

import lombok.Data;

/**
 * 注册请求参数
 */
@Data
public class RegisterRequest {
    private String email;
    private String password;
    private String confirmPassword;
    private String username;
    private String phone;
    private String code;
}
