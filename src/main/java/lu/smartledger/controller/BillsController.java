package lu.smartledger.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lu.smartledger.common.utls.JsonResponse;
import lu.smartledger.mapper.UsersMapper;
import lu.smartledger.model.domain.Bills;
import lu.smartledger.model.domain.Users;
import lu.smartledger.service.IBillsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/bills")
public class BillsController {
    @Autowired
    private IBillsService billsService;
    @Autowired
    private UsersMapper usersMapper;

    @PostMapping("/import")
    public JsonResponse importBills(@RequestParam("file") MultipartFile file) {
        if(file.isEmpty()) return JsonResponse.fail("文件为空");
        return billsService.parseBillFile(file);
    }

    @PostMapping("/confirm-import")
    public JsonResponse confirmImport(@RequestBody Map<String, Object> params) {
        try {
            Long userId = getCurrentUserId();
            if(userId == null) return JsonResponse.fail("用户不存在");

            Object importRecordIdObj = params.get("importRecordId");
            if (importRecordIdObj == null) {
                return JsonResponse.fail("导入记录ID不能为空");
            }
            Long importRecordId;
            try {
                importRecordId = Long.valueOf(importRecordIdObj.toString());
            } catch (Exception e) {
                return JsonResponse.fail("导入记录ID格式错误");
            }

            Object billListObj = params.get("billList");
            if (billListObj == null || !(billListObj instanceof List)) {
                return JsonResponse.fail("账单列表不能为空");
            }
            List<Map<String, Object>> billList = (List<Map<String, Object>>) billListObj;
            if (billList.isEmpty()) {
                return JsonResponse.fail("账单列表为空，无需导入");
            }

            billsService.confirmImport(importRecordId, billList, userId);
            return JsonResponse.success("确认导入成功");
        } catch (Exception e) {
            return JsonResponse.fail("确认导入失败：" + e.getMessage());
        }
    }

    @PostMapping("/AddBills")
    public JsonResponse AddBills(@RequestBody Bills bills){
        try {
            Long userId = getCurrentUserId();
            if(userId == null) return JsonResponse.fail("用户未登录");

            if (bills.getAmount() == null || bills.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                return JsonResponse.fail("账单金额必须大于0");
            }
            if (bills.getBillType() == null || !("EXPENSE".equals(bills.getBillType()) || "INCOME".equals(bills.getBillType()))) {
                return JsonResponse.fail("账单类型必须是支出（EXPENSE）或收入（INCOME）");
            }
            billsService.saveBills(bills, userId);
            return JsonResponse.success("添加账单成功");

        }catch (Exception e){
            return JsonResponse.fail("添加账单失败：" + e.getMessage());
        }
    }
    /**
     * 编辑账单
     */
    @PutMapping("/{id}")
    public JsonResponse updateBill(@PathVariable Long id, @RequestBody Bills bills) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return JsonResponse.fail("用户未登录");
            }

            bills.setId(id);
            billsService.updateBill(bills, userId);
            return JsonResponse.success("编辑成功");
        } catch (Exception e) {
            return JsonResponse.fail("编辑失败：" + e.getMessage());
        }
    }


    @DeleteMapping("/{id}")
    public JsonResponse deleteBill(@PathVariable Long id){
        Long userId = getCurrentUserId();
        if(userId == null) return JsonResponse.fail("用户未登录");
        try{
            billsService.deleteBills(id, userId);
            return JsonResponse.success("删除成功");
        }catch (Exception e){
            return JsonResponse.fail("删除失败："+e.getMessage());
        }
    }

    /**
     * 获取账单列表
     */
    @GetMapping("/list")
    public JsonResponse getBillList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String keyword) {

        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return JsonResponse.fail("用户未登录");
            }

            Page<Bills> result = billsService.getUserBillList(userId, pageNum, pageSize, type, category, startDate, endDate, keyword);
            return JsonResponse.success(result);
        } catch (Exception e) {
            return JsonResponse.fail("获取账单列表失败：" + e.getMessage());
        }
    }

    private Long getCurrentUserId() {
        try {
            org.springframework.security.core.Authentication authentication =
                    SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated() ||
                    "anonymousUser".equals(authentication.getName())) {
                return null;
            }

            String email = authentication.getName();
            Users user = usersMapper.selectOne(
                    new QueryWrapper<Users>()
                            .eq("email", email)
            );
            return user != null ? user.getId() : null;
        } catch (Exception e) {
            return null;
        }
    }
    /**
     * 获取异常账单列表
     */
    @GetMapping("/abnormal")
    public JsonResponse getAbnormalBillList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "6") Integer pageSize) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return JsonResponse.fail("用户未登录");
            }

            Page<Bills> result = billsService.getAbnormalBillList(userId, pageNum, pageSize);
            return JsonResponse.success(result);
        } catch (Exception e) {
            return JsonResponse.fail("获取异常账单失败：" + e.getMessage());
        }
    }

}