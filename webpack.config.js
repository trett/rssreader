const path = require('path');
const webpack = require('webpack');
const UglifyJsPlugin = require('uglifyjs-webpack-plugin');

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
                test: /\.vue$/,
                loader: 'vue-loader',
                options: {
                    loaders: {
                        // Since sass-loader (weirdly) has SCSS as its default parse mode, we map
                        // the "scss" and "sass" values for the lang attribute to the right configs here.
                        // other preprocessors should work out of the box, no loader config like this necessary.
                        'scss': 'vue-style-loader!css-loader!sass-loader',
                        'sass': 'vue-style-loader!css-loader!sass-loader?indentedSyntax',
                    }
                    // other vue-loader options go here
                }
            },
            {
                test: /\.tsx?$/,
                loader: 'ts-loader',
                exclude: /node_modules/,
                options: {
                    appendTsSuffixTo: [/\.vue$/],
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
    devtool: '#eval-source-map',
    optimization: {}
};

if (process.env.NODE_ENV === 'production') {
    module.exports.devtool = '#none';
    module.exports.optimization.minimizer = (module.exports.optimization.minimizer || []).concat([
        new UglifyJsPlugin({
            cache: true,
            parallel: true,
            sourceMap: true,
            uglifyOptions: {
                compress: true,
                ecma: 6,
                mangle: true
            }
        }),
        new webpack.DefinePlugin({
            'process.env': {
                NODE_ENV: "'production'"
            }
        }),
        new webpack.LoaderOptionsPlugin({
            minimize: true
        })
    ])
}