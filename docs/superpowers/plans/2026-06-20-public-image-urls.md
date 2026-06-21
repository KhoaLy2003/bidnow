# Public Image URLs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the presigned S3 GET URL flow (which required a `/download` round-trip per image render) with permanent Supabase public bucket URLs stored at upload time.

**Architecture:** The Supabase bucket is set to public. After any upload (server-side or presigned PUT), the backend computes the permanent public URL from the s3Key and returns it in the response. All downstream storage (auction images, avatar) writes this URL. The frontend uses the URL directly — no hook, no HTTP call, no resolution step.

**Tech Stack:** Spring Boot 3 (media-service, user-service, common), Kafka, Next.js 16 (App Router), TypeScript, Supabase Storage.

## Global Constraints

- No new dependencies may be added.
- `common` module changes compile into all services — rebuild all affected services after modifying it.
- Frontend has no test suite — verify by running `npm run dev` and manually checking image rendering.
- Backend media-service has no existing test files — no new test infrastructure needed.
- Build command for a single service (skip tests): `mvn clean install -pl <module> -DskipTests`
- Startup order: `discovery-service` → `api-gateway` → services.

---

## File Map

| File | Action | What changes |
|---|---|---|
| `backend/media-service/src/main/resources/application.yml` | Modify | Add `supabase.public-url` property |
| `backend/media-service/src/main/java/com/bidnow/media/dto/response/MediaUploadResponse.java` | Modify | Add `publicUrl` field |
| `backend/media-service/src/main/java/com/bidnow/media/dto/response/PresignedUrlResponse.java` | Modify | Add `publicUrl` field |
| `backend/media-service/src/main/java/com/bidnow/media/strategy/UploadEventStrategy.java` | Modify | Add `publicUrl` param to `handle()` |
| `backend/media-service/src/main/java/com/bidnow/media/strategy/UploadEventStrategyFactory.java` | Modify | Add `publicUrl` param to `dispatch()` |
| `backend/media-service/src/main/java/com/bidnow/media/strategy/impl/AvatarUploadEventStrategy.java` | Modify | Pass `publicUrl` into event |
| `backend/common/src/main/java/com/bidnow/common/dto/event/AvatarUploadedEvent.java` | Modify | Add `publicUrl` field |
| `backend/media-service/src/main/java/com/bidnow/media/service/impl/MediaServiceImpl.java` | Modify | Compute `publicUrl`; populate both responses; update strategy dispatch |
| `backend/user-service/src/main/java/com/bidnow/user/kafka/UserKafkaConsumer.java` | Modify | Store `event.getPublicUrl()` instead of `event.getS3Key()` |
| `backend/media-service/src/main/java/com/bidnow/media/service/MediaService.java` | Modify | Remove `generateDownloadUrl` |
| `backend/media-service/src/main/java/com/bidnow/media/controller/MediaController.java` | Modify | Remove `GET /download` endpoint |
| `backend/media-service/src/main/java/com/bidnow/media/config/SecurityConfig.java` | Modify | Remove `/download` permit rule |
| `backend/media-service/src/main/java/com/bidnow/media/config/S3Config.java` | Modify | Remove `S3Presigner` bean |
| `frontend/.env.local` | Modify | Add `NEXT_PUBLIC_SUPABASE_URL` and `NEXT_PUBLIC_SUPABASE_BUCKET` |
| `frontend/types/api/media.api.ts` | Modify | Add `publicUrl` to both response types |
| `frontend/services/media.service.ts` | Modify | Remove `getDownloadUrl`; `PresignedUrlResponse` now has `publicUrl` |
| `frontend/hooks/useSecureImage.ts` | Modify | Replace async HTTP logic with sync URL construction |
| `frontend/app/(dashboard)/profile/page.tsx` | Modify | Use `publicUrl` instead of `s3Key` for avatar |
| `frontend/app/(dashboard)/seller/auctions/new/page.tsx` | Modify | Use `publicUrl` instead of `s3Key` for auction images |
| `frontend/app/(dashboard)/seller/auctions/[id]/manage/page.tsx` | Modify | Use `publicUrl` instead of `s3Key` for auction images |

