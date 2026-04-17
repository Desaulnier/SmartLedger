package lu.smartledger.service.impl;

import lu.smartledger.model.domain.Accounts;
import lu.smartledger.mapper.AccountsMapper;
import lu.smartledger.service.IAccountsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 用户资金账户表 服务实现类
 * </p>
 *
 * @author lu
 * @since 2026-04-11
 */
@Service
public class AccountsServiceImpl extends ServiceImpl<AccountsMapper, Accounts> implements IAccountsService {

}
