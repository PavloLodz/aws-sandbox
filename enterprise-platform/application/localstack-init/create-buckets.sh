#!/bin/bash
# Runs automatically when LocalStack is ready.
# Creates the S3 bucket used by the app in local Docker Compose.

BUCKET="${S3_BUCKET:-local-bucket}"

echo "Creating S3 bucket: $BUCKET"
awslocal s3 mb "s3://$BUCKET"
echo "Bucket $BUCKET created successfully."