**Not touched:** `AuctionGallery`, `UserAvatar`, `AuctionBrowseCard`, `AuctionBrowseCardHorizontal`, `SellerAuctionRow` — these already call `useSecureImage(url)` and need no changes since the hook's interface is unchanged.

---

## Task 1: Backend — Expose `publicUrl` from upload endpoints

**Files:**
- Modify: `backend/media-service/src/main/resources/application.yml`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/dto/response/MediaUploadResponse.java`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/dto/response/PresignedUrlResponse.java`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/strategy/UploadEventStrategy.java`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/strategy/UploadEventStrategyFactory.java`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/strategy/impl/AvatarUploadEventStrategy.java`
- Modify: `backend/common/src/main/java/com/bidnow/common/dto/event/AvatarUploadedEvent.java`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/service/impl/MediaServiceImpl.java`
- Modify: `backend/user-service/src/main/java/com/bidnow/user/kafka/UserKafkaConsumer.java`

**Interfaces:**
- Produces: `MediaUploadResponse.publicUrl` (String), `PresignedUrlResponse.publicUrl` (String) — consumed by Task 2 (frontend types)
- Produces: `AvatarUploadedEvent.publicUrl` (String) — consumed by `UserKafkaConsumer` in this same task

- [ ] **Step 1: Add Supabase public URL property to `application.yml`**

Add under the `aws:` block (after the existing S3 config):

```yaml
supabase:
  public-url: ${SUPABASE_PUBLIC_URL}
```

Full addition in context:
```yaml
aws:
  s3:
    ACCESS_KEY: ${AWS_ACCESS_KEY}
    SECRET_KEY: ${AWS_SECRET_KEY}
    REGION: ${AWS_REGION}
    BUCKET_NAME: ${AWS_BUCKET_NAME}
    ENDPOINT: ${AWS_ENDPOINT}

supabase:
  public-url: ${SUPABASE_PUBLIC_URL}
```

Also add `SUPABASE_PUBLIC_URL` to your local environment (the value is your Supabase project URL, e.g. `https://abcdefgh.supabase.co`).

- [ ] **Step 2: Add `publicUrl` field to `MediaUploadResponse`**

```java
@Schema(description = "Public URL to access the uploaded file directly", requiredMode = Schema.RequiredMode.REQUIRED)
private String publicUrl;
```

Add this field after `s3Key` in `MediaUploadResponse.java`. Keep `s3Key` — it remains useful for admin/debug purposes.

- [ ] **Step 3: Add `publicUrl` field to `PresignedUrlResponse`**

```java
@Schema(description = "Public URL to access the file after the presigned upload completes", requiredMode = Schema.RequiredMode.REQUIRED)
private String publicUrl;
```

Add this field after `s3Key` in `PresignedUrlResponse.java`.

- [ ] **Step 4: Add `publicUrl` to `AvatarUploadedEvent` in `common`**

Replace the entire file content:

```java
package com.bidnow.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvatarUploadedEvent {
    private UUID userId;
    private String s3Key;
    private String publicUrl;
}
```

- [ ] **Step 5: Update `UploadEventStrategy` interface to pass `publicUrl`**

Replace the `handle` method signature:

```java
void handle(UUID ownerId, UUID entityId, String s3Key, String publicUrl);
```

- [ ] **Step 6: Update `UploadEventStrategyFactory.dispatch()` to pass `publicUrl`**

Replace the `dispatch` method:

```java
public void dispatch(MediaEntityType entityType, UUID ownerId, UUID entityId, String s3Key, String publicUrl) {
    Optional.ofNullable(strategies.get(entityType))
            .ifPresentOrElse(
                    strategy -> strategy.handle(ownerId, entityId, s3Key, publicUrl),
                    () -> log.warn("No upload event strategy registered for entityType={}", entityType)
            );
}
```

