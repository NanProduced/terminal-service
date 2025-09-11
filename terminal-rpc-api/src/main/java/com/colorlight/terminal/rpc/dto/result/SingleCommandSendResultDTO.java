package com.colorlight.terminal.rpc.dto.result;

import java.io.Serializable;
import java.util.Objects;

/**
 * 单个指令下发结果DTO (纯Java实现，无Lombok依赖)
 *
 * @author Nan
 * @version 1.0.0
 */
public class SingleCommandSendResultDTO implements Serializable {

    private static final long serialVersionUID = -21589711770021271L;

    /**
     * 是否下发成功
     */
    private boolean success;

    /**
     * 指令ID
     */
    private String commandId;

    /**
     * 下发方式 (WEBSOCKET/REDIS_CACHE/FAILED)
     */
    private String sendMethod;

    /**
     * 结果消息
     */
    private String message;

    // 默认构造函数
    public SingleCommandSendResultDTO() {
    }

    // 全参数构造函数
    public SingleCommandSendResultDTO(boolean success, String commandId, String sendMethod, String message) {
        this.success = success;
        this.commandId = commandId;
        this.sendMethod = sendMethod;
        this.message = message;
    }

    // Getter和Setter方法
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getCommandId() {
        return commandId;
    }

    public void setCommandId(String commandId) {
        this.commandId = commandId;
    }

    public String getSendMethod() {
        return sendMethod;
    }

    public void setSendMethod(String sendMethod) {
        this.sendMethod = sendMethod;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    // equals, hashCode, toString
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SingleCommandSendResultDTO that = (SingleCommandSendResultDTO) o;
        return success == that.success &&
               Objects.equals(commandId, that.commandId) &&
               Objects.equals(sendMethod, that.sendMethod) &&
               Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, commandId, sendMethod, message);
    }

    @Override
    public String toString() {
        return "SingleCommandSendResultDTO{" +
               "success=" + success +
               ", commandId='" + commandId + '\'' +
               ", sendMethod='" + sendMethod + '\'' +
               ", message='" + message + '\'' +
               '}';
    }
}
