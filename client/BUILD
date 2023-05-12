load("@npm//@bazel/typescript:index.bzl", "ts_project")
load("@io_bazel_rules_docker//container:container.bzl", "container_image")
load("@build_bazel_rules_nodejs//:index.bzl", "copy_to_bin")
load("@npm//http-server:index.bzl", "http_server")
load("@npm//webpack-cli:index.bzl", webpack = "webpack_cli")

ts_project(
    name = "project",
    srcs = glob(["src/**/*.ts"]),
    # allow_js = True,
    # source_map = True,
    deps = [
        "@npm//:node_modules",
    ],
)

copy_to_bin(
    name = "resources",
    srcs = ["src/index.html"],
)

webpack(
    name = "bundle",
    outs = ["dist"],
    args = [
        "./$(execpath src/application.js)",
        "--config",
        "$(execpath webpack.config.js)",
        "-o",
        "$@",
        "--env",
        "SERVER_URL=$(SERVER_URL)",
        "CLIENT_ID=$(CLIENT_ID)",
        "REDIRECT_URI=$(REDIRECT_URI)",
    ],
    data = [
        "css/style.css",
        "images/favicon.ico",
        "src/application.js",
        "src/index.html",
        "webpack.config.js",
        ":project",
        "@npm//:node_modules",
    ],
)

container_image(
    name = "image",
    base = "@nginx_image//image",
    cmd = [
        "nginx",
        "-g",
        "daemon off;",
    ],
    files = [":bundle"],
    ports = [
        "80/tcp",
    ],
    symlinks = {
        "/usr/share/nginx/html": "/dist",
    },
)

# Note, on Windows you need `--enable_runfiles`
http_server(
    name = "server",
    data = [
        ":bundle",
    ],
    templated_args = ["client/dist"],
)
