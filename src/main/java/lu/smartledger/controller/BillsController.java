package lu.smartledger.controller;

import lu.smartledger.common.utls.JsonResponse;
import lu.smartledger.service.IBillsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 账单表 前端控制器
 * </p>
 *
 * @author lu
 * @since 2026-04-14
 */
@RestController
@RequestMapping("/bills")
public class BillsController {
    @Autowired
    private IBillsService billsService;

    @PostMapping("/import")
    public JsonResponse importBills(@RequestParam("file") MultipartFile file) {
        if(file.isEmpty()) return JsonResponse.fail("文件为空");
        return billsService.parseBillFile(file);
    }

    @PostMapping("/confirm-import")//确认导入
    public JsonResponse confirmImport(@RequestBody Map<String, Object> params) {
        try {
            Long importRecordId = Long.valueOf(params.get("importRecordId").toString());
            List<Map<String, Object>> billList = (List<Map<String, Object>>) params.get("billList");
            billsService.confirmImport(importRecordId, billList);
            return JsonResponse.success("确认导入成功");
        } catch (Exception e) {
            return JsonResponse.fail("确认导入失败：" + e.getMessage());
        }
        }
    }
