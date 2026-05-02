package lu.smartledger.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lu.smartledger.common.utls.JsonResponse;
import lu.smartledger.model.domain.Categories;
import lu.smartledger.service.ICategoriesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
public class CategoriesController {

    @Autowired
    private ICategoriesService categoriesService;

    @GetMapping("/list")
    public JsonResponse<List<Categories>> getCategoryList() {
        try {
            List<Categories> categories = categoriesService.list(
                    new QueryWrapper<Categories>()
                            .orderByAsc("id")
            );
            return JsonResponse.success(categories);
        } catch (Exception e) {
            return JsonResponse.fail("获取分类列表失败：" + e.getMessage());
        }
    }
}