- [ ] **Step 7: Update `AvatarUploadEventStrategy.handle()` to pass `publicUrl` into the event**

Replace the `handle` method:

```java
@Override
public void handle(UUID ownerId, UUID entityId, String s3Key, String publicUrl) {
    log.info("Handling USER_AVATAR upload for ownerId={}, s3Key={}", ownerId, s3Key);
    mediaKafkaProducer.publishAvatarUploaded(
            AvatarUploadedEvent.builder()
                    .userId(ownerId)
                    .s3Key(s3Key)
                    .publicUrl(publicUrl)
                    .build()
    );
}
```

- [ ] **Step 8: Update `MediaServiceImpl` to compute and populate `publicUrl`**

Add the injected property and helper at the top of the class (alongside the existing `@Value` for `bucketName`):

```java
@Value("${supabase.public-url}")
private String supabasePublicUrl;

private String buildPublicUrl(String s3Key) {
    return supabasePublicUrl + "/storage/v1/object/public/" + bucketName + "/" + s3Key;
}
```

In `uploadFile()`, after the `s3Client.putObject(...)` call and the `mediaAssetRepository.save(asset)` call, update the strategy dispatch and the return statement:

```java
String publicUrl = buildPublicUrl(s3Key);

if (entityType != null && ownerId != null) {
    uploadEventStrategyFactory.dispatch(entityType, ownerId, entityId, s3Key, publicUrl);
}

return MediaUploadResponse.builder()
        .id(saved.getId())
        .originalName(saved.getOriginalName())
        .s3Key(saved.getS3Key())
        .publicUrl(publicUrl)
        .contentType(saved.getContentType())
        .fileSize(saved.getFileSize())
        .width(saved.getWidth())
        .height(saved.getHeight())
        .createdAt(saved.getCreatedAt())
        .build();
```

In `generatePresignedUrl()`, update the return statement:

```java
return PresignedUrlResponse.builder()
        .uploadUrl(presignedRequest.url().toString())
        .s3Key(s3Key)
        .publicUrl(buildPublicUrl(s3Key))
        .expiresAt(expiresAt)
        .build();
```

- [ ] **Step 9: Update `UserKafkaConsumer` to store the public URL**

Replace the body of `consumeAvatarUploaded`:

```java
@Transactional
@KafkaListener(topics = "avatar-uploaded-topic", groupId = "${spring.kafka.consumer.group-id}")
public void consumeAvatarUploaded(AvatarUploadedEvent event) {
    log.info("Received AvatarUploadedEvent for userId={}", event.getUserId());

    UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
            .avatarUrl(event.getPublicUrl())
            .build();
    userProfileService.updateMyProfile(event.getUserId(), request);

    log.info("Updated avatar_url for userId={}", event.getUserId());
}
```

- [ ] **Step 10: Build `common`, then `media-service`, then `user-service`**

```bash
cd backend
mvn clean install -pl common -DskipTests
mvn clean install -pl media-service -DskipTests
mvn clean install -pl user-service -DskipTests
```

Expected: `BUILD SUCCESS` for all three.

- [ ] **Step 11: Smoke-test the upload endpoints**

Start services and call both upload endpoints. Verify `publicUrl` appears in the response.

Server-side upload:
```bash
curl -X POST http://localhost:8080/api/v1/media/upload \
  -H "Authorization: Bearer <token>" \
  -F "file=@/path/to/image.jpg" \
  -F "entityType=USER_AVATAR"
```
Expected: response JSON contains `"publicUrl": "https://<ref>.supabase.co/storage/v1/object/public/<bucket>/uploads/..."`.

Presigned upload:
```bash
curl "http://localhost:8080/api/v1/media/presigned-url?fileName=test.jpg&contentType=image/jpeg" \
  -H "Authorization: Bearer <token>"
```
Expected: response JSON contains `"publicUrl": "https://<ref>.supabase.co/..."`.

Open the `publicUrl` in a browser — the image must load without authentication.

- [ ] **Step 12: Commit**

