package lu.smartledger.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lu.smartledger.common.utls.JsonResponse;
import lu.smartledger.mapper.BillsMapper;
import lu.smartledger.mapper.CategoriesMapper;
import lu.smartledger.mapper.UsersMapper;
import lu.smartledger.mapper.WarningRecordsMapper;
import lu.smartledger.model.domain.AdminActionLogs;
import lu.smartledger.model.domain.Bills;
import lu.smartledger.model.domain.Categories;
import lu.smartledger.model.domain.Users;
import lu.smartledger.service.IAdminActionLogsService;
import lu.smartledger.service.UsersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private UsersService usersService;

    @Autowired
    private UsersMapper usersMapper;

    @Autowired
    private BillsMapper billsMapper;

    @Autowired
    private CategoriesMapper categoriesMapper;

    @Autowired
    private WarningRecordsMapper warningRecordsMapper;

    @Autowired
    private IAdminActionLogsService adminActionLogsService;

    @Autowired
    private lu.smartledger.service.AdminService adminService;

    @Autowired
    private lu.smartledger.mapper.BillImportRecordsMapper billImportRecordsMapper;

    @Autowired
    private lu.smartledger.mapper.AccountsMapper accountsMapper;

    @GetMapping("/stats")
    public JsonResponse getStats() {
        long totalUsers = 0;
        long totalBills = 0;
        BigDecimal totalExpense = BigDecimal.ZERO;
        long warningCount = 0;
        List<Map<String, Object>> recentUserList = new ArrayList<>();

        try {
            totalUsers = usersMapper.selectCount(null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            totalBills = billsMapper.selectCount(null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            List<Map<String, Object>> expenseRows = billsMapper.selectMaps(
                    new QueryWrapper<Bills>()
                            .select("SUM(amount) AS total_amount")
                            .eq("bill_type", "EXPENSE")
            );
            if (!expenseRows.isEmpty()) {
                Object sumValue = expenseRows.get(0).get("total_amount");
                if (sumValue != null) {
                    totalExpense = new BigDecimal(sumValue.toString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            warningCount = warningRecordsMapper.selectCount(null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            List<Users> recentUsers = usersMapper.selectList(
                    new LambdaQueryWrapper<Users>()
                            .orderByDesc(Users::getCreatedAt)
                            .last("LIMIT 4")
            );

            recentUserList = recentUsers.stream().map(user -> {
                Map<String, Object> item = new HashMap<>();
                item.put("username", user.getUsername());
                item.put("email", user.getEmail());
                item.put("createdAt", user.getCreatedAt() != null
                        ? user.getCreatedAt().toString().replace('T', ' ')
                        : null);
                item.put("status", user.getStatus());
                return item;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalUsers", totalUsers);
        result.put("totalBills", totalBills);
        result.put("totalExpense", totalExpense);
        result.put("warningCount", warningCount);
        result.put("recentUsers", recentUserList);

        return JsonResponse.success(result);
    }

    @GetMapping("/trend")
    public JsonResponse getUserTrend() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(6);
        LocalDateTime startDateTime = startDate.atStartOfDay();

        List<Users> users = usersMapper.selectList(
                new LambdaQueryWrapper<Users>()
                        .ge(Users::getCreatedAt, startDateTime)
        );

        Map<LocalDate, Long> counts = users.stream()
                .filter(user -> user.getCreatedAt() != null)
                .collect(Collectors.groupingBy(u -> u.getCreatedAt().toLocalDate(), Collectors.counting()));

        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = startDate.plusDays(i);
            Map<String, Object> item = new HashMap<>();
            item.put("date", date.toString());
            item.put("count", counts.getOrDefault(date, 0L));
            trend.add(item);
        }

        return JsonResponse.success(trend);
    }

    @GetMapping("/category-stats")
    public JsonResponse getCategoryStats() {
        List<Map<String, Object>> rawStats = billsMapper.selectMaps(
                new QueryWrapper<Bills>()
                        .select("category_id", "bill_type", "SUM(amount) AS total_amount")
                        .groupBy("category_id", "bill_type")
        );

        Map<Long, Map<String, Object>> statsByCategory = new HashMap<>();
        Set<Long> categoryIds = new HashSet<>();

        for (Map<String, Object> raw : rawStats) {
            Object categoryIdObj = raw.get("category_id");
            if (categoryIdObj == null) {
                continue;
            }
            Long categoryId = Long.valueOf(categoryIdObj.toString());
            categoryIds.add(categoryId);
            String billType = raw.get("bill_type") != null ? raw.get("bill_type").toString() : "";
            BigDecimal amount = BigDecimal.ZERO;
            Object amountObj = raw.get("total_amount");
            if (amountObj != null) {
                amount = new BigDecimal(amountObj.toString());
            }

            Map<String, Object> item = statsByCategory.computeIfAbsent(categoryId, k -> {
                Map<String, Object> map = new HashMap<>();
                map.put("categoryId", k);
                map.put("expenseAmount", BigDecimal.ZERO);
                map.put("incomeAmount", BigDecimal.ZERO);
                map.put("totalAmount", BigDecimal.ZERO);
                return map;
            });

            if ("EXPENSE".equals(billType)) {
                item.put("expenseAmount", ((BigDecimal) item.get("expenseAmount")).add(amount));
            } else if ("INCOME".equals(billType)) {
                item.put("incomeAmount", ((BigDecimal) item.get("incomeAmount")).add(amount));
            }
            item.put("totalAmount", ((BigDecimal) item.get("totalAmount")).add(amount));
        }

        if (!categoryIds.isEmpty()) {
            List<Categories> categoryList = categoriesMapper.selectBatchIds(categoryIds);
            Map<Long, String> categoryNames = categoryList.stream()
                    .collect(Collectors.toMap(Categories::getId, Categories::getName, (a, b) -> a));
            statsByCategory.values().forEach(item -> {
                Long categoryId = (Long) item.get("categoryId");
                item.put("categoryName", categoryNames.getOrDefault(categoryId, "未知分类"));
            });
        }

        List<Map<String, Object>> categoryStats = new ArrayList<>(statsByCategory.values());
        categoryStats.sort(Comparator.comparing(item -> item.get("categoryName").toString()));

        return JsonResponse.success(categoryStats);
    }

    @GetMapping("/users")
    public JsonResponse getAdminUsers(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword
    ) {
        Page<Users> result = usersService.getAdminUserList(pageNum, pageSize, status, role, keyword);
        return JsonResponse.success(result);
    }

    @PutMapping("/users/{id}/status")
    public JsonResponse updateUserStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        if (id == null || id <= 0) {
            return JsonResponse.fail("用户ID不合法");
        }
        String status = body.get("status");
        if (status == null || status.isBlank()) {
            return JsonResponse.fail("状态不能为空");
        }

        List<String> validStatuses = Arrays.asList("ACTIVE", "DISABLED", "PENDING", "BANNED");
        if (!validStatuses.contains(status)) {
            return JsonResponse.fail("无效的用户状态");
        }

        Users targetUser = usersMapper.selectById(id);
        String oldStatus = targetUser != null ? targetUser.getStatus() : "未知";

        int rows = usersMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Users>()
                        .eq(Users::getId, id)
                        .set(Users::getStatus, status)
        );
        if (rows <= 0) {
            return JsonResponse.fail("更新用户状态失败");
        }

        recordAdminAction("USER_MANAGEMENT", id, "将用户状态从 " + oldStatus + " 修改为 " + status);

        return JsonResponse.success("用户状态更新成功");
    }

    @DeleteMapping("/users/{id}")
    public JsonResponse deleteUser(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return JsonResponse.fail("用户ID不合法");
        }

        Users targetUser = usersMapper.selectById(id);
        if (targetUser == null) {
            return JsonResponse.fail("用户不存在");
        }

        String userInfo = targetUser.getUsername() + "(" + targetUser.getEmail() + ")";

        try {
            adminService.deleteUserWithCleanup(id);
            recordAdminAction("USER_MANAGEMENT", id, "删除用户：" + userInfo + "（已清理关联数据）");
            return JsonResponse.success("删除用户成功");
        } catch (RuntimeException e) {
            return JsonResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/bills")
    public JsonResponse getAdminBills(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String keyword
    ) {
        pageNum = pageNum == null ? 1 : pageNum;
        pageSize = pageSize == null ? 10 : pageSize;

        LambdaQueryWrapper<Bills> wrapper = new LambdaQueryWrapper<>();

        if (type != null && !type.isBlank()) {
            wrapper.eq(Bills::getBillType, type);
        }

        if (user != null && !user.isBlank()) {
            List<Users> matchedUsers = usersMapper.selectList(
                    new LambdaQueryWrapper<Users>()
                            .like(Users::getUsername, user)
                            .or()
                            .like(Users::getEmail, user)
            );

            if (matchedUsers.isEmpty()) {
                Page<Map<String, Object>> emptyPage = new Page<>(pageNum, pageSize);
                emptyPage.setRecords(Collections.emptyList());
                emptyPage.setTotal(0);
                emptyPage.setCurrent(pageNum);
                emptyPage.setSize(pageSize);
                emptyPage.setPages(0);
                return JsonResponse.success(emptyPage);
            }

            List<Long> userIds = matchedUsers.stream()
                    .map(Users::getId)
                    .collect(Collectors.toList());

            wrapper.in(Bills::getUserId, userIds);
        }

        if (startDate != null && !startDate.isBlank()) {
            try {
                wrapper.ge(Bills::getOccurTime, LocalDateTime.parse(startDate + "T00:00:00"));
            } catch (Exception ignored) {
            }
        }

        if (endDate != null && !endDate.isBlank()) {
            try {
                wrapper.le(Bills::getOccurTime, LocalDateTime.parse(endDate + "T23:59:59"));
            } catch (Exception ignored) {
            }
        }

        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Bills::getRemark, keyword);
        }

        wrapper.orderByDesc(Bills::getOccurTime);

        Page<Bills> page = billsMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        if (page.getRecords() == null || page.getRecords().isEmpty()) {
            Page<Map<String, Object>> emptyPage = new Page<>(pageNum, pageSize);
            emptyPage.setRecords(Collections.emptyList());
            emptyPage.setTotal(page.getTotal());
            emptyPage.setCurrent(page.getCurrent());
            emptyPage.setSize(page.getSize());
            emptyPage.setPages(page.getPages());
            return JsonResponse.success(emptyPage);
        }

        Set<Long> userIds = page.getRecords().stream()
                .map(Bills::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> categoryIds = page.getRecords().stream()
                .map(Bills::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        final Map<Long, Users> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : usersMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(Users::getId, u -> u));

        final Map<Long, Categories> categoryMap = categoryIds.isEmpty()
                ? Collections.emptyMap()
                : categoriesMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(Categories::getId, c -> c));


        List<Map<String, Object>> records = page.getRecords().stream().map(bill -> {
            Map<String, Object> item = new HashMap<>();
            Users owner = userMap.get(bill.getUserId());
            Categories category = categoryMap.get(bill.getCategoryId());

            item.put("id", bill.getId());
            item.put("userName", owner != null ? owner.getUsername() : "未知用户");
            item.put("email", owner != null ? owner.getEmail() : null);
            item.put("categoryName", category != null ? category.getName() : "未分类");
            item.put("type", bill.getBillType());
            item.put("amount", bill.getAmount());
            item.put("attribute", bill.getConsumptionAttribute());
            item.put("remark", bill.getRemark());
            item.put("billTime", bill.getOccurTime() != null
                    ? bill.getOccurTime().toLocalDate().toString()
                    : null);

            return item;
        }).collect(Collectors.toList());

        Page<Map<String, Object>> resultPage = new Page<>(pageNum, pageSize);
        resultPage.setRecords(records);
        resultPage.setTotal(page.getTotal());
        resultPage.setCurrent(page.getCurrent());
        resultPage.setSize(page.getSize());
        resultPage.setPages(page.getPages());

        return JsonResponse.success(resultPage);
    }

    @DeleteMapping("/bills/{id}")
    public JsonResponse deleteBill(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return JsonResponse.fail("账单ID不合法");
        }

        Bills bill = billsMapper.selectById(id);
        if (bill == null) {
            return JsonResponse.fail("账单不存在");
        }

        String billInfo = "金额:" + bill.getAmount() + ", 类型:" + bill.getBillType();

        int rows = billsMapper.deleteById(id);
        if (rows <= 0) {
            return JsonResponse.fail("删除账单失败");
        }

        recordAdminAction("BILL_MANAGEMENT", id, "删除账单：" + billInfo);

        return JsonResponse.success("删除账单成功");
    }

    @GetMapping("/action-logs")
    public JsonResponse getActionLogs(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String actionType
    ) {
        LambdaQueryWrapper<AdminActionLogs> wrapper = new LambdaQueryWrapper<>();

        if (actionType != null && !actionType.isBlank()) {
            wrapper.eq(AdminActionLogs::getActionType, actionType);
        }

        wrapper.orderByDesc(AdminActionLogs::getCreatedAt);

        Page<AdminActionLogs> page = adminActionLogsService.page(new Page<>(pageNum, pageSize), wrapper);

        Set<Long> adminIds = page.getRecords().stream()
                .map(AdminActionLogs::getAdminId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> adminNameMap = usersMapper.selectBatchIds(adminIds).stream()
                .collect(Collectors.toMap(Users::getId, Users::getUsername, (a, b) -> a));

        List<Map<String, Object>> records = page.getRecords().stream().map(log -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", log.getId());
            item.put("adminName", adminNameMap.getOrDefault(log.getAdminId(), "未知管理员"));
            item.put("actionType", log.getActionType());
            item.put("targetId", log.getTargetId());
            item.put("actionDesc", log.getActionDesc());
            item.put("ipAddress", log.getIpAddress());
            item.put("createdAt", log.getCreatedAt());
            return item;
        }).collect(Collectors.toList());

        Page<Map<String, Object>> resultPage = new Page<>(pageNum, pageSize);
        resultPage.setTotal(page.getTotal());
        resultPage.setCurrent(page.getCurrent());
        resultPage.setSize(page.getSize());
        resultPage.setRecords(records);
        resultPage.setPages(page.getPages());

        return JsonResponse.success(resultPage);
    }

    private void recordAdminAction(String actionType, Long targetId, String description) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            if (email == null || "anonymousUser".equals(email)) {
                return;
            }

            Users admin = usersMapper.selectOne(
                    new LambdaQueryWrapper<Users>().eq(Users::getEmail, email)
            );

            if (admin == null) {
                return;
            }

            AdminActionLogs log = new AdminActionLogs();
            log.setAdminId(admin.getId());
            log.setActionType(actionType);
            log.setTargetId(targetId);
            log.setActionDesc(description);
            log.setCreatedAt(LocalDateTime.now());

            adminActionLogsService.save(log);
        } catch (Exception e) {
        }
    }
}
