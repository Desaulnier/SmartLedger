package lu.smartledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lu.smartledger.mapper.*;
import lu.smartledger.model.domain.Bills;
import lu.smartledger.model.domain.Users;
import lu.smartledger.service.AdminService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UsersMapper usersMapper;
    private final BillsMapper billsMapper;
    private final WarningRecordsMapper warningRecordsMapper;
    private final BillImportRecordsMapper billImportRecordsMapper;
    private final AccountsMapper accountsMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUserWithCleanup(Long userId) {
        Users targetUser = usersMapper.selectById(userId);
        if (targetUser == null) {
            throw new RuntimeException("用户不存在");
        }

        if ("ADMIN".equals(targetUser.getRole())) {
            throw new RuntimeException("不能删除管理员账号");
        }

        billsMapper.delete(
                new LambdaQueryWrapper<Bills>()
                        .eq(Bills::getUserId, userId)
        );

        warningRecordsMapper.delete(
                new LambdaQueryWrapper<lu.smartledger.model.domain.WarningRecords>()
                        .eq(lu.smartledger.model.domain.WarningRecords::getUserId, userId)
        );

        billImportRecordsMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<lu.smartledger.model.domain.BillImportRecords>()
                        .eq(lu.smartledger.model.domain.BillImportRecords::getUserId, userId)
        );

        accountsMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<lu.smartledger.model.domain.Accounts>()
                        .eq(lu.smartledger.model.domain.Accounts::getUserId, userId)
        );

        int rows = usersMapper.deleteById(userId);
        if (rows <= 0) {
            throw new RuntimeException("删除用户失败");
        }
    }
}