```bash
git add backend/common/src/main/java/com/bidnow/common/dto/event/AvatarUploadedEvent.java \
        backend/media-service/src/main/resources/application.yml \
        backend/media-service/src/main/java/com/bidnow/media/dto/response/MediaUploadResponse.java \
        backend/media-service/src/main/java/com/bidnow/media/dto/response/PresignedUrlResponse.java \
        backend/media-service/src/main/java/com/bidnow/media/strategy/UploadEventStrategy.java \
        backend/media-service/src/main/java/com/bidnow/media/strategy/UploadEventStrategyFactory.java \
        backend/media-service/src/main/java/com/bidnow/media/strategy/impl/AvatarUploadEventStrategy.java \
        backend/media-service/src/main/java/com/bidnow/media/service/impl/MediaServiceImpl.java \
        backend/user-service/src/main/java/com/bidnow/user/kafka/UserKafkaConsumer.java
git commit -m "feat(media): compute and expose Supabase public URL at upload time"
```

---

## Task 2: Frontend — Use `publicUrl` directly, convert `useSecureImage` to sync

**Depends on:** Task 1 deployed and returning `publicUrl` in upload responses.

**Files:**
- Modify: `frontend/.env.local`
- Modify: `frontend/types/api/media.api.ts`
- Modify: `frontend/services/media.service.ts`
- Modify: `frontend/hooks/useSecureImage.ts`
- Modify: `frontend/app/(dashboard)/profile/page.tsx`
- Modify: `frontend/app/(dashboard)/seller/auctions/new/page.tsx`
- Modify: `frontend/app/(dashboard)/seller/auctions/[id]/manage/page.tsx`

**Interfaces:**
- Consumes: `MediaUploadResponse.publicUrl`, `PresignedUrlResponse.publicUrl` from Task 1
- `useSecureImage(value)` interface is **unchanged** — same import, same call, same return type — so the 5 display components need no edits

- [ ] **Step 1: Add Supabase env vars to `.env.local`**

Add to `frontend/.env.local`:

```
NEXT_PUBLIC_SUPABASE_URL=https://<your-project-ref>.supabase.co
NEXT_PUBLIC_SUPABASE_BUCKET=<your-bucket-name>
```

These are public env vars (readable by the browser). `NEXT_PUBLIC_SUPABASE_URL` is your Supabase project URL. `NEXT_PUBLIC_SUPABASE_BUCKET` is the name of the public bucket (e.g. `bidnow-media`).

- [ ] **Step 2: Update TypeScript types in `media.api.ts`**

Replace the entire file:

```typescript
export type MediaEntityType = 'USER_AVATAR' | 'AUCTION_ITEM';

export interface MediaUploadResponse {
  id: string;
  originalName: string;
  s3Key: string;
  publicUrl: string;
  contentType: string;
  fileSize: number;
  width: number;
  height: number;
  createdAt: string;
}

export interface PresignedUrlResponse {
  uploadUrl: string;
  s3Key: string;
  publicUrl: string;
  expiresAt: string;
}
```

- [ ] **Step 3: Remove `getDownloadUrl` from `media.service.ts`**

Delete the `getDownloadUrl` method entirely. The rest of the file is unchanged:

```typescript
import { apiFetch } from "@/lib/apiClient";
import type { MediaUploadResponse, MediaEntityType, PresignedUrlResponse } from '@/types/api/media.api';

export const mediaService = {
  uploadFile: async (
    file: File,
    entityType?: MediaEntityType,
    entityId?: string,
  ): Promise<MediaUploadResponse> => {
    const formData = new FormData();
    formData.append("file", file);
    if (entityType) formData.append("entityType", entityType);
    if (entityId) formData.append("entityId", entityId);

    const response = await apiFetch('/api/v1/media/upload', {
      method: 'POST',
      body: formData,
    });

    if (!response.ok) {
      const error = await response.json();
      throw error;
    }

    const json = await response.json();
    return json.data;
  },

  getPresignedUrl: async (
    fileName: string,
    contentType: string,
  ): Promise<PresignedUrlResponse> => {
    const params = new URLSearchParams({ fileName, contentType });
    const response = await apiFetch(`/api/v1/media/presigned-url?${params}`, {
      method: 'GET',
    });

    if (!response.ok) {
      const error = await response.json();
      throw error;
    }

    const json = await response.json();
    return json.data;
  },

  uploadToS3: async (uploadUrl: string, file: File): Promise<void> => {
    const response = await fetch(uploadUrl, {
      method: 'PUT',
      headers: { 'Content-Type': file.type },
      body: file,
    });

    if (!response.ok) {
      throw new Error(`Failed to upload to S3: ${response.statusText}`);
    }
  },
};
```

