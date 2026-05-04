package lu.smartledger.model.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class MonthlyAnalysisDTO {
    private BigDecimal totalIncome = BigDecimal.ZERO;    // 总收入
    private BigDecimal totalExpense = BigDecimal.ZERO;   // 总支出
    private BigDecimal balance = BigDecimal.ZERO;         // 结余
    private BigDecimal necessaryExpense = BigDecimal.ZERO; // 必须消费
    private BigDecimal improveExpense = BigDecimal.ZERO;   // 改善生活
    private BigDecimal desireExpense = BigDecimal.ZERO;    // 欲望消费
}