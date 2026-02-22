# Deployment Guide for Google Cloud Run

This guide outlines the steps to deploy the RSS Reader application to Google Cloud Run, including setting up the PostgreSQL database and background update tasks.

## Prerequisites

*   **Google Cloud Platform Account** with billing enabled.
*   **gcloud CLI** installed and authenticated.
*   **Existing Docker Image** hosted in Google Container Registry (GCR) or Artifact Registry.

## 1. Environment Setup

Define the following environment variables for your deployment.

```bash
export PROJECT_ID="your-gcp-project-id"
export REGION="us-central1" # Or your preferred region
export IMAGE_URL="docker.pkg.dev/your-gcp-project-id/your-repo/your-image:latest" 
export DB_INSTANCE_NAME="rss-postgres"
export DB_NAME="rss"
export DB_USER="rss_user"
export DB_PASSWORD="your-secure-password"
export SERVICE_NAME="rss-reader"
export JOB_TOKEN="your-secret-job-token" # Generate a strong random string
export JWT_SECRET="your-secure-jwt-secret" # Generate a strong random string for token signing
export OAUTH_CLIENT_ID="your-google-oauth-client-id"
export OAUTH_CLIENT_SECRET="your-google-oauth-client-secret"
export GOOGLE_API_KEY="your-google-gemini-api-key"
```

## 2. Infrastructure Setup

### Create Cloud SQL Instance

Create a PostgreSQL instance (if you haven't already).

```bash
gcloud sql instances create $DB_INSTANCE_NAME \
    --database-version=POSTGRES_15 \
    --cpu=1 \
    --memory=3840MiB \
    --region=$REGION
```

Create the database and user.

```bash
gcloud sql databases create $DB_NAME --instance=$DB_INSTANCE_NAME

gcloud sql users create $DB_USER \
    --instance=$DB_INSTANCE_NAME \
    --password=$DB_PASSWORD
```

## 3. Configuration

### Service Definition
Create a `service.yaml` file with the following content. This defines the Cloud Run service.

**Important:** Replace placeholders (like `YOUR_PROJECT_ID`, `YOUR_IMAGE_URL`) with your actual values or use `envsubst`.

```yaml
apiVersion: serving.knative.dev/v1
kind: Service
metadata:
  name: rss-reader
  annotations:
    run.googleapis.com/maxScale: '1'
    run.googleapis.com/launch-stage: BETA
spec:
  template:
    metadata:
      annotations:
        run.googleapis.com/cloudsql-instances: ${PROJECT_ID}:${REGION}:${DB_INSTANCE_NAME}
        run.googleapis.com/execution-environment: gen1
        run.googleapis.com/startup-cpu-boost: 'true'
    spec:
      containers:
      - image: IMAGE_URL
        name: rss-reader-app
        ports:
          - containerPort: 8080
        env:
        - name: DATASOURCE_URL
          value: "jdbc:postgresql:///rss?cloudSqlInstance=$PROJECT_ID:$REGION:$DB_INSTANCE_NAME&socketFactory=com.google.cloud.sql.postgres.SocketFactory&user=$DB_USER&password=$DB_PASSWORD"
        - name: CLIENT_ID
          value: "$OAUTH_CLIENT_ID"
        - name: CLIENT_SECRET
          value: "$OAUTH_CLIENT_SECRET"
        - name: GOOGLE_API_KEY
          value: "$GOOGLE_API_KEY"
        - name: JOB_TOKEN
          value: "$JOB_TOKEN"
        - name: JWT_SECRET
          value: "$JWT_SECRET"
        - name: SERVER_URL
          value: "https://rss-reader-PROJECT_ID.REGION.run.app" # Update after first deploy if needed
        - name: CORS_URL
          value: "https://rss-reader-PROJECT_ID.REGION.run.app" # Update after first deploy if needed
        resources:
          limits:
            cpu: 1000m
            memory: 512Mi
        startupProbe:
          failureThreshold: 1
          periodSeconds: 240
          tcpSocket:
            port: 8080
          timeoutSeconds: 240
      timeoutSeconds: 300
```

## 4. Deploy to Cloud Run

Deploy using the `service.yaml`. 

```bash
envsubst < service.yaml | gcloud run services replace - --region=$REGION
```

## 5. Configure Cloud Scheduler

Set up a job to trigger the feed update every 10 minutes.

```bash
# Get the Service URL
SERVICE_URL=$(gcloud run services describe $SERVICE_NAME --platform managed --region $REGION --format "value(status.url)")

gcloud scheduler jobs create http rss-update-job \
    --schedule="*/10 * * * *" \
    --uri="$SERVICE_URL/api/jobs/update" \
    --http-method=POST \
    --headers="Authorization=Bearer $JOB_TOKEN" \
    --location=$REGION \
    --description="Trigger RSS feed updates"
```

## 6. Database Schema

The database schema should be applied manually using the `db/init.sql` file provided in the resources.