- [ ] **Step 4: Rewrite `useSecureImage` to be synchronous**

Replace the entire file with a sync implementation. The hook's signature and import path are identical so all 5 display components continue to work with zero changes:

```typescript
const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL!
const SUPABASE_BUCKET = process.env.NEXT_PUBLIC_SUPABASE_BUCKET!

export function useSecureImage(value?: string | null): string | undefined {
  if (!value) return undefined
  // Already a full URL (new records written after this change)
  if (value.startsWith('http')) return value
  // Legacy s3Key stored in DB before this change (e.g. "uploads/uuid/file.jpg")
  return `${SUPABASE_URL}/storage/v1/object/public/${SUPABASE_BUCKET}/${value}`
}
```

No `useState`, no `useEffect`, no imports needed. This handles both old records (s3Keys in DB) and new records (public URLs) transparently.

- [ ] **Step 5: Update `profile/page.tsx` to use `publicUrl` for avatar**

Find lines 83–92 (the avatar upload block). Change `uploadRes.s3Key` to `uploadRes.publicUrl`:

```typescript
const uploadRes = await mediaService.uploadFile(
  selectedFile,
  'USER_AVATAR',
)
const publicUrl = uploadRes.publicUrl

// Persist the new avatarUrl directly to the user-profile
await updateProfile({ avatarUrl: publicUrl })
```

- [ ] **Step 6: Update `new/page.tsx` to use `publicUrl` for auction images**

Find the image upload block inside `handleSubmit` (around line 148–158). Change `presigned.s3Key` to `presigned.publicUrl`:

```typescript
const uploadedUrls = await Promise.all(
  data.images.map(async (file) => {
    try {
      const presigned = await mediaService.getPresignedUrl(file.name, file.type)
      await mediaService.uploadToS3(presigned.uploadUrl, file)
      return presigned.publicUrl
    } catch (e) {
      console.error('Failed to upload image:', file.name, e)
      throw new Error(`Image upload failed for "${file.name}".`)
    }
  })
)
```

- [ ] **Step 7: Update `manage/page.tsx` to use `publicUrl` for auction images**

Find the image upload block inside `submitForm` (around line 123–133). Change `presigned.s3Key` to `presigned.publicUrl`:

```typescript
for (const file of images) {
  try {
    const presigned = await mediaService.getPresignedUrl(file.name, file.type)
    await mediaService.uploadToS3(presigned.uploadUrl, file)
    uploadedUrls.push(presigned.publicUrl)
  } catch (e) {
    console.error('Failed to upload image:', file.name, e)
    throw new Error('Image upload failed.')
  }
}
```

- [ ] **Step 8: Verify in the browser**

Run `npm run dev` in `frontend/`.

Check these flows:
1. **Browse auctions page** — auction cards show images without delay or flash (previously there was a loading state while the hook fetched the presigned URL; now images render immediately).
2. **Auction detail page** — gallery images load instantly.
3. **Profile page** — avatar loads instantly. Upload a new avatar; confirm it appears immediately after save.
4. **Seller auction row** — images in seller's auction list render without delay.
5. **Create auction** — upload images, submit; navigate to the new auction and confirm images are visible.

Open DevTools Network tab — there must be **zero** requests to `/api/v1/media/download`.

- [ ] **Step 9: Commit**

