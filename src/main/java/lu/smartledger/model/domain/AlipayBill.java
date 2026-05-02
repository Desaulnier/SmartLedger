package lu.smartledger.model.domain;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

@Data
public class AlipayBill {

    @ExcelProperty(index = 0)
    private String recordTime;

    @ExcelProperty(index = 1)
    private String category;

    @ExcelProperty(index = 2)
    private String incomeExpenseType;

    @ExcelProperty(index = 3)
    private String amountStr;

    @ExcelProperty(index = 4)
    private String remark;

    @ExcelProperty(index = 5)
    private String account;

    @ExcelProperty(index = 6)
    private String sourceText;

    @ExcelProperty(index = 7)
    private String tag;

    /**
     * 转换为Bills对象
     */
    public Bills toBills() {
        Bills bills = new Bills();

        // 解析时间
        if (recordTime != null && !recordTime.trim().isEmpty()) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                LocalDateTime dateTime = LocalDateTime.parse(recordTime.trim(), formatter);
                bills.setOccurTime(dateTime);
            } catch (DateTimeParseException e) {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
                    LocalDateTime dateTime = LocalDateTime.parse(recordTime.trim(), formatter);
                    bills.setOccurTime(dateTime);
                } catch (Exception ex) {
                    try {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                        LocalDateTime dateTime = LocalDateTime.parse(recordTime.trim(), formatter);
                        bills.setOccurTime(dateTime);
                    } catch (Exception e2) {
                        bills.setOccurTime(LocalDateTime.now());
                    }
                }
            }
        }

        // 解析金额
        if (amountStr != null && !amountStr.trim().isEmpty()) {
            try {
                String cleanAmount = amountStr.trim()
                        .replace("¥", "")
                        .replace(",", "")
                        .replace(" ", "");
                cleanAmount = cleanAmount.replaceAll("^支出", "-").replaceAll("^收入", "");
                if (Pattern.matches("^-?\\d+(\\.\\d+)?$", cleanAmount)) {
                    bills.setAmount(new BigDecimal(cleanAmount));
                } else {
                    bills.setAmount(BigDecimal.ZERO);
                }
            } catch (Exception e) {
                bills.setAmount(BigDecimal.ZERO);
            }
        }

        // 解析收支类型
        if (incomeExpenseType != null && !incomeExpenseType.trim().isEmpty()) {
            String type = incomeExpenseType.trim();
            if ("支出".equals(type)) {
                bills.setBillType("EXPENSE");
            } else if ("收入".equals(type)) {
                bills.setBillType("INCOME");
            } else if ("转账".equals(type)) {
                bills.setBillType("EXPENSE");
            }
        }

        // 处理备注
        String finalRemark = remark != null ? remark.trim() : "";
        if (sourceText != null && !sourceText.trim().isEmpty()) {
            finalRemark = finalRemark + "（来源：" + sourceText.trim() + "）";
        }
        bills.setRemark(finalRemark);

        // 设置基础字段
        bills.setSource("IMPORT");
        bills.setCreatedAt(LocalDateTime.now());
        bills.setUpdatedAt(LocalDateTime.now());

        // 过滤无效数据
        if (bills.getAmount() == null || bills.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return bills;
    }
}