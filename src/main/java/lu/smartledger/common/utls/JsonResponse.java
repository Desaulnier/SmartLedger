package lu.smartledger.common.utls;

import lombok.Data;

@Data
public class JsonResponse<T> {
    /**
     * 状态码：
     * 200 = 成功
     * 400 = 参数错误
     * 500 = 业务异常（比如用户不存在、密码错误）
     * 501 = 系统异常（比如数据库报错）
     */
    private int code;
    // 提示信息：给用户看的友好提示（比如“登录成功”“密码错误”）
    private String message;

    /**
     * 响应数据（成功时返回，比如用户信息、账单列表）
     * 用泛型<T>：支持任意数据类型（User、List<User>、Integer等）
     */
    private T data;
    //成功响应
    public static <T> JsonResponse<T> success(){
        JsonResponse<T> response = new JsonResponse<>();
        response.setCode(200);
        response.setMessage("success");
        response.setData(null);
        return response;
    }
    /**
     * 成功响应（带数据）
     * 场景：登录、查询用户信息、查询账单列表等“需要返回业务数据”的接口
     * @param data 要返回的业务数据（如Users对象、List<Bill>列表）
     */
    public static <T> JsonResponse<T> success(T data){
        JsonResponse<T> response = new JsonResponse<>();
        response.setCode(200);
        response.setMessage("success");
        response.setData(data);
        return response;
    }
    /**
     * 成功响应（自定义提示语+数据）
     * 场景：需要个性化提示（如“登录成功”“注册成功”）
     * @param message 自定义提示语
     * @param data 要返回的业务数据
     */
    public  static <T> JsonResponse<T> success(String message, T data){
        JsonResponse<T> response = new JsonResponse<>();
        response.setCode(200);
        response.setMessage(message);
        response.setData(data);
        return response;
    }
    /**
     * 失败响应（业务异常，默认500状态码）
     * 场景：用户不存在、密码错误、账单不存在等业务逻辑错误
     * @param message 失败提示语（如“密码错误”“该邮箱未注册”）
     */
    public static <T> JsonResponse<T> fail(String message) {
        return fail(500, message);
    }

    /**
     * 失败响应（自定义状态码+提示语）
     * 场景：需要区分失败类型（如参数错误用400，系统错误用501）
     * @param code 状态码
     * @param message 失败提示语
     */
    public  static <T> JsonResponse<T> fail(int code, String message){
        JsonResponse<T> response = new JsonResponse<>();
        response.setCode(code);
        response.setMessage(message);
        response.setData(null);
        return response;
    }
    /**
     * 参数错误响应（默认400状态码）
     * 场景：邮箱为空、密码长度不足、账单金额为负数等参数校验失败
     * @param message 参数错误提示语
     */
    public static <T> JsonResponse<T> paramError(String message) {
        return fail(400, message);
    }
}
