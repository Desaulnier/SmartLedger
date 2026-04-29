package lu.smartledger.service;

import lu.smartledger.model.domain.Bills;

import java.util.List;

public interface AnomalyDetectionService {
    void detectAnomaly(Bills bill, List<Bills> historyBills);
}
