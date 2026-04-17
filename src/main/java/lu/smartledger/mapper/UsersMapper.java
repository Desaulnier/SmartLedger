package lu.smartledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lu.smartledger.model.domain.Users;
import org.apache.ibatis.annotations.Mapper;

@Mapper//@Mapper是 MyBatis-Plus/MyBatis 框架的核心注解，作用是告诉 Spring 容器：这个接口是 “数据访问层（Mapper 层）” 的接口，框架会自动为它生成实现类
public interface UsersMapper extends BaseMapper<Users> {


}
