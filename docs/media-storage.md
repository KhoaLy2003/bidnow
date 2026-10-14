# Media Storage (S3 + CloudFront)

Images are uploaded by **media-service** to a **private S3 bucket** and read by browsers through **CloudFront**.

```
Browser ──upload──▶ media-service ──PutObject──▶ S3 (private)
Browser ◀──GET──── CloudFront (OAC) ◀──────────── S3
```

- media-service stores/returns `publicUrl = {CDN_BASE_URL}/{s3Key}` (`MediaServiceImpl.buildPublicUrl`). Downstream services (auction, user) persist that full URL.
- Keys are `uploads/{ownerId}/{uuid}.{ext}` (server upload) or `uploads/{uuid}.{ext}` (presigned PUT). They are never overwritten, so objects are uploaded with `Cache-Control: public, max-age=31536000, immutable`.
- The frontend uses full URLs as-is; a bare key is resolved against `NEXT_PUBLIC_CDN_URL` (`frontend/lib/image-utils.ts`).

## Configuration

| Variable | Where | Notes |
|---|---|---|
| `AWS_REGION`, `AWS_BUCKET_NAME` | media-service | |
| `CDN_BASE_URL` | media-service | CloudFront domain, e.g. `https://d1234abcd.cloudfront.net` |
| `NEXT_PUBLIC_CDN_URL` | frontend | Same value as `CDN_BASE_URL` |
| `AWS_PROFILE` or `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | local only | Read by the SDK default credentials chain |

No keys are configured in the application. `S3Config` uses `DefaultCredentialsProvider`: env vars / profile locally, the ECS task / EKS service-account / EC2 instance role on AWS.

## AWS setup

1. **Bucket:** private, Block Public Access ON, per environment (e.g. `bidnow-media-dev`).
2. **CloudFront:** distribution with the bucket as origin using **Origin Access Control**; viewer protocol redirect to HTTPS.
3. **Bucket policy** (allow only the distribution to read):

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "AllowCloudFrontRead",
    "Effect": "Allow",
    "Principal": { "Service": "cloudfront.amazonaws.com" },
    "Action": "s3:GetObject",
    "Resource": "arn:aws:s3:::<bucket>/*",
    "Condition": { "StringEquals": { "AWS:SourceArn": "arn:aws:cloudfront::<account-id>:distribution/<distribution-id>" } }
  }]
}
```

4. **media-service identity** (dev IAM user locally; a role on AWS). The code only writes objects (`putObject` and presigned PUT), so this is enough:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": "s3:PutObject",
    "Resource": "arn:aws:s3:::<bucket>/uploads/*"
  }]
}
```

Add `s3:DeleteObject` only if delete is introduced; add `kms:GenerateDataKey` if the bucket uses SSE-KMS.

5. **Bucket CORS** (browser presigned PUT):

```json
[{
  "AllowedOrigins": ["http://localhost:3000"],
  "AllowedMethods": ["PUT"],
  "AllowedHeaders": ["*"],
  "MaxAgeSeconds": 3000
}]
```

## Migrating from Supabase

Existing rows in auction-service and user-service store `https://<supabase>/storage/v1/object/public/<bucket>/<key>`. Copy the objects to the new bucket (same keys), then rewrite the host prefix to `{CDN_BASE_URL}/` in each service's own database (no cross-database queries). Until then the old URLs keep rendering, because the frontend passes full URLs through.
