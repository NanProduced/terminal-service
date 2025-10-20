package com.colorlight.terminal.commons.exception.technical;

/**
 * Redis事务失败异常
 * <p>用于检测Redis事务执行失败的场景，例如exec()返回null</p>
 *
 * @author Nan
 */
public class RedisTransactionFailedException extends TechnicalException {

    public RedisTransactionFailedException(String message) {
        super(TechErrorCode.REDIS_TRANSACTION_FAILED, message);
    }

    public RedisTransactionFailedException(String message, Throwable cause) {
        super(TechErrorCode.REDIS_TRANSACTION_FAILED, message, cause);
    }
}
