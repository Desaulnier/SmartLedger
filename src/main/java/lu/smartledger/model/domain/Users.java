package lu.smartledger.model.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Users {

    private Long id;
    private String email;
    private String password;
    private String username;
    private String phone;

    private String role;   // USER / ADMIN
    private String status; // PENDING / ACTIVE / DISABLED / BANNED

    private Boolean isWarningEnabled;
    private BigDecimal monthlyLimit;
    private BigDecimal warningThreshold;

    private BigDecimal currentSpent;
    private String limitMonth;

    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Users(){}//调用接口时先创造这样一个对象方便输入数据




}
