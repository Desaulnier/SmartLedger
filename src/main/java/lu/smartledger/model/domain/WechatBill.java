package lu.smartledger.model.domain;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
public class WechatBill {

    @ExcelProperty(index = 0)
    private String tradeTime;

    @ExcelProperty(index = 1)
    private String tradeType;

    @ExcelProperty(index = 2)
    private String tradePartner;

    @ExcelProperty(index = 3)
    private String goodsDesc;

    @ExcelProperty(index = 4)
    private String incomeExpense;

    @ExcelProperty(index = 5)
    private String amountStr;

    @ExcelProperty(index = 6)
    private String payMethod;

    @ExcelProperty(index = 7)
    private String status;

    @ExcelProperty(index = 8)
    private String tradeNo;

    public Bills toBills() {
        Bills bills = new Bills();

        // 1. 时间解析（简化逻辑，避免多层嵌套）
        if (tradeTime != null && !tradeTime.trim().isEmpty()) {
            String[] patterns = {"yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm", "yyyy/MM/dd HH:mm:ss"};
            for (String pattern : patterns) {
                try {
                    LocalDateTime dateTime = LocalDateTime.parse(tradeTime.trim(), DateTimeFormatter.ofPattern(pattern));
                    bills.setOccurTime(dateTime);
                    break; // 解析成功则退出循环
                } catch (Exception e) {
                    continue; // 失败则尝试下一个格式
                }
            }
        }
        // 时间解析失败时，默认设为当前时间（保留原逻辑）
        if (bills.getOccurTime() == null) {
            bills.setOccurTime(LocalDateTime.now());
        }

        // 2. 金额解析（修复核心问题：支出不转负，且处理空格）
        if (amountStr != null && !amountStr.trim().isEmpty()) {
            try {
                // 处理：¥、逗号、空格（如“¥ 1,000.00” → “1000.00”）
                String cleanAmount = amountStr.replace("¥", "").replace(",", "").replace(" ", "").trim();
                BigDecimal amount = new BigDecimal(cleanAmount);

                // 关键修复：支出/收入都保留正数，仅通过 billType 区分类型
                if ("支出".equals(incomeExpense)) {
                    bills.setBillType("EXPENSE");
                    bills.setAmount(amount.abs()); // 确保支出金额为正
                } else if ("收入".equals(incomeExpense)) {
                    bills.setBillType("INCOME");
                    bills.setAmount(amount.abs()); // 确保收入金额为正
                } else {
                    bills.setBillType("EXPENSE");
                    bills.setAmount(amount.abs()); // 未知类型默认按支出处理
                }
            } catch (Exception e) {
                bills.setAmount(BigDecimal.ZERO);
                return null; // 金额解析失败，返回null过滤该数据
            }
        } else {
            bills.setAmount(BigDecimal.ZERO);
            return null;
        }

        // 3. 备注拼接（保留原逻辑，优化空值判断）
        StringBuilder remark = new StringBuilder();
        if (tradePartner != null && !tradePartner.trim().isEmpty()) {
            remark.append(tradePartner.trim());
        }
        if (goodsDesc != null && !goodsDesc.trim().isEmpty() && !"/".equals(goodsDesc.trim())) {
            if (remark.length() > 0) {
                remark.append(" - ");
            }
            remark.append(goodsDesc.trim());
        }
        bills.setRemark(remark.toString());

        // 4. 基础字段（保留原逻辑）
        bills.setSource("IMPORT");
        bills.setCreatedAt(LocalDateTime.now());
        bills.setUpdatedAt(LocalDateTime.now());

        // 过滤金额为0的数据
        if (bills.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return bills;
    }
}