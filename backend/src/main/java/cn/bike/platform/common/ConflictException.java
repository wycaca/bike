package cn.bike.platform.common;

public class ConflictException extends RuntimeException {

    /** 输入: 可返回给调用方的冲突原因; 输出: HTTP 409 对应的业务异常。 */
    public ConflictException(String message) {
        super(message);
    }
}
