package lu.smartledger.service.impl;

import lu.smartledger.model.domain.Categories;
import lu.smartledger.mapper.CategoriesMapper;
import lu.smartledger.service.ICategoriesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 消费分类表 服务实现类
 * </p>
 *
 * @author lu
 * @since 2026-04-11
 */
@Service
public class CategoriesServiceImpl extends ServiceImpl<CategoriesMapper, Categories> implements ICategoriesService {

}
