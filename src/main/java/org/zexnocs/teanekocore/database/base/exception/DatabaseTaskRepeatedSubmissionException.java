package org.zexnocs.teanekocore.database.base.exception;

/**
 * 数据库任务重复提交异常。
 * 当尝试重复提交一个已经提交的数据库任务时抛出此异常。
 *
 * @author zExNocs
 * @date 2026/02/15
 */
public class DatabaseTaskRepeatedSubmissionException extends RuntimeException {
    /**
     * 构造函数。
     *
     * @param message 异常消息
     */
    public DatabaseTaskRepeatedSubmissionException(String message) {
        super(message);
    }
}
