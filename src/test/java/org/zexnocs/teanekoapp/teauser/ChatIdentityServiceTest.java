package org.zexnocs.teanekoapp.teauser;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.ITeaNekoUserData;
import org.zexnocs.teanekoapp.message.api.TeaNekoMessageType;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataRow;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDto;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDtoTaskConfig;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataService;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataTransactionDto;
import org.zexnocs.teanekocore.framework.function.IVoidCallable;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 通过 DTO 边界验证名称更新、空资料保留、群名片隔离及迟到消息，避免使用真实数据库。
 *
 * <br>2026/11/05：明确模拟调用参数的字符串类型，并移除不可能抛出的受检异常声明。
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.7-l
 * @version 4.8.0-l
 */
class ChatIdentityServiceTest {
    /**
     * 2026/11/04：相同用户在不同群中保留不同名片，空值和旧消息不清除新名称。
     * 2026/11/05：明确模拟调用参数的字符串类型，并移除不可能抛出的受检异常声明。
     */
    @Test
    void namesUpdateWithoutMixingGroupsOrRollingBackOnLateMessages() {
        var mapper = new ObjectMapper();
        var data = mock(IEasyDataService.class);
        var dto = mock(IEasyDataDto.class);
        var transaction = mock(IEasyDataTransactionDto.class);
        var task = mock(IEasyDataDtoTaskConfig.class);
        var rows = new HashMap<String, EasyDataRow>();
        var work = new AtomicReference<IVoidCallable>();
        when(data.getEasyDataFor(any(), anyString(), anyString())).thenReturn(dto);
        when(dto.atomic()).thenReturn(transaction);
        when(dto.getTaskConfig(anyString())).thenReturn(task);
        doAnswer(call -> {
            work.set(call.getArgument(0));
            return task;
        }).when(task).addTransactionTask(any());
        when(task.pushAsync()).thenAnswer(ignored -> { work.get().call(); return CompletableFuture.completedStage(null); });
        when(transaction.find(anyString(), any())).thenAnswer(call -> rows.get(call.getArgument(0, String.class)));
        when(transaction.create(anyString(), anyString())).thenAnswer(call -> {
            String key = call.getArgument(0);
            var body = new AtomicReference<String>(call.getArgument(1));
            var row = mock(EasyDataRow.class);
            when(row.getKey()).thenReturn(key);
            when(row.getValue()).thenAnswer(ignored -> body.get());
            doAnswer(value -> { body.set(value.getArgument(0)); return null; }).when(row).setValue(anyString());
            return row;
        });
        doAnswer(call -> { EasyDataRow row = call.getArgument(0); rows.put(row.getKey(), row); return null; })
                .when(transaction).save(any());
        when(dto.get(anyString(), eq(ChatIdentityService.Identity.class))).thenAnswer(call -> {
            var row = rows.get(call.getArgument(0, String.class));
            return row == null ? null : mapper.readValue(row.getValue(), ChatIdentityService.Identity.class);
        });
        var message = mock(ITeaNekoMessageData.class);
        var user = mock(ITeaNekoUserData.class);
        var client = mock(ITeaNekoClient.class);
        var id = UUID.randomUUID();
        when(client.getClientId()).thenReturn("onebot");
        when(message.getClient()).thenReturn(client);
        when(message.getUserData()).thenReturn(user);
        when(user.getUuid()).thenReturn(id);
        when(user.getUserIdInPlatform()).thenReturn("123");
        when(user.getNickname()).thenReturn("小茶");
        when(message.getMessageType()).thenReturn(TeaNekoMessageType.GROUP);
        when(message.getScopeId()).thenReturn("group-a");
        when(message.getScopeName()).thenReturn("茶室");
        when(message.getSenderGroupNickname()).thenReturn("茶茶");
        when(message.getTime()).thenReturn(Instant.parse("2026-11-04T00:00:00Z"));
        var service = new ChatIdentityService(data, mapper);
        service.observe(message).toCompletableFuture().join();
        when(message.getScopeId()).thenReturn("group-b");
        when(message.getScopeName()).thenReturn("其他群");
        when(message.getSenderGroupNickname()).thenReturn("群内别名");
        service.observe(message).toCompletableFuture().join();
        assertEquals("茶茶", service.member("onebot", "group-a", id).orElseThrow().card());
        assertEquals("群内别名", service.member("onebot", "group-b", id).orElseThrow().card());
        when(message.getScopeId()).thenReturn("group-a");
        when(message.getScopeName()).thenReturn("");
        when(message.getSenderGroupNickname()).thenReturn("");
        service.observe(message).toCompletableFuture().join();
        assertEquals("茶室", service.name("onebot", "group-a"));
        assertEquals("茶茶", service.member("onebot", "group-a", id).orElseThrow().card());
        when(message.getTime()).thenReturn(Instant.parse("2026-11-03T00:00:00Z"));
        when(message.getScopeName()).thenReturn("过期名称");
        service.observe(message).toCompletableFuture().join();
        assertEquals("茶室", service.name("onebot", "group-a"));
    }
}
