package lu.smartledger.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lu.smartledger.common.utls.JsonResponse;
import lu.smartledger.mapper.BillsMapper;
import lu.smartledger.mapper.CategoryRulesMapper;
import lu.smartledger.model.domain.Bills;
import lu.smartledger.model.domain.Categories;
import lu.smartledger.model.domain.CategoryRules;
import lu.smartledger.service.ICategoriesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/categories")
public class CategoriesController {

    @Autowired
    private ICategoriesService categoriesService;

    @Autowired
    private BillsMapper billsMapper;

    @Autowired
    private CategoryRulesMapper categoryRulesMapper;

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

    @PostMapping("/add")
    public JsonResponse<Categories> addCategory(@RequestBody Categories category) {
        try {
            if (category == null) {
                return JsonResponse.fail("分类数据不能为空");
            }

            if (category.getName() == null || category.getName().trim().isEmpty()) {
                return JsonResponse.fail("分类名称不能为空");
            }

            if (category.getType() == null ||
                    (!"EXPENSE".equals(category.getType()) && !"INCOME".equals(category.getType()))) {
                return JsonResponse.fail("分类类型必须是EXPENSE或INCOME");
            }

            category.setName(category.getName().trim());
            category.setIsDefault(false);
            category.setCreatedAt(LocalDateTime.now());

            if ("INCOME".equals(category.getType())) {
                category.setDefaultType(null);
            } else if (category.getDefaultType() == null) {
                category.setDefaultType((byte) 1);
            }

            categoriesService.save(category);
            return JsonResponse.success("添加分类成功", category);
        } catch (Exception e) {
            return JsonResponse.fail("添加分类失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public JsonResponse<String> deleteCategory(@PathVariable Long id) {
        try {
            if (id == null) {
                return JsonResponse.fail("分类ID不能为空");
            }

            Categories category = categoriesService.getById(id);
            if (category == null) {
                return JsonResponse.fail("分类不存在");
            }

            if (Boolean.TRUE.equals(category.getIsDefault())) {
                return JsonResponse.fail("系统默认分类不可删除");
            }

            Long billCount = billsMapper.selectCount(
                    new QueryWrapper<Bills>().eq("category_id", id)
            );
            if (billCount != null && billCount > 0) {
                return JsonResponse.fail("该分类已被账单使用，无法删除");
            }

            Long ruleCount = categoryRulesMapper.selectCount(
                    new QueryWrapper<CategoryRules>().eq("category_id", id)
            );
            if (ruleCount != null && ruleCount > 0) {
                return JsonResponse.fail("该分类已被自动分类规则使用，无法删除");
            }

            boolean removed = categoriesService.removeById(id);
            if (!removed) {
                return JsonResponse.fail("删除分类失败");
            }

            return JsonResponse.success("删除分类成功");
        } catch (Exception e) {
            return JsonResponse.fail("删除分类失败：" + e.getMessage());
        }
    }
}
