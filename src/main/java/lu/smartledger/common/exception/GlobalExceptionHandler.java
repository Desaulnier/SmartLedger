package lu.smartledger.common.exception;

import lu.smartledger.common.utls.JsonResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 自动捕获接口异常，转换为统一的JsonResponse格式返回，无需手动try-catch
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * 捕获自定义业务异常（如用户不存在、密码错误）
     */
    @ExceptionHandler(BusinessException.class)
    public JsonResponse<?> handleBusinessException(BusinessException e) {
        return JsonResponse.fail(e.getMessage());
    }

    /**
     * 捕获参数校验异常（如空指针、非法参数）
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public JsonResponse<?> handleParamException(IllegalArgumentException e) {
        return JsonResponse.paramError(e.getMessage());
    }

    /**
     * 捕获系统异常（如数据库报错、未知错误）
     */
    @ExceptionHandler(Exception.class)
    public JsonResponse<?> handleSystemException(Exception e) {
        // 系统异常返回通用提示，避免暴露敏感信息
        return JsonResponse.fail(501, "系统繁忙，请稍后重试");
    }
}
