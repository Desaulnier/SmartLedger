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

    public Bills toBills(){
        Bills bills = new Bills();

        if(tradeTime != null && !tradeTime.isEmpty()){
            try {
                LocalDateTime dateTime = LocalDateTime.parse(tradeTime,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                bills.setOccurTime(dateTime);
            } catch (Exception e) {
                bills.setOccurTime(LocalDateTime.now());
            }
        }

        if(amountStr != null && !amountStr.isEmpty()){
            try {
                String cleanAmount = amountStr.replaceAll("[^0-9.\\-]", "");
                if (!cleanAmount.isEmpty()) {
                    BigDecimal amount = new BigDecimal(cleanAmount);
                    bills.setAmount(amount);
                }
            } catch (Exception e) {
                bills.setAmount(BigDecimal.ZERO);
            }
        }

        if("支出".equals(incomeExpense)) {
            bills.setBillType("EXPENSE");
        } else if("收入".equals(incomeExpense)) {
            bills.setBillType("INCOME");
        }

        String remark = goodsDesc;
        if(tradePartner != null && !tradePartner.isEmpty()) {
            String goods = (goodsDesc != null && !goodsDesc.equals("/") && !goodsDesc.isEmpty()) ? goodsDesc : "";
            remark = goods.isEmpty() ? tradePartner : tradePartner + " - " + goods;
        }
        bills.setRemark(remark);
        bills.setSource("IMPORT");
        bills.setCreatedAt(LocalDateTime.now());
        bills.setUpdatedAt(LocalDateTime.now());

        if(bills.getAmount() == null) {
            return null;
        }

        return bills;
    }
}