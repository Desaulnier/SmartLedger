package lu.smartledger.service;

import lu.smartledger.common.utls.JsonResponse;
import lu.smartledger.model.domain.Bills;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import javax.xml.transform.Result;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 账单表 服务类
 * </p>
 *
 * @author lu
 * @since 2026-04-14
 */
public interface IBillsService extends IService<Bills> {

    JsonResponse<Map<String, Object>> parseBillFile(MultipartFile file);
    //确认导入
    public void confirmImport(Long importRecordId, List<Map<String, Object>> billList);
}
