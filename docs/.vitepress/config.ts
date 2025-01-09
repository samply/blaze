import {defineConfig} from 'vitepress'

export default defineConfig({
    title: "Blaze",
    description: "Blaze FHIR Server Documentation",

    base: process.env.DOCS_BASE || "",
    lastUpdated: true,

    themeConfig: {
        outline: false,

        editLink: {
            pattern: 'https://github.com/samply/blaze/edit/master/docs/:path',
            text: 'Edit this page on GitHub'
        },

        socialLinks: [
            {icon: 'github', link: 'https://github.com/samply/blaze'}
        ],

        footer: {
            message: 'Released under the <a href="https://www.apache.org/licenses/LICENSE-2.0">Apache License 2.0</a>',
            copyright: 'Copyright 2019 - 2024 The Samply Community • Circuit icons created by <a href="https://www.flaticon.com/free-icons/circuit" title="circuit icons">Eucalyp - Flaticon</a>',
        },

        nav: [
            {text: 'Home', link: '/'},
            {
                text: "v0.31.0",
                items: [
                    {
                        text: 'Changelog',
                        link: 'https://github.com/samply/blaze/blob/master/CHANGELOG.md',
                    },
                    {
                        text: 'Development',
                        link: 'https://github.com/samply/blaze/blob/master/DEVELOPMENT.md',
                    },
                ]
            }
        ],

        sidebar: [
            {
                items: [
                    {text: "Overview", link: "/README"},
                ]
            },
            {
                text: 'Deployment',
                link: '/deployment/README',
                items: [
                    {text: 'Using Docker', link: '/deployment/docker-deployment.md'},
                    {text: 'Using JVM', link: '/deployment/manual-deployment.md'},
                    {text: 'Distributed', link: '/deployment/distributed.md'},
                    {text: 'Configuration', link: '/deployment/environment-variables.md'},
                    {text: 'Authentication', link: '/authentication'},
                ],
            },
            {
                text: 'Usage',
                items: [
                    {text: 'Conformance', link: '/conformance'},
                    {text: 'FHIR RESTful API', link: '/api'},
                    {text: 'Importing Data', link: '/importing-data'},
                    {text: 'Sync Data', link: '/data-sync'},
                    {text: 'CQL Queries', link: '/cql-queries'},
                    {text: 'Monitoring', link: '/monitoring'},
                    {text: 'Tooling', link: '/tooling'},
                ],
            },
            {
                text: 'Deep Dive',
                items: [
                    {text: 'Performance', link: '/performance'},
                    {text: 'Tuning Guide', link: '/tuning-guide'},
                    {text: 'Architecture', link: '/architecture'},
                    {text: 'Implementation', link: '/implementation/README'},
                ],
            }
        ],
    }
})
