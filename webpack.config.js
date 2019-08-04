const path = require('path')
const webpack = require('webpack')
const UglifyJsPlugin = require('uglifyjs-webpack-plugin')
const TSLintPlugin = require('tslint-webpack-plugin');

module.exports = {
    entry: `./src/main/resources/static/src/application.ts`,
    output: {
        path: path.resolve(__dirname, './src/main/resources/static/dist'),
        publicPath: `./src/main/resources/static/dist`,
        filename: 'application.js'
    },
    mode: 'production',
    module: {
        rules: [
            {
                test: /\.s(c|a)ss$/,
                use: [
                    'vue-style-loader',
                    'css-loader',
                    {
                        loader: 'sass-loader',
                        options: {
                            implementation: require('sass'),
                            fiber: require('fibers'),
                            indentedSyntax: true // optional
                        }
                    }
                ]
            },
            {
                test: /\.css$/i,
                use: ['style-loader', 'css-loader']
            },
            {
                test: /\.(png|woff|woff2|eot|ttf|svg)$/,
                loader: 'url-loader?limit=100000'
            },
            {
                test: /\.tsx?$/,
                loader: 'ts-loader',
                exclude: /node_modules/,
                options: {
                    appendTsSuffixTo: [/\.vue$/]
                }
            },
            {
                test: /\.(png|jpg|gif|svg)$/,
                loader: 'file-loader',
                options: {
                    name: '[name].[ext]?[hash]'
                }
            }
        ]
    },
    resolve: {
        extensions: ['.ts', '.js', '.vue', '.json'],
        alias: {
            'vue$': 'vue/dist/vue.esm.js'
        }
    },
    devServer: {
        historyApiFallback: true,
        noInfo: true
    },
    performance: {
        hints: false
    },
    plugins: [
        new TSLintPlugin({
            files: ['./src/**/*.ts']
        })
    ],
    devtool: '#eval-source-map',
    optimization: {}
}

if (process.env.NODE_ENV === 'production') {
    module.exports.devtool = '#none'
    module.exports.optimization.minimizer = (module.exports.optimization.minimizer ||
    []).concat([
        new UglifyJsPlugin({
            cache: true,
            parallel: true,
            uglifyOptions: {
                compress: true,
                ecma: 6,
                mangle: true,
                output: {
                    comments: false
                }
            }
        }),
        new webpack.DefinePlugin({
            'process.env': {
                NODE_ENV: '\'production\''
            }
        }),
        new webpack.LoaderOptionsPlugin({
            minimize: true
        })
    ])
}
