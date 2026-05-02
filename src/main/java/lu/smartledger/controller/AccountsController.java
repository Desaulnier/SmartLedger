package lu.smartledger.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lu.smartledger.common.utls.JsonResponse;
import lu.smartledger.model.domain.Accounts;
import lu.smartledger.model.domain.Users;
import lu.smartledger.service.IAccountsService;
import lu.smartledger.mapper.UsersMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/accounts")
public class AccountsController {

    @Autowired
    private IAccountsService accountsService;

    @Autowired
    private UsersMapper usersMapper;

    private Long getCurrentUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Users user = usersMapper.selectOne(new QueryWrapper<Users>().eq("email", email));
        return user != null ? user.getId() : null;
    }

    @GetMapping("/list")
    public JsonResponse<List<Accounts>> getAccountList() {
        Long userId = getCurrentUserId();
        if (userId == null) return JsonResponse.fail("用户未登录");

        List<Accounts> list = accountsService.list(
                new QueryWrapper<Accounts>()
                        .eq("user_id", userId)
                        .eq("is_deleted", 0)
                        .orderByDesc("is_default")
                        .orderByAsc("create_time")
        );
        return JsonResponse.success(list);
    }

    @PostMapping
    public JsonResponse createAccount(@RequestBody Accounts account) {
        Long userId = getCurrentUserId();
        if (userId == null) return JsonResponse.fail("用户未登录");

        account.setUserId(userId);
        account.setBalance(java.math.BigDecimal.ZERO);
        account.setIsDeleted((byte) 0);
        account.setCreateTime(java.time.LocalDateTime.now());
        account.setUpdateTime(java.time.LocalDateTime.now());

        long count = accountsService.count(new QueryWrapper<Accounts>().eq("user_id", userId).eq("is_deleted", 0));
        if (count == 0) {
            account.setIsDefault((byte) 1);
        } else {
            account.setIsDefault((byte) 0);
        }

        accountsService.save(account);
        return JsonResponse.success("账户创建成功");
    }

    @PutMapping("/{id}")
    public JsonResponse updateAccount(@PathVariable Long id, @RequestBody Accounts account) {
        Long userId = getCurrentUserId();
        if (userId == null) return JsonResponse.fail("用户未登录");

        Accounts existAccount = accountsService.getById(id);
        if (existAccount == null || !existAccount.getUserId().equals(userId)) {
            return JsonResponse.fail("无权操作或账户不存在");
        }

        if (account.getAccountName() != null && !account.getAccountName().trim().isEmpty()) {
            existAccount.setAccountName(account.getAccountName().trim());
        }
        if (account.getAccountType() != null) {
            existAccount.setAccountType(account.getAccountType());
        }

        existAccount.setUpdateTime(java.time.LocalDateTime.now());
        accountsService.updateById(existAccount);

        return JsonResponse.success("账户更新成功");
    }

    @PutMapping("/{id}/set-default")
    public JsonResponse setDefaultAccount(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) return JsonResponse.fail("用户未登录");

        Accounts targetAccount = accountsService.getById(id);
        if (targetAccount == null || !targetAccount.getUserId().equals(userId)) {
            return JsonResponse.fail("账户不存在或无权操作");
        }

        accountsService.update(new UpdateWrapper<Accounts>()
                .eq("user_id", userId)
                .set("is_default", 0));

        targetAccount.setIsDefault((byte) 1);
        targetAccount.setUpdateTime(java.time.LocalDateTime.now());
        accountsService.updateById(targetAccount);

        return JsonResponse.success("默认账户设置成功");
    }

    @DeleteMapping("/{id}")
    public JsonResponse deleteAccount(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) return JsonResponse.fail("用户未登录");

        Accounts account = accountsService.getById(id);
        if (account == null || !account.getUserId().equals(userId)) {
            return JsonResponse.fail("无权操作");
        }

        if (account.getIsDefault() == 1) {
            return JsonResponse.fail("不能删除默认账户");
        }

        account.setIsDeleted((byte) 1);
        account.setUpdateTime(java.time.LocalDateTime.now());
        accountsService.updateById(account);
        return JsonResponse.success("账户已删除");
    }
}