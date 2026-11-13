# Nova AI Firestore Security Specification

## 1. Data Invariants
1. **Strict Multi-Tenant Isolation**: Every document lives under `/users/{userId}/...` AND carries an explicit `userId` field equal to both `{userId}` and `request.auth.uid`. Unauthenticated callers and cross-user callers are strictly rejected on all `get`, `list`, `create`, `update`, and `delete` operations.
2. **Parent Existence Invariant**: A `ChatMessage` document under `/users/{userId}/conversations/{conversationId}/messages/{messageId}` cannot be created unless its parent `/users/{userId}/conversations/{conversationId}` document exists.
3. **Query Enforcer Alignment**: Every `allow list` rule validates `resource.data.userId == request.auth.uid` in addition to `request.auth.uid == userId`, preventing unfiltered query scraping.
4. **Temporal & Identity Immutability**: `userId`, `conversationId`, and `createdAt` are immutable after document creation. Timestamps must be server-resolved `timestamp` instances `<= request.time`.
5. **Strict Key & Size Bounds**: Every string and integer field enforces strict size/range limits, and `hasOnly` / `affectedKeys().hasOnly(...)` blocks any shadow or undeclared fields.

## 2. The "Dirty Dozen" Adversarial Payloads
1. **Unauthenticated Read**: `unauthDb.collection("users/alice/conversations").where("userId", "==", "alice").get()` -> REJECTED (`request.auth != null`).
2. **Cross-User Read (PII/Transcript Leak)**: Bob reads `/users/alice/conversations/conv_1` -> REJECTED (`request.auth.uid == userId`).
3. **Unfiltered List Scraping**: Alice queries `users/alice/conversations` without `.where("userId", "==", "alice")` when seeded doc mismatches or unscoped -> REJECTED (`resource.data.userId == request.auth.uid`).
4. **Identity Spoofing on Create**: Alice creates `/users/alice/conversations/conv_1` with `userId: "bob"` -> REJECTED (`data.userId == request.auth.uid`).
5. **Orphaned Message Write**: Alice creates `/users/alice/conversations/nonexistent/messages/msg_1` without parent conversation -> REJECTED (`exists(...)`).
6. **Shadow Field Injection on Create**: Payload adds `"isAdmin": true` -> REJECTED (`incoming().keys().hasOnly(...)`).
7. **Shadow Field Injection on Update**: Alice updates `conv_1` adding `"isVerified": true` -> REJECTED (`affectedKeys().hasOnly(...)`).
8. **Immutable Field Tampering**: Alice updates `createdAt` or `userId` on an existing conversation -> REJECTED (`incoming().createdAt == existing().createdAt`).
9. **Enum Poisoning**: Alice creates conversation with `mode: "SUPERUSER"` or message with `role: "system"` -> REJECTED (`mode in ['chat', 'image', 'voice']`, `role in ['user', 'model']`).
10. **Denial-of-Wallet Oversized String**: Alice writes a 300-char `title` (limit 200) -> REJECTED (`data.title.size() <= 200`).
11. **Path Variable Poisoning**: Alice creates a document with invalid ID characters or >128 chars -> REJECTED (`isValidId(...)`).
12. **Future Timestamp Spoofing**: Client submits a `createdAt` timestamp in the future (`> request.time`) or epoch integer -> REJECTED (`is timestamp && <= request.time`).

## 3. Red Team Audit Summary
- **Conversations (`/users/{userId}/conversations/{conversationId}`)**:
  - Identity Spoofing: Protected (`data.userId == request.auth.uid` and `incoming().userId == existing().userId`).
  - Resource Poisoning: Protected (`isValidId`, bounded strings/ints, `hasOnly`).
  - Value Poisoning on Update: Protected (`isValidConversation(incoming())` wraps the entire update rule alongside `affectedKeys().hasOnly`).
- **Messages (`/users/{userId}/conversations/{conversationId}/messages/{messageId}`)**:
  - Orphaned Write: Protected (`exists` parent conversation check on `create`).
  - Identity & Path Binding: Protected (`data.conversationId == conversationId` and `data.userId == request.auth.uid`).
  - Value Poisoning on Update: Protected (`isValidChatMessage(incoming(), conversationId)` wraps update).
- **Voice Recordings (`/users/{userId}/voice_recordings/{recordingId}`)**:
  - Cross-User Transcript Leak: Protected (`request.auth.uid == userId` and `resource.data.userId == request.auth.uid`).
  - Value Poisoning on Update: Protected (`isValidVoiceRecording(incoming())` wraps update).
