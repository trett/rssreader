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

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

http_archive(
    name = "rules_oci",
    sha256 = "acbf8f40e062f707f8754e914dcb0013803c6e5e3679d3e05b571a9f5c7e0b43",
    strip_prefix = "rules_oci-2.0.1",
    url = "https://github.com/bazel-contrib/rules_oci/releases/download/v2.0.1/rules_oci-v2.0.1.tar.gz",
)

load("@rules_oci//oci:dependencies.bzl", "rules_oci_dependencies")

rules_oci_dependencies()

load("@rules_oci//oci:repositories.bzl", "oci_register_toolchains")

oci_register_toolchains(name = "oci")

load("@rules_oci//oci:pull.bzl", "oci_pull")

oci_pull(
    name = "distroless_java",
    digest = "sha256:161a1d97d592b3f1919801578c3a47c8e932071168a96267698f4b669c24c76d",
    image = "gcr.io/distroless/java17",
)

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
http_archive(
    name = "rules_pkg",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_pkg/releases/download/1.0.1/rules_pkg-1.0.1.tar.gz",
        "https://github.com/bazelbuild/rules_pkg/releases/download/1.0.1/rules_pkg-1.0.1.tar.gz",
    ],
    sha256 = "d20c951960ed77cb7b341c2a59488534e494d5ad1d30c4818c736d57772a9fef",
)
load("@rules_pkg//:deps.bzl", "rules_pkg_dependencies")
rules_pkg_dependencies()

