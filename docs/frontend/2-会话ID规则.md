# 2 · 会话 ID 规则

## 2.1 基本规则

1. **前端生成**，后端不会返回或分配。
2. 首次进入页面：从 `localStorage` 读取 `polaris.conversationId`；没有则用 `crypto.randomUUID()` 生成并写回。
3. 点击「新会话」：生成新 UUID → 写入 `localStorage` → 清空当前消息列表。
4. 刷新页面：沿用同一个 ID，继续同一个后端会话。
5. 多标签页共享 `localStorage`，因此会共用同一个会话；要模拟另一个用户，用无痕窗口。
6. 不要在不同用户之间复用 ID。当前后端没有鉴权，ID 即访问凭据。

## 2.2 参考实现

可放在 `src/lib/conversationId.ts` 或 store 内：

```ts
const KEY = 'polaris.conversationId'

export function loadOrCreateConversationId(): string {
  const existing = localStorage.getItem(KEY)?.trim()
  if (existing) return existing
  const created = crypto.randomUUID()
  localStorage.setItem(KEY, created)
  return created
}

export function createConversationId(): string {
  const created = crypto.randomUUID()
  localStorage.setItem(KEY, created)
  return created
}
```

`crypto.randomUUID()` 在 `localhost` 安全上下文中可用；如果团队要兼容非安全上下文，再加降级实现，不要引入新依赖。

## 2.3 消息记录持久化建议

建议把当前会话的消息也持久化到 `localStorage`（键名自定，例如 `polaris.messages.<conversationId>`），刷新后恢复聊天记录。

不做的后果：刷新后界面空白，但模型仍记得历史，用户会觉得像串话。

---

下一步 → [3-改造清单](3-改造清单.md)