```bash
git add frontend/.env.local \
        frontend/types/api/media.api.ts \
        frontend/services/media.service.ts \
        frontend/hooks/useSecureImage.ts \
        "frontend/app/(dashboard)/profile/page.tsx" \
        "frontend/app/(dashboard)/seller/auctions/new/page.tsx" \
        "frontend/app/(dashboard)/seller/auctions/[id]/manage/page.tsx"
git commit -m "feat(frontend): use Supabase public URLs directly, remove async image resolution"
```

---

## Task 3: Backend — Remove presigned download infrastructure

**Depends on:** Task 2 deployed and verified (frontend no longer calls `/download`).

**Files:**
- Modify: `backend/media-service/src/main/java/com/bidnow/media/service/MediaService.java`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/service/impl/MediaServiceImpl.java`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/controller/MediaController.java`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/config/SecurityConfig.java`
- Modify: `backend/media-service/src/main/java/com/bidnow/media/config/S3Config.java`

- [ ] **Step 1: Remove `generateDownloadUrl` from `MediaService` interface**

Delete the entire `generateDownloadUrl` method declaration from `MediaService.java`.

- [ ] **Step 2: Remove `generateDownloadUrl` from `MediaServiceImpl`**

Delete the `generateDownloadUrl` method implementation from `MediaServiceImpl.java`.

- [ ] **Step 3: Remove the `/download` endpoint from `MediaController`**

Delete the entire `getDownloadUrl` method (the `GET /download` handler) from `MediaController.java`. Also remove the unused import for `GetMapping` if it's now unreferenced — check if `@GetMapping` is still used by `getPresignedUploadUrl`.

- [ ] **Step 4: Remove the `/download` permit rule from `SecurityConfig`**

Remove the line:
```java
.requestMatchers("/api/v1/media/download")
.permitAll()
```

The `securityFilterChain` method should now only have `SecurityConstants.PUBLIC_ENDPOINTS` as the permit-all matcher.

- [ ] **Step 5: Remove `S3Presigner` bean from `S3Config`**

Delete the entire `s3Presigner()` `@Bean` method from `S3Config.java`. Also remove these unused imports:
```java
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
```

- [ ] **Step 6: Remove `S3Presigner` from `MediaServiceImpl`**

Remove the `S3Presigner s3Presigner` field and its usage in `generatePresignedUrl` — wait, `generatePresignedUrl` (presigned PUT for uploads) still uses `S3Presigner`. **Do not remove the presigner from `generatePresignedUrl`.**

Re-check: `S3Presigner` is used in:
- `generatePresignedUrl` → presigned PUT for client-side uploads (keep)
- `generateDownloadUrl` → presigned GET for reading (removed in Step 2)

Since `S3Presigner` is still needed for presigned PUT uploads, **do not remove it from `S3Config` or `MediaServiceImpl`**. Revert Step 5 — only remove the `generateDownloadUrl` method.

Update Step 5: **Skip** — `S3Presigner` stays.

- [ ] **Step 7: Build `media-service`**

```bash
cd backend
mvn clean install -pl media-service -DskipTests
```

Expected: `BUILD SUCCESS`. If there are compilation errors about `S3Presigner` references, verify `generatePresignedUrl` still compiles correctly.

- [ ] **Step 8: Verify the endpoint is gone**

Start `media-service` and confirm the endpoint returns 404:

```bash
curl -i "http://localhost:8080/api/v1/media/download?s3Key=uploads/test.jpg" \
  -H "Authorization: Bearer <token>"
```

Expected: `404 Not Found` (or the gateway returns a routing error).

- [ ] **Step 9: Commit**

```bash
git add backend/media-service/src/main/java/com/bidnow/media/service/MediaService.java \
        backend/media-service/src/main/java/com/bidnow/media/service/impl/MediaServiceImpl.java \
        backend/media-service/src/main/java/com/bidnow/media/controller/MediaController.java \
        backend/media-service/src/main/java/com/bidnow/media/config/SecurityConfig.java
git commit -m "feat(media): remove presigned download endpoint, no longer needed with public bucket"
```
