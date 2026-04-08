FROM ubuntu:24.04

ARG JDK_VERSION=21
ARG ANDROID_SDK_VERSION=36
ARG BUILD_TOOLS_VERSION=36.0.0
ARG CMDLINE_TOOLS_VERSION=13114758

ENV DEBIAN_FRONTEND=noninteractive
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-${JDK_VERSION}-jdk-headless \
    wget \
    unzip \
    git \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-${JDK_VERSION}-openjdk-amd64

RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    wget -q "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

RUN yes | sdkmanager --licenses > /dev/null 2>&1 && \
    sdkmanager \
    "platforms;android-${ANDROID_SDK_VERSION}" \
    "build-tools;${BUILD_TOOLS_VERSION}" \
    "platform-tools"

WORKDIR /app
COPY . .

RUN chmod +x ./gradlew

RUN ./gradlew assembleDebug --no-daemon
