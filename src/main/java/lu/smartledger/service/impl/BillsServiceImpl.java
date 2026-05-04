package lu.smartledger.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lu.smartledger.common.utls.JsonResponse;
import lu.smartledger.mapper.BillsMapper;
import lu.smartledger.model.domain.*;
import lu.smartledger.model.dto.MonthlyAnalysisDTO;
import lu.smartledger.service.AnomalyDetectionService;
import lu.smartledger.service.IAccountsService;
import lu.smartledger.service.IBillImportRecordsService;
import lu.smartledger.service.IBillsService;
import lu.smartledger.service.ICategoryRulesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lu.smartledger.mapper.UsersMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BillsServiceImpl extends ServiceImpl<BillsMapper, Bills> implements IBillsService {

    private final ICategoryRulesService categoryRulesService;
    private final IBillImportRecordsService importRecordsService;
    private final UsersMapper usersMapper;
    private final IAccountsService accountsService;
    private final WarningRecordsServiceImpl warningRecordsService;
    private final AnomalyDetectionServiceImpl anomalyDetectionService;
    private final lu.smartledger.mapper.CategoriesMapper categoriesMapper;

    /**
     * 解析账单文件，返回预览数据
     */
    @Override
    @Transactional
    public JsonResponse<Map<String, Object>> parseBillFile(MultipartFile file) {
        try {
            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.isBlank()) {
                return JsonResponse.fail("文件名不能为空");
            }

            String lowerName = fileName.toLowerCase(Locale.ROOT);
            List<Bills> billsList;
            String fileType;

            if (lowerName.endsWith(".csv")) {
                billsList = parseAlipayCsv(file);
                fileType = "csv";
            } else if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) {
                billsList = parseWechatExcel(file);
                fileType = "excel";
            } else {
                return JsonResponse.fail("暂不支持该文件格式，仅支持 CSV/Excel");
            }

            if (billsList.isEmpty()) {
                return JsonResponse.fail("文件中没有可导入的有效账单");
            }

            autoClassify(billsList);

            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            Users user = usersMapper.selectOne(
                    new LambdaQueryWrapper<Users>().eq(Users::getEmail, email)
            );
            if (user == null) {
                return JsonResponse.fail("当前登录用户不存在，无法导入账单");
            }

            BillImportRecords record = new BillImportRecords();
            record.setUserId(user.getId())
                    .setFileName(fileName)
                    .setFileType(fileType)
                    .setTotalCount(billsList.size())
                    .setSuccessCount(0)
                    .setImportTime(LocalDateTime.now());

            importRecordsService.save(record);

            Map<String, Object> data = new HashMap<>();
            data.put("billList", billsList);
            data.put("importRecordId", record.getId());

            return JsonResponse.success("解析成功，共 " + billsList.size() + " 条", data);
        } catch (Exception e) {
            return JsonResponse.fail("解析失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmImport(Long importRecordId, List<Map<String, Object>> billList, Long userId) {
        if (importRecordId == null) {
            throw new RuntimeException("导入记录ID不能为空");
        }
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        if (billList == null || billList.isEmpty()) {
            throw new RuntimeException("没有可导入的账单数据");
        }

        BillImportRecords record = importRecordsService.getById(importRecordId);
        if (record == null) {
            throw new RuntimeException("导入记录不存在，请重新解析文件");
        }
        if (!userId.equals(record.getUserId())) {
            throw new RuntimeException("无权操作该导入记录");
        }

        List<Bills> bills = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        Accounts defaultAccount = getOrCreateDefaultAccount(userId);
        Long defaultAccountId = defaultAccount.getId();

        BigDecimal totalExpense = BigDecimal.ZERO;
        List<Bills> historyBills = this.list(
                new LambdaQueryWrapper<Bills>()
                        .eq(Bills::getUserId, userId)
                        .eq(Bills::getBillType, "EXPENSE")
                        .orderByDesc(Bills::getOccurTime)
                        .last("limit 200")
        );
        for (Map<String, Object> map : billList) {
            if (map == null || map.isEmpty()) {
                continue;
            }

            Bills bill = new Bills();

            Object amountObj = map.get("amount");
            BigDecimal amount = BigDecimal.ZERO;
            if (amountObj != null) {
                if (amountObj instanceof BigDecimal) {
                    amount = (BigDecimal) amountObj;
                } else {
                    try {
                        amount = new BigDecimal(amountObj.toString());
                    } catch (Exception e) {
                        continue;
                    }
                }
            }

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            bill.setAmount(amount);

            Object billTypeObj = map.get("billType");
            String billType = "EXPENSE";
            if (billTypeObj != null && "INCOME".equals(billTypeObj.toString())) {
                billType = "INCOME";
            }
            bill.setBillType(billType);

            Object categoryIdObj = map.get("categoryId");
            Long categoryId = null;
            if (categoryIdObj != null) {
                try {
                    categoryId = Long.valueOf(categoryIdObj.toString());
                } catch (Exception e) {
                    continue;
                }
            }
            if (categoryId == null) {
                continue;
            }
            bill.setCategoryId(categoryId);

            Object remarkObj = map.get("remark");
            String remark = remarkObj != null ? remarkObj.toString() : "";
            bill.setRemark(remark);

            bill.setOccurTime(parseOccurTime(map, now));
            bill.setUserId(userId);
            bill.setAccountId(defaultAccountId);
            bill.setSource("IMPORT");
            bill.setImportRecordId(importRecordId);
            bill.setCreatedAt(now);
            bill.setUpdatedAt(now);
            if ("EXPENSE".equals(billType)) {
                anomalyDetectionService.detectAnomaly(bill, historyBills);
                historyBills.add(bill);
            }
            bills.add(bill);

            if ("EXPENSE".equals(billType)) {
                totalExpense = totalExpense.add(amount);
            }
        }

        if (bills.isEmpty()) {
            throw new RuntimeException("没有符合要求的账单可导入");
        }

        saveBatch(bills);

        record.setSuccessCount(bills.size());
        importRecordsService.updateById(record);

        if (totalExpense.compareTo(BigDecimal.ZERO) > 0) {
            usersMapper.update(
                    null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Users>()
                            .eq(Users::getId, userId)
                            .setSql("current_spent = current_spent + {0}", totalExpense)
            );

            warningRecordsService.createBudgetWarningRecord(userId);
            
            for (Bills bill : bills) {
                if ("EXPENSE".equals(bill.getBillType())) {
                    warningRecordsService.createAnomalyWarningRecord(userId, bill);
                }
            }
        }
    }



    // 时间解析工具（全量判断null）
    private LocalDateTime parseOccurTime(Map<String, Object> map, LocalDateTime defaultTime) {
        // 先试occurTime
        Object occurTimeObj = map.get("occurTime");
        if (occurTimeObj != null && !occurTimeObj.toString().isBlank()) {
            try {
                return LocalDateTime.parse(occurTimeObj.toString());
            } catch (Exception e) {
                // 格式错误，继续试billDate
            }
        }

        // 再试billDate
        Object billDateObj = map.get("billDate");
        if (billDateObj != null && !billDateObj.toString().isBlank()) {
            try {
                return LocalDateTime.parse(billDateObj.toString() + "T00:00:00");
            } catch (Exception e) {
                // 格式错误，用默认时间
            }
        }

        return defaultTime;
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBills(Bills bill, Long userId) {
        if (bill == null) {
            throw new RuntimeException("账单数据不能为空");
        }

        if (bill.getAmount() == null || bill.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("账单金额必须大于0");
        }

        if (bill.getBillType() == null || (!"EXPENSE".equals(bill.getBillType()) && !"INCOME".equals(bill.getBillType()))) {
            throw new RuntimeException("账单类型必须是EXPENSE或INCOME");
        }

        bill.setUserId(userId);
        bill.setSource("MANUAL");
        bill.setCreatedAt(LocalDateTime.now());
        bill.setUpdatedAt(LocalDateTime.now());

        Accounts defaultAccount = getOrCreateDefaultAccount(userId);

        bill.setAccountId(defaultAccount.getId());
        if ("EXPENSE".equals(bill.getBillType())) {
            List<Bills> historyBills = this.list(
                    new LambdaQueryWrapper<Bills>()
                            .eq(Bills::getUserId, userId)
                            .eq(Bills::getBillType, "EXPENSE")
                            .orderByDesc(Bills::getOccurTime)
                            .last("limit 200")
            );

            anomalyDetectionService.detectAnomaly(bill, historyBills);
        }
        // 判断账单是否异常
        if (bill.getAnomalyType() != null || (bill.getAnomalyScore() != null && bill.getAnomalyScore().doubleValue() >= 0.68)) {
            bill.setIsAbnormal(true);
        } else {
            bill.setIsAbnormal(false);
        }

        this.save(bill);

        if ("EXPENSE".equals(bill.getBillType()) && bill.getAmount() != null && bill.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            usersMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Users>()
                    .eq(Users::getId, userId)
                    .setSql("current_spent = current_spent + {0}", bill.getAmount()));

            warningRecordsService.createBudgetWarningRecord(userId);
            warningRecordsService.createAnomalyWarningRecord(userId, bill);
        }
    }


    @Override
    public Page<Bills> getUserBillList(Long userId, Integer pageNum, Integer pageSize, String type, String category, String startDate, String endDate, String keyword) {
        // 防止分页参数为null
        pageNum = pageNum == null ? 1 : pageNum;
        pageSize = pageSize == null ? 10 : pageSize;

        Page<Bills> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Bills> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(Bills::getUserId, userId);

        if (type != null && !type.isEmpty()) {
            wrapper.eq(Bills::getBillType, type);
        }

        // 处理分类参数（先判断是否为null）
        if (category != null && !category.isEmpty()) {
            try {
                wrapper.eq(Bills::getCategoryId, Long.parseLong(category));
            } catch (Exception e) {
                // 分类格式错误，不添加该条件
            }
        }

        if (startDate != null && !startDate.isEmpty()) {
            try {
                LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
                wrapper.ge(Bills::getOccurTime, start);
            } catch (Exception e) {
                // 日期格式错误，不添加该条件
            }
        }

        if (endDate != null && !endDate.isEmpty()) {
            try {
                LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");
                wrapper.le(Bills::getOccurTime, end);
            } catch (Exception e) {
                // 日期格式错误，不添加该条件
            }
        }

        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Bills::getRemark, keyword));
        }

        wrapper.orderByDesc(Bills::getOccurTime);

        return this.page(page, wrapper);
    }

    @Override
    public Map<String, Object> getMonthlyStatistics(Long userId, String month) {
        if (userId == null || month == null || month.isEmpty()) {
            return new HashMap<>();
        }

        LocalDate firstDay;
        try {
            firstDay = LocalDate.parse(month + "-01");
        } catch (Exception e) {
            return new HashMap<>();
        }
        LocalDate lastDay = firstDay.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());

        List<Bills> bills = this.list(
                new LambdaQueryWrapper<Bills>()
                        .eq(Bills::getUserId, userId)
                        .ge(Bills::getOccurTime, firstDay.atStartOfDay())
                        .le(Bills::getOccurTime, lastDay.atTime(23, 59, 59))
        );

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        Map<String, BigDecimal> categoryStats = new HashMap<>();
        Map<Long, String> categoryIdToName = new HashMap<>();
        int expenseCount = 0;
        int incomeCount = 0;

        for (Bills bill : bills) {
            if (bill.getAmount() == null) {
                continue;
            }

            if ("INCOME".equals(bill.getBillType())) {
                totalIncome = totalIncome.add(bill.getAmount());
                incomeCount++;
            } else if ("EXPENSE".equals(bill.getBillType())) {
                totalExpense = totalExpense.add(bill.getAmount());
                expenseCount++;

                String categoryName = getCategoryName(bill.getCategoryId(), categoryIdToName);
                if (categoryName != null) {
                    categoryStats.merge(categoryName, bill.getAmount(), BigDecimal::add);
                }
            }
        }

        BigDecimal balance = totalIncome.subtract(totalExpense);
        BigDecimal avgDailyExpense = totalExpense.divide(
                BigDecimal.valueOf(lastDay.getDayOfMonth()), 2, RoundingMode.HALF_UP
        );

        Map<String, Object> result = new HashMap<>();
        result.put("totalIncome", totalIncome);
        result.put("totalExpense", totalExpense);
        result.put("balance", balance);
        result.put("categoryStats", categoryStats);
        result.put("expenseCount", expenseCount);
        result.put("incomeCount", incomeCount);
        result.put("avgDailyExpense", avgDailyExpense);
        result.put("billCount", bills.size());

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBills(Long billId, Long userId) {
        if (billId == null) {
            throw new RuntimeException("账单ID不能为空");
        }
        Bills bill = this.getById(billId);
        if (bill == null) {
            throw new RuntimeException("账单不存在");
        }
        if (!bill.getUserId().equals(userId)) {
            throw new RuntimeException("无权限删除他人账单");
        }

        if ("EXPENSE".equals(bill.getBillType()) && bill.getAmount() != null && bill.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            usersMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Users>()
                    .eq(Users::getId, userId)
                    .setSql("current_spent = GREATEST(current_spent - {0}, 0)", bill.getAmount()));
        }

        this.removeById(billId);

        warningRecordsService.createBudgetWarningRecord(userId);
    }

    /**
     * 更新账单
     *
     * @param bill
     * @param userId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBill(Bills bill, Long userId) {
        if (bill == null || bill.getId() == null) {
            throw new RuntimeException("账单ID不能为空");
        }
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        if (bill.getAmount() == null || bill.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("账单金额必须大于0");
        }
        if (bill.getBillType() == null || (!"EXPENSE".equals(bill.getBillType()) && !"INCOME".equals(bill.getBillType()))) {
            throw new RuntimeException("账单类型必须是EXPENSE或INCOME");
        }
        if (bill.getCategoryId() == null) {
            throw new RuntimeException("分类不能为空");
        }

        Bills oldBill = this.getById(bill.getId());
        if (oldBill == null) {
            throw new RuntimeException("账单不存在");
        }
        if (!userId.equals(oldBill.getUserId())) {
            throw new RuntimeException("无权编辑他人账单");
        }

        BigDecimal oldExpense = "EXPENSE".equals(oldBill.getBillType()) ? oldBill.getAmount() : BigDecimal.ZERO;
        BigDecimal newExpense = "EXPENSE".equals(bill.getBillType()) ? bill.getAmount() : BigDecimal.ZERO;
        BigDecimal diff = newExpense.subtract(oldExpense);

        oldBill.setAmount(bill.getAmount());
        oldBill.setBillType(bill.getBillType());
        oldBill.setCategoryId(bill.getCategoryId());
        oldBill.setOccurTime(bill.getOccurTime());
        oldBill.setRemark(bill.getRemark());
        oldBill.setConsumptionAttribute(bill.getConsumptionAttribute());
        oldBill.setUpdatedAt(LocalDateTime.now());

        if ("EXPENSE".equals(bill.getBillType())) {
            List<Bills> historyBills = this.list(
                    new LambdaQueryWrapper<Bills>()
                            .eq(Bills::getUserId, userId)
                            .eq(Bills::getBillType, "EXPENSE")
                            .orderByDesc(Bills::getOccurTime)
                            .last("limit 200")
            );

            anomalyDetectionService.detectAnomaly(oldBill, historyBills);
        }

        this.updateById(oldBill);

        if (diff.compareTo(BigDecimal.ZERO) != 0) {
            usersMapper.update(
                    null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Users>()
                            .eq(Users::getId, userId)
                            .setSql("current_spent = GREATEST(current_spent + {0}, 0)", diff)
            );

            warningRecordsService.createBudgetWarningRecord(userId);
            
            if ("EXPENSE".equals(bill.getBillType())) {
                warningRecordsService.createAnomalyWarningRecord(userId, oldBill);
            }
        }
    }

    @Override
    public MonthlyAnalysisDTO getMonthlyAnalysis(Long userId, String monthStr) {
        if (userId == null || monthStr == null || monthStr.isEmpty()) {
            return new MonthlyAnalysisDTO();
        }

        LocalDate firstDay;
        try {
            firstDay = LocalDate.parse(monthStr + "-01");
        } catch (Exception e) {
            return new MonthlyAnalysisDTO();
        }
        LocalDate lastDay = firstDay.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());

        List<Bills> bills = this.list(
                new LambdaQueryWrapper<Bills>()
                        .eq(Bills::getUserId, userId)
                        .ge(Bills::getOccurTime, firstDay.atStartOfDay())
                        .le(Bills::getOccurTime, lastDay.atTime(23, 59, 59))
        );

        MonthlyAnalysisDTO dto = new MonthlyAnalysisDTO();
        BigDecimal necessaryExpense = BigDecimal.ZERO;
        BigDecimal improveExpense = BigDecimal.ZERO;
        BigDecimal desireExpense = BigDecimal.ZERO;

        for (Bills bill : bills) {
            if (bill.getAmount() == null) {
                continue;
            }

            if ("INCOME".equals(bill.getBillType())) {
                dto.setTotalIncome(dto.getTotalIncome().add(bill.getAmount()));
            } else if ("EXPENSE".equals(bill.getBillType())) {
                dto.setTotalExpense(dto.getTotalExpense().add(bill.getAmount()));

                Byte attribute = bill.getConsumptionAttribute();
                if (attribute != null) {
                    switch (attribute) {
                        case 1:
                            necessaryExpense = necessaryExpense.add(bill.getAmount());
                            break;
                        case 2:
                            improveExpense = improveExpense.add(bill.getAmount());
                            break;
                        case 3:
                            desireExpense = desireExpense.add(bill.getAmount());
                            break;
                    }
                }
            }
        }

        dto.setNecessaryExpense(necessaryExpense);
        dto.setImproveExpense(improveExpense);
        dto.setDesireExpense(desireExpense);
        dto.setBalance(dto.getTotalIncome().subtract(dto.getTotalExpense()));

        return dto;
    }

    @Override
    public List<String> getAnalysisSuggestions(Long userId, String monthStr) {
        List<String> suggestions = new ArrayList<>();

        if (userId == null || monthStr == null || monthStr.isEmpty()) {
            return suggestions;
        }

        MonthlyAnalysisDTO analysis = getMonthlyAnalysis(userId, monthStr);
        if (analysis == null) {
            return suggestions;
        }

        BigDecimal totalExpense = analysis.getTotalExpense();
        BigDecimal necessaryExpense = analysis.getNecessaryExpense();
        BigDecimal improveExpense = analysis.getImproveExpense();
        BigDecimal desireExpense = analysis.getDesireExpense();
        BigDecimal balance = analysis.getBalance();

        if (totalExpense.compareTo(BigDecimal.ZERO) == 0) {
            suggestions.add("本月暂无支出记录，建议开始记账以了解消费情况");
            return suggestions;
        }

        BigDecimal desireRatio = desireExpense.divide(totalExpense, 4, RoundingMode.HALF_UP);
        BigDecimal necessaryRatio = necessaryExpense.divide(totalExpense, 4, RoundingMode.HALF_UP);

        if (desireRatio.compareTo(BigDecimal.valueOf(0.3)) > 0) {
            suggestions.add("欲望型消费占比超过30%，建议减少非必要支出，优先保证必要消费");
        }

        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            suggestions.add("本月已超支，建议控制后续消费，避免进一步扩大赤字");
        } else if (balance.compareTo(totalExpense.multiply(BigDecimal.valueOf(0.2))) > 0) {
            suggestions.add("财务状况良好，结余充足，可以考虑增加储蓄或投资");
        }

        if (necessaryRatio.compareTo(BigDecimal.valueOf(0.6)) < 0) {
            suggestions.add("必要支出占比较低，注意区分必要和非必要消费，避免冲动消费");
        }

        if (improveExpense.compareTo(desireExpense) < 0) {
            suggestions.add("改善型消费少于欲望型消费，建议优化消费结构，提升生活质量");
        }

        if (suggestions.isEmpty()) {
            suggestions.add("本月消费结构合理，继续保持理性消费习惯");
        }

        return suggestions;
    }

    @Override
    public Page<Bills> getAbnormalBillList(Long userId, Integer pageNum, Integer pageSize) {
        pageNum = pageNum == null ? 1 : pageNum;
        pageSize = pageSize == null ? 6 : pageSize;

        Page<Bills> page = new Page<>(pageNum, pageSize);

        LocalDate now = LocalDate.now();
        LocalDateTime monthStart = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = now.withDayOfMonth(now.lengthOfMonth()).atTime(23, 59, 59);

        LambdaQueryWrapper<Bills> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Bills::getUserId, userId)
                .eq(Bills::getBillType, "EXPENSE")
                .ge(Bills::getOccurTime, monthStart)
                .le(Bills::getOccurTime, monthEnd)
                .and(w -> w
                        .eq(Bills::getIsAbnormal, true)
                        .or()
                        .isNotNull(Bills::getAnomalyType)
                        .or()
                        .ge(Bills::getAnomalyScore, BigDecimal.valueOf(0.68))
                )
                .orderByDesc(Bills::getOccurTime);

        return this.page(page, wrapper);
    }

    private String getCategoryName(Long categoryId, Map<Long, String> cache) {
        if (categoryId == null) {
            return "未分类";
        }

        if (cache.containsKey(categoryId)) {
            return cache.get(categoryId);
        }

        Categories category = categoriesMapper.selectById(categoryId);
        String name = category != null ? category.getName() : "未分类";
        cache.put(categoryId, name);
        return name;
    }

    private Accounts getOrCreateDefaultAccount(Long userId) {
        Accounts defaultAccount = accountsService.getOne(
                new QueryWrapper<Accounts>()
                        .eq("user_id", userId)
                        .eq("is_default", 1)
                        .eq("is_deleted", 0)
        );

        if (defaultAccount == null) {
            defaultAccount = new Accounts();
            defaultAccount.setUserId(userId);
            defaultAccount.setAccountName("默认账本");
            defaultAccount.setAccountType((byte) 5);
            defaultAccount.setBalance(BigDecimal.ZERO);
            defaultAccount.setIsDefault((byte) 1);
            defaultAccount.setIsDeleted((byte) 0);
            defaultAccount.setCreateTime(LocalDateTime.now());
            defaultAccount.setUpdateTime(LocalDateTime.now());
            accountsService.save(defaultAccount);
        }

        return defaultAccount;
    }

    // 自动分类（不变）
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

    // 微信解析（不变）
    private List<Bills> parseWechatExcel(MultipartFile file) throws Exception {
        List<WechatBill> wechatBills = new ArrayList<>();

        EasyExcel.read(file.getInputStream(), WechatBill.class, new AnalysisEventListener<WechatBill>() {
            @Override
            public void invoke(WechatBill data, AnalysisContext context) {
                if (data.getTradeTime() != null && !data.getTradeTime().isEmpty()) {
                    wechatBills.add(data);
                }
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {}
        }).sheet().headRowNumber(8).doReadSync();

        List<Bills> billsList = new ArrayList<>();
        for (WechatBill wechatBill : wechatBills) {
            Bills bill = wechatBill.toBills();
            if (bill != null && bill.getAmount().compareTo(BigDecimal.ZERO) != 0) {
                billsList.add(bill);
            }
        }
        return billsList;
    }

    // 支付宝解析（不变）
    private List<Bills> parseAlipayCsv(MultipartFile file) throws Exception {
        for (Charset charset : List.of(Charset.forName("GBK"), StandardCharsets.UTF_8)) {
            List<Bills> parsed = tryParseAlipayCsv(file, charset);
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }
        throw new RuntimeException("支付宝CSV解析失败（编码或格式错误）");
    }

    private List<Bills> tryParseAlipayCsv(MultipartFile file, Charset charset) throws Exception {
        List<Bills> billsList = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), charset))) {

            String line;
            int lineNo = 0;
            boolean isHeaderPassed = false;

            while ((line = reader.readLine()) != null) {
                lineNo++;

                if (lineNo <= 9) {
                    continue;
                }

                line = line.trim();
                if (line.isBlank()) {
                    continue;
                }

                if (!isHeaderPassed) {
                    if (line.startsWith("记录时间") || line.startsWith("交易时间")) {
                        isHeaderPassed = true;
                        continue;
                    }
                }

                List<String> fields = parseCsvLine(line);
                if (fields.size() < 8) {
                    continue;
                }

                AlipayBill alipayBill = new AlipayBill();
                alipayBill.setRecordTime(stripBom(fields.get(0)));
                alipayBill.setCategory(fields.get(1));
                alipayBill.setIncomeExpenseType(fields.get(2));
                alipayBill.setAmountStr(fields.get(3));
                alipayBill.setRemark(fields.get(4));
                alipayBill.setAccount(fields.get(5));
                alipayBill.setSourceText(fields.get(6));
                alipayBill.setTag(fields.get(7));

                Bills bill = alipayBill.toBills();
                if (bill != null && bill.getAmount().compareTo(BigDecimal.ZERO) != 0) {
                    billsList.add(bill);
                }
            }
        }

        return billsList;
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '\"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    current.append('\"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }

        fields.add(current.toString());
        return fields;
    }

    private String stripBom(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\uFEFF", "").trim();
    }
}