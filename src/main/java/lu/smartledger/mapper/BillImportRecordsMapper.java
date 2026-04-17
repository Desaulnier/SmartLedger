package lu.smartledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lu.smartledger.model.domain.BillImportRecords;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 账单导入记录表 Mapper 接口
 * </p>
 *
 * @author lu
 * @since 2026-04-11
 */
@Mapper
public interface BillImportRecordsMapper extends BaseMapper<BillImportRecords> {

}
