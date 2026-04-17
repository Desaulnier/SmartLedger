package lu.smartledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lu.smartledger.model.domain.Categories;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 消费分类表 Mapper 接口
 * </p>
 *
 * @author lu
 * @since 2026-04-11
 */
@Mapper
public interface CategoriesMapper extends BaseMapper<Categories> {

}
