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
import java.util.stream.Collectors;

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

    private Byte resolveConsumptionAttribute(Long categoryId) {
        if (categoryId == null) {
            return 1;
        }

        Categories category = categoriesMapper.selectById(categoryId);
        if (category == null || category.getDefaultType() == null) {
            return 1;
        }

        return category.getDefaultType();
    }

    private Categories getCategoryById(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoriesMapper.selectById(categoryId);
    }

    private void validateCategoryMatch(Long categoryId, String billType) {
        Categories category = getCategoryById(categoryId);
        if (category == null) {
            throw new RuntimeException("Category not found");
        }
        if (billType == null || category.getType() == null || !billType.equalsIgnoreCase(category.getType())) {
            throw new RuntimeException("璐﹀崟绫诲瀷涓庡垎绫荤被鍨嬩笉鍖归厤");
        }
    }

    private void normalizeBillExtraFields(Bills bill) {
        if (bill == null || bill.getBillType() == null) {
            return;
        }

        if ("INCOME".equals(bill.getBillType())) {
            if (bill.getIncomeSource() == null || bill.getIncomeSource().isBlank()) {
                bill.setIncomeSource("OTHER");
            }
            bill.setExpenseMethod(null);
        } else if ("EXPENSE".equals(bill.getBillType())) {
            if (bill.getExpenseMethod() == null || bill.getExpenseMethod().isBlank()) {
                bill.setExpenseMethod("OTHER");
            }
            bill.setIncomeSource(null);
        }
    }

    private BigDecimal calcCurrentMonthSpent(Long userId) {
        if (userId == null) {
            return BigDecimal.ZERO;
        }

        LocalDate now = LocalDate.now();
        LocalDateTime monthStart = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = now.withDayOfMonth(now.lengthOfMonth()).atTime(23, 59, 59);

        List<Bills> monthBills = this.list(
                new LambdaQueryWrapper<Bills>()
                        .eq(Bills::getUserId, userId)
                        .eq(Bills::getBillType, "EXPENSE")
                        .between(Bills::getOccurTime, monthStart, monthEnd)
        );

        BigDecimal total = BigDecimal.ZERO;
        for (Bills monthBill : monthBills) {
            if (monthBill.getAmount() != null) {
                total = total.add(monthBill.getAmount());
            }
        }
        return total;
    }

    private void refreshCurrentSpent(Long userId) {
        BigDecimal currentMonthSpent = calcCurrentMonthSpent(userId);
        usersMapper.update(
                null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Users>()
                        .eq(Users::getId, userId)
                        .set(Users::getCurrentSpent, currentMonthSpent)
        );
    }

    /**
     * Parse bill file and return preview data
     */
    @Override
    @Transactional
    public JsonResponse<Map<String, Object>> parseBillFile(MultipartFile file) {
        try {
            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.isBlank()) {
                return JsonResponse.fail("File name must not be blank");
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
                return JsonResponse.fail("Unsupported file format. Only CSV/Excel is supported.");
            }

            if (billsList.isEmpty()) {
                return JsonResponse.fail("No valid bills found in file");
            }

            autoClassify(billsList);

            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            Users user = usersMapper.selectOne(
                    new LambdaQueryWrapper<Users>().eq(Users::getEmail, email)
            );
            if (user == null) {
                return JsonResponse.fail("褰撳墠鐧诲綍鐢ㄦ埛涓嶅瓨鍦紝鏃犳硶瀵煎叆璐﹀崟");
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

            return JsonResponse.success("Parse success, total " + billsList.size() + " records", data);
        } catch (Exception e) {
            return JsonResponse.fail("Parse failed: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmImport(Long importRecordId, List<Map<String, Object>> billList, Long userId) {
        if (importRecordId == null) {
            throw new RuntimeException("瀵煎叆璁板綍ID涓嶈兘涓虹┖");
        }
        if (userId == null) {
            throw new RuntimeException("User not logged in");
        }
        if (billList == null || billList.isEmpty()) {
            throw new RuntimeException("娌℃湁鍙鍏ョ殑璐﹀崟鏁版嵁");
        }

        BillImportRecords record = importRecordsService.getById(importRecordId);
        if (record == null) {
            throw new RuntimeException("Import record not found, please parse the file again");
        }
        if (!userId.equals(record.getUserId())) {
            throw new RuntimeException("No permission to operate this import record");
        }

        Byte importAccountType = null;
        String importAccountName = null;

        if ("csv".equalsIgnoreCase(record.getFileType())) {
            importAccountType = (byte) 3;
            importAccountName = "Alipay import account";
        } else if ("excel".equalsIgnoreCase(record.getFileType())) {
            importAccountType = (byte) 4;
            importAccountName = "寰俊璐︽埛";
        }


        List<Bills> bills = new ArrayList<>();
        Map<Long, BigDecimal> accountBalanceDeltaMap = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();

        Accounts currentAccount = getCurrentOrDefaultAccount(userId);
        Long currentAccountId = currentAccount.getId();

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
            try {
                validateCategoryMatch(categoryId, billType);
            } catch (Exception e) {
                continue;
            }
            bill.setCategoryId(categoryId);
            bill.setConsumptionAttribute(resolveConsumptionAttribute(categoryId));

            Object remarkObj = map.get("remark");
            String remark = remarkObj != null ? remarkObj.toString() : "";
            bill.setRemark(remark);

            bill.setOccurTime(parseOccurTime(map, now));
            bill.setUserId(userId);
            Long accountId = currentAccountId;
            bill.setAccountId(accountId);
            bill.setSource("IMPORT");
            bill.setImportRecordId(importRecordId);
            bill.setCreatedAt(now);
            bill.setUpdatedAt(now);
            normalizeBillExtraFields(bill);
            if ("EXPENSE".equals(billType)) {
                anomalyDetectionService.detectAnomaly(bill, historyBills);
                if (bill.getAnomalyType() != null || (bill.getAnomalyScore() != null && bill.getAnomalyScore().doubleValue() >= 0.68)) {
                    bill.setIsAbnormal(true);
                } else {
                    bill.setIsAbnormal(false);
                }
                historyBills.add(bill);
            }
            else {
                bill.setIsAbnormal(false);//榛樿姝ｅ父
            }
            bills.add(bill);
            mergeAccountBalanceDelta(accountBalanceDeltaMap, accountId, billType, amount);

            if ("EXPENSE".equals(billType)) {
                totalExpense = totalExpense.add(amount);
            }
        }

        if (bills.isEmpty()) {
            throw new RuntimeException("娌℃湁绗﹀悎瑕佹眰鐨勮处鍗曞彲瀵煎叆");
        }

        saveBatch(bills);
        applyAccountBalanceDeltaMap(accountBalanceDeltaMap);

        record.setSuccessCount(bills.size());
        importRecordsService.updateById(record);

        if (totalExpense.compareTo(BigDecimal.ZERO) > 0) {
            refreshCurrentSpent(userId);
            warningRecordsService.createBudgetWarningRecord(userId);
            
            for (Bills bill : bills) {
                if ("EXPENSE".equals(bill.getBillType())) {
                    warningRecordsService.createAnomalyWarningRecord(userId, bill);
                }
            }
        }
    }



    // Time parsing helper
    private LocalDateTime parseOccurTime(Map<String, Object> map, LocalDateTime defaultTime) {
        // 鍏堣瘯occurTime
        Object occurTimeObj = map.get("occurTime");
        if (occurTimeObj != null && !occurTimeObj.toString().isBlank()) {
            try {
                return LocalDateTime.parse(occurTimeObj.toString());
            } catch (Exception e) {
                // 鏍煎紡閿欒锛岀户缁瘯billDate
            }
        }

        // 鍐嶈瘯billDate
        Object billDateObj = map.get("billDate");
        if (billDateObj != null && !billDateObj.toString().isBlank()) {
            try {
                return LocalDateTime.parse(billDateObj.toString() + "T00:00:00");
            } catch (Exception e) {
                // 鏍煎紡閿欒锛岀敤榛樿鏃堕棿
            }
        }

        return defaultTime;
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBills(Bills bill, Long userId) {
        if (bill == null) {
            throw new RuntimeException("璐﹀崟鏁版嵁涓嶈兘涓虹┖");
        }

        if (bill.getAmount() == null || bill.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("璐﹀崟閲戦蹇呴』澶т簬0");
        }

        if (bill.getBillType() == null || (!"EXPENSE".equals(bill.getBillType()) && !"INCOME".equals(bill.getBillType()))) {
            throw new RuntimeException("璐﹀崟绫诲瀷蹇呴』鏄疎XPENSE鎴朓NCOME");
        }

        if (bill.getCategoryId() == null) {
            throw new RuntimeException("璐﹀崟鍒嗙被涓嶈兘涓虹┖");
        }
        validateCategoryMatch(bill.getCategoryId(), bill.getBillType());

        bill.setUserId(userId);
        bill.setSource("MANUAL");
        bill.setCreatedAt(LocalDateTime.now());
        bill.setUpdatedAt(LocalDateTime.now());

        // 鑾峰彇褰撳墠璐︽埛
        Accounts currentAccount = getCurrentOrDefaultAccount(userId);
        bill.setAccountId(currentAccount.getId());
        if (bill.getConsumptionAttribute() == null && bill.getBillType() == "EXPENSE") {
            bill.setConsumptionAttribute(resolveConsumptionAttribute(bill.getCategoryId()));
        }
        normalizeBillExtraFields(bill);
        if ("INCOME".equals(bill.getBillType())) {
            bill.setConsumptionAttribute(null);
            bill.setAnomalyScore(null);
            bill.setAnomalyType(null);
            bill.setAnomalyReason(null);
            bill.setIsAbnormal(false);
        } else {
            if (bill.getConsumptionAttribute() == null) {
                bill.setConsumptionAttribute(resolveConsumptionAttribute(bill.getCategoryId()));
            }

            List<Bills> historyBills = this.list(
                    new LambdaQueryWrapper<Bills>()
                            .eq(Bills::getUserId, userId)
                            .eq(Bills::getBillType, "EXPENSE")
                            .orderByDesc(Bills::getOccurTime)
                            .last("limit 200")
            );

            anomalyDetectionService.detectAnomaly(bill, historyBills);

            if (bill.getAnomalyType() != null || (bill.getAnomalyScore() != null && bill.getAnomalyScore().doubleValue() >= 0.68)) {
                bill.setIsAbnormal(true);
            } else {
                bill.setIsAbnormal(false);
            }
        }

        this.save(bill);
        applyAccountBalanceChange(bill.getAccountId(), bill.getBillType(), bill.getAmount(), false);

        if ("EXPENSE".equals(bill.getBillType()) && bill.getAmount() != null && bill.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            refreshCurrentSpent(userId);
            warningRecordsService.createBudgetWarningRecord(userId);
            warningRecordsService.createAnomalyWarningRecord(userId, bill);
        }
    }


    @Override
    public Page<Bills> getUserBillList(Long userId, Integer pageNum, Integer pageSize, String type, String category, String startDate, String endDate, String keyword) {
        // 闃叉鍒嗛〉鍙傛暟涓簄ull
        pageNum = pageNum == null ? 1 : pageNum;
        pageSize = pageSize == null ? 10 : pageSize;

        Page<Bills> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Bills> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(Bills::getUserId, userId);

        if (type != null && !type.isEmpty()) {
            wrapper.eq(Bills::getBillType, type);
        }

        // Handle category parameter
        if (category != null && !category.isEmpty()) {
            try {
                wrapper.eq(Bills::getCategoryId, Long.parseLong(category));
            } catch (Exception e) {
                // Ignore invalid category format
            }
        }

        if (startDate != null && !startDate.isEmpty()) {
            try {
                LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
                wrapper.ge(Bills::getOccurTime, start);
            } catch (Exception e) {
                // Ignore invalid start date format
            }
        }

        if (endDate != null && !endDate.isEmpty()) {
            try {
                LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");
                wrapper.le(Bills::getOccurTime, end);
            } catch (Exception e) {
                // Ignore invalid end date format
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
            throw new RuntimeException("璐﹀崟ID涓嶈兘涓虹┖");
        }
        Bills bill = this.getById(billId);
        if (bill == null) {
            throw new RuntimeException("Bill not found");
        }
        if (!bill.getUserId().equals(userId)) {
            throw new RuntimeException("No permission to delete this bill");
        }

        applyAccountBalanceChange(bill.getAccountId(), bill.getBillType(), bill.getAmount(), true);
        this.removeById(billId);
        refreshCurrentSpent(userId);

        warningRecordsService.createBudgetWarningRecord(userId);
    }

    /**
     * 鏇存柊璐﹀崟
     *
     * @param bill
     * @param userId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBill(Bills bill, Long userId) {
        if (bill == null || bill.getId() == null) {
            throw new RuntimeException("璐﹀崟ID涓嶈兘涓虹┖");
        }
        if (userId == null) {
            throw new RuntimeException("User not logged in");
        }
        if (bill.getAmount() == null || bill.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("璐﹀崟閲戦蹇呴』澶т簬0");
        }
        if (bill.getBillType() == null || (!"EXPENSE".equals(bill.getBillType()) && !"INCOME".equals(bill.getBillType()))) {
            throw new RuntimeException("璐﹀崟绫诲瀷蹇呴』鏄疎XPENSE鎴朓NCOME");
        }
        if (bill.getCategoryId() == null) {
            throw new RuntimeException("鍒嗙被涓嶈兘涓虹┖");
        }

        validateCategoryMatch(bill.getCategoryId(), bill.getBillType());

        Bills oldBill = this.getById(bill.getId());
        if (oldBill == null) {
            throw new RuntimeException("Bill not found");
        }
        if (!userId.equals(oldBill.getUserId())) {
            throw new RuntimeException("鏃犳潈缂栬緫浠栦汉璐﹀崟");
        }

        BigDecimal oldExpense = "EXPENSE".equals(oldBill.getBillType()) ? oldBill.getAmount() : BigDecimal.ZERO;
        BigDecimal newExpense = "EXPENSE".equals(bill.getBillType()) ? bill.getAmount() : BigDecimal.ZERO;
        BigDecimal diff = newExpense.subtract(oldExpense);
        Long oldAccountId = oldBill.getAccountId();
        String oldBillType = oldBill.getBillType();
        BigDecimal oldAmount = oldBill.getAmount();

        oldBill.setAmount(bill.getAmount());
        oldBill.setBillType(bill.getBillType());
        oldBill.setCategoryId(bill.getCategoryId());
        oldBill.setOccurTime(bill.getOccurTime());
        oldBill.setRemark(bill.getRemark());
        if ("INCOME".equals(bill.getBillType())) {
            oldBill.setConsumptionAttribute(null);
            oldBill.setAnomalyScore(null);
            oldBill.setAnomalyType(null);
            oldBill.setAnomalyReason(null);
            oldBill.setIsAbnormal(false);
        } else {
            oldBill.setConsumptionAttribute(
                    bill.getConsumptionAttribute() != null
                            ? bill.getConsumptionAttribute()
                            : resolveConsumptionAttribute(bill.getCategoryId())
            );
        }
        oldBill.setIncomeSource(bill.getIncomeSource());
        oldBill.setExpenseMethod(bill.getExpenseMethod());
        if (bill.getAccountId() != null) {
            oldBill.setAccountId(bill.getAccountId());
        }
        normalizeBillExtraFields(oldBill);
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

            if (oldBill.getAnomalyType() != null || (oldBill.getAnomalyScore() != null && oldBill.getAnomalyScore().doubleValue() >= 0.68)) {
                oldBill.setIsAbnormal(true);
            } else {
                oldBill.setIsAbnormal(false);
            }
        } else {
            oldBill.setAnomalyScore(null);
            oldBill.setAnomalyType(null);
            oldBill.setAnomalyReason(null);
            oldBill.setIsAbnormal(false);
        }

        applyAccountBalanceChange(oldAccountId, oldBillType, oldAmount, true);
        this.updateById(oldBill);
        applyAccountBalanceChange(oldBill.getAccountId(), oldBill.getBillType(), oldBill.getAmount(), false);

        if (diff.compareTo(BigDecimal.ZERO) != 0) {
            refreshCurrentSpent(userId);
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

    /**
     * 获取月度建议
     * */
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
    suggestions.add("本月暂未记录支出，建议先持续记账，系统才能为你生成更准确的消费建议。");
    return suggestions;
}

BigDecimal desireRatio = desireExpense.divide(totalExpense, 4, RoundingMode.HALF_UP);
BigDecimal necessaryRatio = necessaryExpense.divide(totalExpense, 4, RoundingMode.HALF_UP);

if (desireRatio.compareTo(BigDecimal.valueOf(0.3)) > 0) {
    suggestions.add("本月欲望型消费占比较高，建议优先压缩奶茶、娱乐和冲动购物等非必要支出。");
}

if (balance.compareTo(BigDecimal.ZERO) < 0) {
    suggestions.add("本月已经出现超支，建议控制后续消费节奏，优先保留必要支出。");
} else if (balance.compareTo(totalExpense.multiply(BigDecimal.valueOf(0.2))) > 0) {
    suggestions.add("本月结余情况较好，可以继续保持当前消费节奏，适当增加储蓄。");
}

if (necessaryRatio.compareTo(BigDecimal.valueOf(0.6)) < 0) {
    suggestions.add("必要消费占比较低，说明非必要支出偏多，建议适当优化消费结构。");
}

if (improveExpense.compareTo(desireExpense) < 0) {
    suggestions.add("生活改善型消费少于欲望型消费，建议将更多预算用于提升生活质量和学习成长。");
}

if (suggestions.isEmpty()) {
    suggestions.add("本月消费结构整体较为合理，请继续保持良好的记账和预算习惯。");
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

    /**
     * Get daily spending trend for the specified month
     *
     * @param userId   鐢ㄦ埛ID
     * @param monthStr 鏈堜唤瀛楃涓诧紝鏍煎紡涓簓yyy-MM
     * @return 娑堣垂瓒嬪娍鏁版嵁鍒楄〃
     */
    @Override
    public List<Map<String, Object>> getDailyTrend(Long userId, String monthStr) {
        List<Map<String, Object>> trendData = new ArrayList<>();
        if (userId == null || monthStr == null || monthStr.isEmpty()) {
            return trendData;
        }

        LocalDate firstDay;
        try {
            firstDay = LocalDate.parse(monthStr + "-01");
        } catch (Exception e) {
            return trendData;
        }
        LocalDate lastDay = firstDay.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());

        List<Bills> bills = this.list(
                new LambdaQueryWrapper<Bills>()
                        .eq(Bills::getUserId, userId)
                        .eq(Bills::getBillType, "EXPENSE")
                        .ge(Bills::getOccurTime, firstDay.atStartOfDay())
                        .le(Bills::getOccurTime, lastDay.atTime(23, 59, 59))
                        .orderByAsc(Bills::getOccurTime)
        );

        Map<LocalDate, BigDecimal> dailyMap = new LinkedHashMap<>();
        for (int i = 0; i < lastDay.getDayOfMonth(); i++) {
            dailyMap.put(firstDay.plusDays(i), BigDecimal.ZERO);
        }

        for (Bills bill : bills) {
            if (bill.getOccurTime() != null && bill.getAmount() != null) {
                LocalDate date = bill.getOccurTime().toLocalDate();
                dailyMap.merge(date, bill.getAmount(), BigDecimal::add);
            }
        }

        for (Map.Entry<LocalDate, BigDecimal> entry : dailyMap.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("date", entry.getKey().getDayOfMonth() + "d");
            item.put("amount", entry.getValue());
            trendData.add(item);
        }

        return trendData;
    }

    private String getCategoryName(Long categoryId, Map<Long, String> cache) {
        if (categoryId == null) {
            return "Uncategorized";
        }

        if (cache.containsKey(categoryId)) {
            return cache.get(categoryId);
        }

        Categories category = categoriesMapper.selectById(categoryId);
        String name = category != null ? category.getName() : "Uncategorized";
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
            defaultAccount.setAccountName("榛樿璐︽埛");
            defaultAccount.setAccountType((byte) 5);
            defaultAccount.setBalance(BigDecimal.ZERO);
            defaultAccount.setIsDefault((byte) 1);
            defaultAccount.setIsDeleted((byte) 0);
            defaultAccount.setCreateTime(LocalDateTime.now());
            defaultAccount.setUpdateTime(LocalDateTime.now());
            accountsService.save(defaultAccount);

            Users user = usersMapper.selectById(userId);
            if (user != null && user.getCurrentAccountId() == null) {
                user.setCurrentAccountId(defaultAccount.getId());
                usersMapper.updateById(user);
            }
        }

        return defaultAccount;
    }

    private void autoClassify(List<Bills> billsList) {
        List<CategoryRules> rules = categoryRulesService.lambdaQuery()
                .eq(CategoryRules::getIsEnabled, true)
                .list();

        if (rules.isEmpty()) {
            return;
        }

        Set<Long> categoryIds = new HashSet<>();
        for (CategoryRules rule : rules) {
            if (rule.getCategoryId() != null) {
                categoryIds.add(rule.getCategoryId());
            }
        }

        Map<Long, Categories> categoryMap = categoryIds.isEmpty()
                ? Collections.emptyMap()
                : categoriesMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(Categories::getId, c -> c));

        for (Bills bill : billsList) {
            String remark = bill.getRemark() == null ? "" : bill.getRemark();
            String cleanedRemark = cleanRemark(remark).toLowerCase();
            String billType = bill.getBillType();

            for (CategoryRules rule : rules) {
                if (rule.getCategoryId() == null || rule.getKeyword() == null) {
                    continue;
                }

                Categories category = categoryMap.get(rule.getCategoryId());
                if (category == null || category.getType() == null) {
                    continue;
                }

                if (billType == null || !billType.equalsIgnoreCase(category.getType())) {
                    continue;
                }

                String keyword = rule.getKeyword().trim().toLowerCase();
                if (!keyword.isEmpty() && cleanedRemark.contains(keyword)) {
                    bill.setCategoryId(rule.getCategoryId());
                    bill.setConsumptionAttribute(resolveConsumptionAttribute(rule.getCategoryId()));
                    break;
                }
            }
        }
    }

    /**
     * Get current account or default account
     *
     * @param userId
     * @return
     */
    private Accounts getCurrentOrDefaultAccount(Long userId) {
        Users user = usersMapper.selectById(userId);
        if (user != null && user.getCurrentAccountId() != null) {
            Accounts currentAccount = accountsService.getById(user.getCurrentAccountId());
            if (currentAccount != null
                    && userId.equals(currentAccount.getUserId())
                    && currentAccount.getIsDeleted() != null
                    && currentAccount.getIsDeleted() == 0) {
                return currentAccount;
            }
        }

        Accounts defaultAccount = getOrCreateDefaultAccount(userId);

        if (user != null && user.getCurrentAccountId() == null) {
            user.setCurrentAccountId(defaultAccount.getId());
            usersMapper.updateById(user);
        }

        return defaultAccount;
    }

    private void mergeAccountBalanceDelta(Map<Long, BigDecimal> deltaMap, Long accountId, String billType, BigDecimal amount) {
        if (accountId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal signedDelta = calculateBalanceDelta(billType, amount, false);
        if (signedDelta.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        deltaMap.merge(accountId, signedDelta, BigDecimal::add);
    }

    private void applyAccountBalanceDeltaMap(Map<Long, BigDecimal> deltaMap) {
        if (deltaMap == null || deltaMap.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, BigDecimal> entry : deltaMap.entrySet()) {
            updateAccountBalance(entry.getKey(), entry.getValue());
        }
    }

    private void applyAccountBalanceChange(Long accountId, String billType, BigDecimal amount, boolean reverse) {
        if (accountId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal delta = calculateBalanceDelta(billType, amount, reverse);
        updateAccountBalance(accountId, delta);
    }

    private BigDecimal calculateBalanceDelta(String billType, BigDecimal amount, boolean reverse) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal delta;
        if ("INCOME".equals(billType)) {
            delta = amount;
        } else if ("EXPENSE".equals(billType)) {
            delta = amount.negate();
        } else {
            return BigDecimal.ZERO;
        }

        return reverse ? delta.negate() : delta;
    }

    private void updateAccountBalance(Long accountId, BigDecimal delta) {
        if (accountId == null || delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        Accounts account = accountsService.getById(accountId);
        if (account == null) {
            return;
        }

        BigDecimal currentBalance = account.getBalance() == null ? BigDecimal.ZERO : account.getBalance();
        account.setBalance(currentBalance.add(delta));
        account.setUpdateTime(LocalDateTime.now());
        accountsService.updateById(account);
    }


    private String cleanRemark(String remark) {
        if (remark == null || remark.isEmpty()) {
            return "";
        }

        String cleaned = remark;
        cleaned = cleaned.replaceAll("\\u3010.*?\\u3011", "");
        cleaned = cleaned.replaceAll("_.*", "");
        cleaned = cleaned.replaceAll("\\u6536\\u6B3E\\u65B9\\u5907\\u6CE8:", "");
        cleaned = cleaned.replaceAll("杞处澶囨敞:", "");
        cleaned = cleaned.replaceAll("鍟嗘埛鍗曞彿.*", "");
        cleaned = cleaned.replaceAll("/$", "");
        cleaned = cleaned.replaceAll("鏀粯$", "");
        cleaned = cleaned.replaceAll("-鏀粯$", "");
        cleaned = cleaned.replaceAll("\\s+", "");

        return cleaned.trim();
    }

    // 寰俊瑙ｆ瀽锛堜笉鍙橈級
    private List<Bills> parseWechatExcel(MultipartFile file) throws Exception {
        List<WechatBill> wechatBills = new ArrayList<>();

        EasyExcel.read(file.getInputStream(), WechatBill.class, new AnalysisEventListener<WechatBill>() {
            @Override
            public void invoke(WechatBill data, AnalysisContext context) {
                if (data.getTradeTime() != null && !data.getTradeTime().isEmpty()) {
                    wechatBills.add(data);
                }
            }

            /**
             * Called after all rows are read
             * @param context
             */
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

    // Parse Alipay CSV
    private List<Bills> parseAlipayCsv(MultipartFile file) throws Exception {
        for (Charset charset : List.of(Charset.forName("GBK"), StandardCharsets.UTF_8)) {
            List<Bills> parsed = tryParseAlipayCsv(file, charset);
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }
        throw new RuntimeException("Failed to parse Alipay CSV");
    }

    /**
     * 灏濊瘯瑙ｆ瀽鏀粯瀹滳SV鏂囦欢
     *
     * @param file      CSV鏂囦欢
     * @param charset   鏂囦欢缂栫爜
     * @return 瑙ｆ瀽鍚庣殑璐﹀崟鍒楄〃
     * @throws Exception 瑙ｆ瀽寮傚父
     */
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
                    if (line.startsWith("璁板綍鏃堕棿") || line.startsWith("浜ゆ槗鏃堕棿")) {
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

    /**
     * Parse one CSV line
     */
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

    /**
     * Strip BOM
     */
    private String stripBom(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\uFEFF", "").trim();
    }
    /**
     * Find user account by account type
     */
    private Accounts findUserAccountByType(Long userId, Byte accountType) {
    if (userId == null || accountType == null) {
        return null;
    }

    return accountsService.getOne(
            new QueryWrapper<Accounts>()
                    .eq("user_id", userId)
                    .eq("account_type", accountType)
                    .eq("is_deleted", 0)
                    .last("limit 1")
    );
}

/**
 * 鑾峰彇鎴栧垱寤哄鍏ヨ处鍗曠殑璐︽埛
 */
private Accounts getOrCreateImportAccount(Long userId, Byte accountType, String accountName) {
    Accounts account = findUserAccountByType(userId, accountType);
    if (account != null) {
        return account;
    }

    account = new Accounts();
    account.setUserId(userId);
    account.setAccountName(accountName);
    account.setAccountType(accountType);
    account.setBalance(BigDecimal.ZERO);
    account.setIsDefault((byte) 0);
    account.setIsDeleted((byte) 0);
    account.setCreateTime(LocalDateTime.now());
    account.setUpdateTime(LocalDateTime.now());
    accountsService.save(account);

    return account;
}
}

