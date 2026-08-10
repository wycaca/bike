FROM m.daocloud.io/docker.io/library/gradle:8.11.1-jdk17 AS gradle-runtime

FROM dockerproxy.net/mobiledevops/android-sdk-image:36.1.0 AS builder
COPY --from=gradle-runtime /opt/gradle /opt/gradle
ENV PATH="/opt/gradle/bin:${PATH}"
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle gradle --no-daemon testDebugUnitTest assembleDebug

FROM scratch AS artifact
COPY --from=builder /workspace/app/build/outputs/apk/debug/app-debug.apk /bike-operations-debug.apk
