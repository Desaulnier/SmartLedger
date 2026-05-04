package lu.smartledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lu.smartledger.model.domain.Bills;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 账单表 Mapper 接口
 * </p>
 *
 * @author lu
 * @since 2026-04-24
 */
@Mapper
public interface BillsMapper extends BaseMapper<Bills> {

}
