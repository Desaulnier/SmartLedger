package lu.smartledger.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import lu.smartledger.common.utls.JsonResponse;
import lu.smartledger.model.domain.BillImportRecords;
import lu.smartledger.model.domain.Bills;
import lu.smartledger.model.domain.CategoryRules;
import lu.smartledger.model.domain.WechatBill;
import lu.smartledger.service.IBillImportRecordsService;
import lu.smartledger.service.IBillsService;
import lu.smartledger.service.ICategoryRulesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lu.smartledger.mapper.BillsMapper;
import lu.smartledger.mapper.UsersMapper;
import lu.smartledger.model.domain.Users;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BillsServiceImpl extends ServiceImpl<BillsMapper, Bills> implements IBillsService {

    private final ICategoryRulesService categoryRulesService;
    private final IBillImportRecordsService importRecordsService;
    private final UsersMapper usersMapper;

    /**
     * 解析账单文件，返回预览数据
     */
    @Override
    @Transactional
    public JsonResponse<Map<String, Object>> parseBillFile(MultipartFile file) {
        try {
            List<WechatBill> wechatBills = new ArrayList<>();

            EasyExcel.read(file.getInputStream(), WechatBill.class, new AnalysisEventListener<WechatBill>() {
                @Override
                public void invoke(WechatBill data, AnalysisContext context) {
                    wechatBills.add(data);
                }
                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {}
            }).sheet().headRowNumber(9).doReadSync();

            log.info("读取行数：{}", wechatBills.size());

            if (wechatBills.isEmpty()) {
                return JsonResponse.fail("文件内无数据");
            }

            List<Bills> billsList = new ArrayList<>();
            for (WechatBill wechatBill : wechatBills) {
                Bills bill = wechatBill.toBills();
                if (bill != null) {
                    billsList.add(bill);
                }
            }

            autoClassify(billsList);

            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            Users user = usersMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Users>()
                            .eq(Users::getEmail, email)
            );
            Long userId = user != null ? user.getId() : 1L;

            BillImportRecords record = new BillImportRecords();
            record.setUserId(userId)
                    .setFileName(file.getOriginalFilename())
                    .setFileType("excel")
                    .setTotalCount(billsList.size())
                    .setSuccessCount(billsList.size())
                    .setImportTime(LocalDateTime.now());
            importRecordsService.save(record);

            Map<String, Object> data = new HashMap<>();
            data.put("billList", billsList);
            data.put("importRecordId", record.getId());

            return JsonResponse.success("解析成功", data);

        } catch (Exception e) {
            log.error("解析异常", e);
            return JsonResponse.fail("解析失败：" + e.getMessage());
        }
    }

    /**
     * 确认导入入库
     */
    @Override
    public void confirmImport(Long importRecordId, List<Map<String, Object>> billList) {
        List<Bills> bills = new ArrayList<>();

        for (Map<String, Object> map : billList) {
            Bills bill = new Bills();
            bill.setAmount(new BigDecimal(map.get("amount").toString()));
            bill.setBillType((String) map.get("billType"));
            bill.setCategoryId(Long.valueOf(map.get("categoryId").toString()));
            bill.setRemark((String) map.get("remark"));
            bill.setBillDate(new Date());
            bill.setSource("IMPORT");
            bill.setImportRecordId(importRecordId);
            bill.setCreatedAt(LocalDateTime.now());
            bill.setUpdatedAt(LocalDateTime.now());
            bills.add(bill);
        }

        saveBatch(bills);

        BillImportRecords record = importRecordsService.getById(importRecordId);
        record.setSuccessCount(billList.size());
        importRecordsService.updateById(record);
    }

    /**
     * 自动分类（修复拼写错误）
     */
    private void autoClassify(List<Bills> billsList) {
        List<CategoryRules> rules = categoryRulesService.lambdaQuery()
                .eq(CategoryRules::getIsEnabled, true)
                .list();

        for (Bills bill : billsList) {
            String remark = bill.getRemark() == null ? "" : bill.getRemark();
            String cleanedRemark = cleanRemark(remark);

            for (CategoryRules rule : rules) {
                String keyword = rule.getKeyword().toLowerCase();
                if (cleanedRemark.toLowerCase().contains(keyword)) {
                    bill.setCategoryId(rule.getCategoryId());
                    break;
                }
            }
        }
    }

    private String cleanRemark(String remark) {
        if (remark == null || remark.isEmpty()) {
            return "";
        }
        
        String cleaned = remark;
        
        cleaned = cleaned.replaceAll("【.*?】", "");
        cleaned = cleaned.replaceAll("_.*", "");
        cleaned = cleaned.replaceAll("收款方备注:", "");
        cleaned = cleaned.replaceAll("转账备注:", "");
        cleaned = cleaned.replaceAll("商户单号.*", "");
        cleaned = cleaned.replaceAll("/$", "");
        cleaned = cleaned.replaceAll("支付$", "");
        cleaned = cleaned.replaceAll("-支付$", "");
        
        cleaned = cleaned.replaceAll("\\s+", "");
        
        return cleaned.trim();
    }

}