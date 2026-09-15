package org.example.travel_agent.Exceptions;

/**
 * 用户主动取消本轮对话。属于正常中止，不是系统故障：
 * 节点内部抛出它来中断工作流，各处 catch 识别后不应记 error trace。
 */
public class CancelException extends RuntimeException {

    public CancelException(String message) {
        super(message);
    }
}
