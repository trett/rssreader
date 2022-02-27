const path = require('path')
const HtmlWebpackPlugin = require('html-webpack-plugin')
const CopyPlugin = require("copy-webpack-plugin")

module.exports = (env, argv) => ({
    entry: '.src/application.ts',
    output: {
        path: path.resolve(__dirname, `dist`),
        filename: 'application-[fullhash:6].js',
        clean: true
    },
    mode: 'production',
    module: {
        rules: [
            {
                test: /\.css$/i,
                use: ['style-loader', 'css-loader'],
            },
            {
                test: /\.(png|woff|woff2|eot|ttf|svg)$/,
                use: [{
                    loader: 'url-loader',
                    options: {
                        limit: 100000
                    }
                }]
            },
        ],
    },
    resolve: {
        alias: {
            'vue$': 'vue/dist/vue.esm',
        },
    },
    resolveLoader: {
        modules: ['client/node_modules'],
      },
    plugins: [
        new HtmlWebpackPlugin({
            template: 'client/src/index.html'
        }),
        new CopyPlugin({
              patterns: [
                { from: "client/css", to: "css/" },
            ],
        })
    ],
});
