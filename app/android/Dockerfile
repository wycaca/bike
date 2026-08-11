FROM m.daocloud.io/docker.io/library/gradle:8.11.1-jdk17 AS gradle-runtime

FROM dockerproxy.net/mobiledevops/android-sdk-image:36.1.0 AS builder
COPY --from=gradle-runtime /opt/gradle /opt/gradle
ENV PATH="/opt/gradle/bin:${PATH}"
WORKDIR /workspace
COPY . .
ARG BIKE_DEBUG_WEB_APP_URL=http://192.168.50.204:8082
RUN --mount=type=cache,target=/root/.gradle gradle --no-daemon testDebugUnitTest assembleDebug -PBIKE_DEBUG_WEB_APP_URL=${BIKE_DEBUG_WEB_APP_URL}

FROM scratch AS artifact
COPY --from=builder /workspace/app/build/outputs/apk/debug/app-debug.apk /bike-operations-debug.apk
