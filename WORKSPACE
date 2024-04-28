load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

RULES_JVM_EXTERNAL_TAG = "5.1"

RULES_JVM_EXTERNAL_SHA = "8c3b207722e5f97f1c83311582a6c11df99226e65e2471086e296561e57cc954"

http_archive(
    name = "rules_jvm_external",
    sha256 = RULES_JVM_EXTERNAL_SHA,
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    url = "https://github.com/bazelbuild/rules_jvm_external/releases/download/%s/rules_jvm_external-%s.tar.gz" % (RULES_JVM_EXTERNAL_TAG, RULES_JVM_EXTERNAL_TAG),
)

load("@rules_jvm_external//:repositories.bzl", "rules_jvm_external_deps")

rules_jvm_external_deps()

load("@rules_jvm_external//:setup.bzl", "rules_jvm_external_setup")

rules_jvm_external_setup()

load("@rules_jvm_external//:defs.bzl", "maven_install")

http_archive(
    name = "rules_spring",
    sha256 = "87b337f95f9c09a2e5875f0bca533b050c9ccb8b0d2c92915e290520b79d0912",
    urls = [
        "https://github.com/salesforce/rules_spring/releases/download/2.3.2/rules-spring-2.3.2.zip",
    ],
)

maven_install(
    artifacts = [
        "org.postgresql:postgresql:42.7.3",
        "com.zaxxer:HikariCP:5.1.0",
        "org.springframework.boot:spring-boot:3.2.5",
        "org.springframework.boot:spring-boot-actuator:3.2.5",
        "org.springframework.boot:spring-boot-actuator-autoconfigure:3.2.5",
        "org.springframework.boot:spring-boot-loader:3.2.5",
        "org.springframework.boot:spring-boot-autoconfigure:3.2.5",
        "org.springframework.boot:spring-boot-starter-test:3.1.0",
        "org.springframework.boot:spring-boot-starter-jdbc:3.2.5",
        "org.springframework.boot:spring-boot-starter-web:3.2.5",
        "org.springframework.boot:spring-boot-starter-security:3.2.5",
        "org.springframework.boot:spring-boot-starter-oauth2-client:3.2.5",
        "org.springframework.boot:spring-boot-starter-validation:3.2.5",
        "com.h2database:h2:1.4.200",
        "com.fasterxml.jackson.core:jackson-databind:2.17.0",
        "org.flywaydb:flyway-database-postgresql:10.11.1",
        "org.apache.httpcomponents.client5:httpclient5:5.3.1",
        "org.apache.commons:commons-lang3:3.14.0",
        "com.rometools:rome:2.1.0",
        "org.slf4j:slf4j-simple:2.0.13",
        "junit:junit:4.13.2",
        "org.flywaydb:flyway-core:10.11.1",
        "jakarta.validation:jakarta.validation-api:3.0.2",
        "jakarta.servlet:jakarta.servlet-api:6.0.0",
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
    sha256 = "709cc0dcb51cf9028dd57c268066e5bc8f03a119ded410a13b5c3925d6e43c48",
    urls = ["https://github.com/bazelbuild/rules_nodejs/releases/download/5.8.4/rules_nodejs-5.8.4.tar.gz"],
)

load("@build_bazel_rules_nodejs//:repositories.bzl", "build_bazel_rules_nodejs_dependencies")

build_bazel_rules_nodejs_dependencies()

load("@build_bazel_rules_nodejs//:index.bzl", "node_repositories", "npm_install")

node_repositories(
    node_version = "18.2.0",
)

npm_install(
    name = "npm",
    npm_command = "install",
    package_json = "//client:package.json",
    package_lock_json = "//client:package-lock.json",
)

http_archive(
    name = "io_bazel_rules_docker",
    sha256 = "b1e80761a8a8243d03ebca8845e9cc1ba6c82ce7c5179ce2b295cd36f7e394bf",
    urls = ["https://github.com/bazelbuild/rules_docker/releases/download/v0.25.0/rules_docker-v0.25.0.tar.gz"],
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
    tag = "1.25.5-alpine",
)

container_pull(
    name = "jre_image",
    registry = "index.docker.io",
    repository = "eclipse-temurin",
    tag = "18-jre-alpine",
)

load(
    "@io_bazel_rules_docker//repositories:go_repositories.bzl",
    container_go_deps = "go_deps",
)

container_go_deps()
