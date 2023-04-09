load("@rules_java//java:defs.bzl", "java_library")
load("@rules_spring//springboot:springboot.bzl", "springboot")
load("@io_bazel_rules_docker//container:container.bzl", "container_image")

java_library(
    name = "lib",
    srcs = glob(["src/main/java/**/*.java"]),
    resources = [
        "src/main/resources/application.properties",
        "src/main/resources/db/migration/V1_0_0__Initial.sql",
    ],
    visibility = ["//visibility:public"],
    runtime_deps = [
        "@maven//:com_zaxxer_HikariCP",
        "@maven//:org_flywaydb_flyway_core",
        "@maven//:org_slf4j_slf4j_simple",
    ],
    deps = [
        "@maven//:com_fasterxml_jackson_core_jackson_annotations",
        "@maven//:com_fasterxml_jackson_core_jackson_core",
        "@maven//:com_fasterxml_jackson_core_jackson_databind",
        "@maven//:com_fasterxml_jackson_datatype_jackson_datatype_jsr310",
        "@maven//:com_rometools_rome",
        "@maven//:javax_validation_validation_api",
        "@maven//:org_apache_commons_commons_lang3",
        "@maven//:org_apache_httpcomponents_httpclient",
        "@maven//:org_apache_tomcat_embed_tomcat_embed_core",
        "@maven//:org_postgresql_postgresql",
        "@maven//:org_slf4j_slf4j_api",
        "@maven//:org_springframework_boot_spring_boot_autoconfigure",
        "@maven//:org_springframework_boot_spring_boot_starter_jdbc",
        "@maven//:org_springframework_boot_spring_boot_starter_security",
        "@maven//:org_springframework_boot_spring_boot_starter_web",
        "@maven//:org_springframework_security_spring_security_config",
        "@maven//:org_springframework_security_spring_security_core",
        "@maven//:org_springframework_security_spring_security_web",
        "@maven//:org_springframework_spring_jdbc",
        "@maven//:org_springframework_spring_webmvc",
        "@rules_spring//springboot/import_bundles:springboot_required_deps",
    ],
)

java_test(
    name = "test",
    srcs = glob(["src/test/**/*.java"]),
    resources = glob(["src/test/resources/**"]),
    test_class = "ru.trett.rss.AllSpecs",
    jvm_flags = ["-Djava.security.manager=allow"],
    runtime_deps = [
        "@maven//:com_h2database_h2",
    ],
    deps = [
        ":lib",
        "@maven//:com_fasterxml_jackson_core_jackson_annotations",
        "@maven//:com_fasterxml_jackson_core_jackson_core",
        "@maven//:com_fasterxml_jackson_core_jackson_databind",
        "@maven//:com_fasterxml_jackson_datatype_jackson_datatype_jsr310",
        "@maven//:com_rometools_rome",
        "@maven//:javax_validation_validation_api",
        "@maven//:junit_junit",
        "@maven//:org_apache_commons_commons_lang3",
        "@maven//:org_apache_httpcomponents_httpclient",
        "@maven//:org_apache_tomcat_embed_tomcat_embed_core",
        "@maven//:org_postgresql_postgresql",
        "@maven//:org_slf4j_slf4j_api",
        "@maven//:org_springframework_boot_spring_boot_autoconfigure",
        "@maven//:org_springframework_boot_spring_boot_starter_jdbc",
        "@maven//:org_springframework_boot_spring_boot_starter_security",
        "@maven//:org_springframework_boot_spring_boot_starter_web",
        "@maven//:org_springframework_security_spring_security_config",
        "@maven//:org_springframework_security_spring_security_core",
        "@maven//:org_springframework_security_spring_security_web",
        "@maven//:org_springframework_spring_jdbc",
        "@maven//:org_springframework_spring_test",
        "@maven//:org_springframework_spring_webmvc",
        "@rules_spring//springboot/import_bundles:springboot_required_deps",
    ],
)

springboot(
    name = "app",
    boot_app_class = "ru.trett.rss.RssApplication",
    java_library = ":lib",
)

container_image(
    name = "image",
    base = "@jre_image//image",
    cmd = [
        "java",
        "-jar",
        "app.jar",
    ],
    files = [":app"],
    ports = ["8080/tcp"],
)
