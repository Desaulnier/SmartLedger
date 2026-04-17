package lu.smartledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lu.smartledger.model.domain.Bills;
import org.apache.ibatis.annotations.Mapper;

@Mapper//BaseMapper 为你的 BillsMapper 自动提供了方法
public interface BillsMapper extends BaseMapper<Bills> {

}
