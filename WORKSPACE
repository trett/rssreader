load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

RULES_JVM_EXTERNAL_TAG = "4.2"

RULES_JVM_EXTERNAL_SHA = "cd1a77b7b02e8e008439ca76fd34f5b07aecb8c752961f9640dea15e9e5ba1ca"

http_archive(
    name = "rules_jvm_external",
    sha256 = RULES_JVM_EXTERNAL_SHA,
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

load("@rules_jvm_external//:defs.bzl", "maven_install")

http_archive(
    name = "rules_spring",
    sha256 = "9385652bb92d365675d1ca7c963672a8091dc5940a9e307104d3c92e7a789c8e",
    urls = [
        "https://github.com/salesforce/rules_spring/releases/download/2.1.4/rules-spring-2.1.4.zip",
    ],
)

maven_install(
    artifacts = [
        "org.postgresql:postgresql:42.3.3",
        "com.zaxxer:HikariCP:5.0.1",
        "org.springframework.boot:spring-boot:2.3.12.RELEASE",
        "org.springframework.boot:spring-boot-actuator:2.3.12.RELEASE",
        "org.springframework.boot:spring-boot-actuator-autoconfigure:2.3.12.RELEASE",
        "org.springframework.boot:spring-boot-loader:2.3.12.RELEASE",
        "org.springframework.boot:spring-boot-autoconfigure:2.3.12.RELEASE",
        "org.springframework.boot:spring-boot-starter-test:2.3.12.RELEASE",
        "org.springframework.boot:spring-boot-starter-jdbc:2.3.12.RELEASE",
        "org.springframework.boot:spring-boot-starter-web:2.3.12.RELEASE",
        "org.springframework.boot:spring-boot-starter-security:2.3.12.RELEASE",
        "com.h2database:h2:1.4.200",
        "com.fasterxml.jackson.core:jackson-databind:2.10.0",
        "org.apache.httpcomponents:httpclient:4.5.13",
        "org.apache.commons:commons-lang3:3.9",
        "com.rometools:rome:1.12.0",
        "org.slf4j:slf4j-simple:2.0.0-alpha6",
        "junit:junit:4.12",
        "org.flywaydb:flyway-core:6.5.7",
        "javax.validation:validation-api:2.0.1.Final",
    ],
    excluded_artifacts = [
        "ch.qos.logback:logback-classic",
    ],
    fetch_sources = True,
    maven_install_json = "//:maven_install.json",
    repositories = [
        "https://repo1.maven.org/maven2",
    ],
    version_conflict_policy = "pinned",
)

load("@maven//:defs.bzl", "pinned_maven_install")

pinned_maven_install()

http_archive(
    name = "build_bazel_rules_nodejs",
    sha256 = "c077680a307eb88f3e62b0b662c2e9c6315319385bc8c637a861ffdbed8ca247",
    urls = ["https://github.com/bazelbuild/rules_nodejs/releases/download/5.1.0/rules_nodejs-5.1.0.tar.gz"],
)

load("@build_bazel_rules_nodejs//:repositories.bzl", "build_bazel_rules_nodejs_dependencies")

build_bazel_rules_nodejs_dependencies()

load("@build_bazel_rules_nodejs//:index.bzl", "node_repositories", "npm_install")

node_repositories(
    node_version = "17.3.0",
)

npm_install(
    name = "npm",
    npm_command = "install",
    package_json = "//client:package.json",
    package_lock_json = "//client:package-lock.json",
)

http_archive(
    name = "io_bazel_rules_docker",
    sha256 = "59536e6ae64359b716ba9c46c39183403b01eabfbd57578e84398b4829ca499a",
    strip_prefix = "rules_docker-0.22.0",
    urls = ["https://github.com/bazelbuild/rules_docker/releases/download/v0.22.0/rules_docker-v0.22.0.tar.gz"],
)

load(
    "@io_bazel_rules_docker//repositories:repositories.bzl",
    container_repositories = "repositories",
)

container_repositories()

load("@io_bazel_rules_docker//container:pull.bzl", "container_pull")

container_pull(
    name = "nginx_image",
    registry = "index.docker.io",
    repository = "nginx",
    tag = "1.21.6-alpine",
)

container_pull(
    name = "jre_image",
    registry = "index.docker.io",
    repository = "eclipse-temurin",
    tag = "17-jre-alpine",
)

load(
    "@io_bazel_rules_docker//repositories:go_repositories.bzl",
    container_go_deps = "go_deps",
)

container_go_deps()
