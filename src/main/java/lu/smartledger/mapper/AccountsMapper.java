package lu.smartledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lu.smartledger.model.domain.Accounts;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 用户资金账户表 Mapper 接口
 * </p>
 *
 * @author lu
 * @since 2026-04-11
 */
@Mapper
public interface AccountsMapper extends BaseMapper<Accounts> {

}
