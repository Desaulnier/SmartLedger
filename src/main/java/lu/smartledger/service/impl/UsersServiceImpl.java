package lu.smartledger.service.impl;

import ch.qos.logback.core.util.StringUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.Resource;
import lu.smartledger.mapper.UsersMapper;
import lu.smartledger.model.domain.Users;
import lu.smartledger.service.UsersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UsersServiceImpl implements UsersService {
    @Autowired
    private UsersMapper usersMapper;//依赖注入：把UsersMapper（数据库操作层）注入进来，才能操作数据库


    @Override
    public Users geByEmail(String email) {//查询用户
        return usersMapper.selectOne(new QueryWrapper<Users>().eq("email", email));
    }

    @Override
    public boolean register(Users user) {//注册用户
        return usersMapper.insert(user) > 0;
    }

    @Override
    public Users login(String email, String password) {
        return usersMapper.selectOne(new QueryWrapper<Users>()
                .eq("email", email)
                .eq("password", password));
    }
}
