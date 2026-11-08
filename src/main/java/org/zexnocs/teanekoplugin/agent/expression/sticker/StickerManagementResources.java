package org.zexnocs.teanekoplugin.agent.expression.sticker;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;
import org.zexnocs.teanekocore.plugin.management.IManagementResource;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 将明确图库用例注册到原管理网关，不发布 HTTP、默认角色或任意反射路由。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.9.0-l
 */
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.expression")
public final class StickerManagementResources {
    /** 禁止实例化资源命名空间。 */ private StickerManagementResources(){}
    /** stickers 的固定全局权限与字段声明。 */
    public static final IManagementResource.Descriptor QUERY=new IManagementResource.Descriptor("agent.expression","stickers",1,"agent.stickers.inspect",false,List.of(
            new IManagementResource.Field("kind","string","CATALOG 或 JOBS",true,false,List.of("CATALOG","JOBS")),
            new IManagementResource.Field("after","string","上页最后 ID，首屏空",false,false,List.of()),
            new IManagementResource.Field("limit","integer","一至一百项",true,false,List.of()),
            new IManagementResource.Field("category","string","空或主类别 ID",false,false,List.of()),
            new IManagementResource.Field("state","string","可选条目状态",false,false,List.of())));
    /** stickers.preview 的固定全局权限与字段声明。 */
    public static final IManagementResource.Descriptor PREVIEW=new IManagementResource.Descriptor("agent.expression","stickers.preview",1,"agent.stickers.preview",false,List.of(
            new IManagementResource.Field("id","string","素材 ID，不接受路径",true,false,List.of()),
            new IManagementResource.Field("expectedRevision","integer","预期条目版本",true,false,List.of())));
    /** stickers.change 的固定全局权限与字段声明。 */
    public static final IManagementResource.Descriptor CHANGE=new IManagementResource.Descriptor("agent.expression","stickers.change",1,"agent.stickers.manage",true,List.of(
            new IManagementResource.Field("operationId","string","持久幂等 UUID，至少保留24小时",true,false,List.of()),
            new IManagementResource.Field("id","string","素材 ID",true,false,List.of()),
            new IManagementResource.Field("expectedRevision","integer","预期条目版本",true,false,List.of()),
            new IManagementResource.Field("action","string","明确管理动作",true,false,List.of("PIN","UNPIN","DISABLE","ENABLE","DELETE","RESTORE","PATCH","REFRESH")),
            new IManagementResource.Field("details","object","仅 PATCH：category、visualDescription、usageDescription、tags",false,false,List.of()),
            new IManagementResource.Field("analysisScope","object","仅 REFRESH：已登记 clientId 和 scopeId；不继承用户权限",false,false,List.of())));
    /** stickers.operation 的固定全局权限与字段声明。 */
    public static final IManagementResource.Descriptor OPERATION=new IManagementResource.Descriptor("agent.expression","stickers.operation",1,"agent.stickers.inspect",false,List.of(
            new IManagementResource.Field("operationId","string","本人原操作幂等 UUID",true,false,List.of())));
    /**
     * 有界目录与作业管理查询。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.9.0-l
     */
    @Component public static final class Catalog implements IManagementResource<StickerManagement.Query,StickerManagement.Page>{
        private final StickerManagementService service;
        /** @param service 原管理用例 */ public Catalog(StickerManagementService service){this.service=service;}
        /** @return 明确全局权限 */ @Override public Descriptor descriptor(){return QUERY;}
        /** @return 明确请求类型 */ @Override public Class<StickerManagement.Query> requestType(){return StickerManagement.Query.class;}
        /**
         * @param context 已认证上下文
         * @param request 固定请求
         * @return 真实业务结果
         */
        @Override public CompletionStage<StickerManagement.Page> execute(ManagementContext context,StickerManagement.Query request){return service.query(context,request);}
    }
    /**
     * 受控缩略图管理资源。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.9.0-l
     */
    @Component public static final class Preview implements IManagementResource<StickerManagement.Preview,StickerManagement.PreviewView>{
        private final StickerManagementService service;
        /** @param service 原管理用例 */ public Preview(StickerManagementService service){this.service=service;}
        /** @return 明确全局权限 */ @Override public Descriptor descriptor(){return PREVIEW;}
        /** @return 明确请求类型 */ @Override public Class<StickerManagement.Preview> requestType(){return StickerManagement.Preview.class;}
        /**
         * @param context 已认证上下文
         * @param request 固定请求
         * @return 真实业务结果
         */
        @Override public CompletionStage<StickerManagement.PreviewView> execute(ManagementContext context,StickerManagement.Preview request){return service.preview(context,request);}
    }
    /**
     * 带原子版本及幂等回执的管理动作。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.9.0-l
     */
    @Component public static final class Change implements IManagementResource<StickerManagement.Change,StickerManagement.ChangeResult>{
        private final StickerManagementService service;
        /** @param service 原管理用例 */ public Change(StickerManagementService service){this.service=service;}
        /** @return 明确全局权限 */ @Override public Descriptor descriptor(){return CHANGE;}
        /** @return 明确请求类型 */ @Override public Class<StickerManagement.Change> requestType(){return StickerManagement.Change.class;}
        /**
         * @param context 已认证上下文
         * @param request 固定请求
         * @return 真实业务结果
         */
        @Override public CompletionStage<StickerManagement.ChangeResult> execute(ManagementContext context,StickerManagement.Change request){return service.change(context,request);}
    }
    /**
     * 本人原管理操作结果查询。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.9.0-l
     */
    @Component public static final class Operation implements IManagementResource<StickerManagement.OperationQuery,StickerManagement.ChangeResult>{
        private final StickerManagementService service;
        /** @param service 原管理用例 */ public Operation(StickerManagementService service){this.service=service;}
        /** @return 明确全局权限 */ @Override public Descriptor descriptor(){return OPERATION;}
        /** @return 明确请求类型 */ @Override public Class<StickerManagement.OperationQuery> requestType(){return StickerManagement.OperationQuery.class;}
        /**
         * @param context 已认证上下文
         * @param request 固定请求
         * @return 真实业务结果
         */
        @Override public CompletionStage<StickerManagement.ChangeResult> execute(ManagementContext context,StickerManagement.OperationQuery request){return service.operation(context,request);}
    }
}

