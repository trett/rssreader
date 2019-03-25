const webpackConfig = require('./webpack.config.js')

module.exports = function (grunt) {
    grunt.initConfig({
        webpack: {
            options: {
                stats: !process.env.NODE_ENV || process.env.NODE_ENV === 'development'
            },
            prod: webpackConfig,
            dev: webpackConfig
        }
    })

    grunt.loadNpmTasks('grunt-webpack')
    // Default task(s).
    grunt.registerTask('default', ['webpack'])
}
