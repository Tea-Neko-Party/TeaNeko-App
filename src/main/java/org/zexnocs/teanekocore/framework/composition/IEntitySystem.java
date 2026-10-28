package org.zexnocs.teanekocore.framework.composition;

import java.util.concurrent.CompletionStage;

/**
 * 只接收快照并返回变更的异步执行步骤；业务依赖通过构造器注入实现类。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IEntitySystem {
    /**
     * 声明步骤的稳定标识、输入输出与前置依赖，供装配阶段编译执行顺序。
     *
     * @return 满足本方法约束的 {@code SystemDescriptor} 结果
     */
    SystemDescriptor descriptor();

    /**
     * 在只读快照与共享执行控制上运行一个步骤，异步返回受声明约束的补丁或跳过、停止结果。
     *
     * @param context 用于本次处理的 {@code context} 数据
     * @return 包含步骤决定与补丁的完成阶段，由管线校验并提交
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    CompletionStage<SystemResult> execute(SystemContext context) throws Exception;
}
