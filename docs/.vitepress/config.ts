import {defineConfig} from 'vitepress'

export default defineConfig({
    title: "Blaze",
    description: "Blaze FHIR Server Documentation",

    base: process.env.DOCS_BASE || "",
    lastUpdated: true,

    themeConfig: {
        logo: '/blaze-logo.svg',
        siteTitle: false,

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
            copyright: 'Copyright 2019 - 2025 The Samply Community • Circuit icons created by <a href="https://www.flaticon.com/free-icons/circuit" title="circuit icons">Eucalyp - Flaticon</a>',
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
                text: 'FHIR API',
                items: [
                    {text: 'Overview', link: '/api'},
                    {
                        text: 'Interactions',
                        items: [
                            {
                                text: 'Instance Level',
                                items: [
                                    {text: 'Read', link: '/api/interaction/read'},
                                    {text: 'Versioned Read', link: '/api/interaction/vread'},
                                    {text: 'Update', link: '/api/interaction/update'},
                                    {text: 'Delete', link: '/api/interaction/delete'},
                                    {text: 'Delete History', link: '/api/interaction/delete-history'},
                                    {text: 'History', link: '/api/interaction/history-instance'},
                                ],
                            },
                            {
                                text: 'Type Level',
                                items: [
                                    {text: 'Create', link: '/api/interaction/create'},
                                    {text: 'Search', link: '/api/interaction/search-type'},
                                    {text: 'History', link: '/api/interaction/history-type'},
                                ],
                            },
                            {
                                text: 'System Level',
                                items: [
                                    {text: 'Capabilities', link: '/api/interaction/capabilities'},
                                    {text: 'Transaction', link: '/api/interaction/transaction'},
                                    {text: 'Batch', link: '/api/interaction/batch'},
                                    {text: 'Search', link: '/api/interaction/search-system'},
                                    {text: 'History', link: '/api/interaction/history-system'},
                                ],
                            },
                        ],
                    },
                    {
                        text: 'Operations',
                        items: [
                            {
                                text: 'System',
                                items: [
                                    {text: '$compact', link: '/api/operation/compact'},
                                ],
                            },
                            {
                                text: 'CodeSystem',
                                items: [
                                    {text: '$validate-code', link: '/api/operation/code-system-validate-code'},
                                ],
                            },
                            {
                                text: 'Measure',
                                items: [
                                    {text: '$evaluate-measure', link: '/api/operation/measure-evaluate-measure'},
                                ],
                            },
                            {
                                text: 'Patient',
                                items: [
                                    {text: '$everything', link: '/api/operation/patient-everything'},
                                    {text: '$purge', link: '/api/operation/patient-purge'},
                                ],
                            },
                            {
                                text: 'ValueSet',
                                items: [
                                    {text: '$expand', link: '/api/operation/value-set-expand'},
                                    {text: '$validate-code', link: '/api/operation/value-set-validate-code'},
                                ],
                            },
                        ],
                    },
                ],
            },
            {
                text: 'CQL Queries',
                items: [
                    {text: 'via blazectl', link: '/cql-queries/blazectl'},
                    {text: 'via API', link: '/cql-queries/api'},
                    {text: 'Conformance', link: '/conformance/cql'},
                ],
            },
            {
                text: 'Terminology Service',
                items: [
                    {text: 'Overview', link: '/terminology-service'},
                    {text: 'LOINC', link: '/terminology-service/loinc'},
                    {text: 'SNOMED CT', link: '/terminology-service/snomed-ct'},
                    {text: 'UCUM', link: '/terminology-service/ucum'},
                ],
            },
            {
                text: 'Usage',
                items: [
                    {text: 'Frontend', link: '/frontend'},
                    {text: 'Importing Data', link: '/importing-data'},
                    {text: 'Sync Data', link: '/data-sync'},
                    {text: 'Conformance', link: '/conformance'},
                    {text: 'Tooling', link: '/tooling'},
                ],
            },
            {
                text: 'Deployment',
                link: '/deployment/README',
                items: [
                    {text: 'Full Standalone', link: '/deployment/full-standalone'},
                    {text: 'Standalone Backend Only', link: '/deployment/standalone-backend'},
                    {text: 'Distributed Backend Only', link: '/deployment/distributed-backend'},
                    {text: 'Configuration', link: '/deployment/environment-variables'},
                    {text: 'Authentication', link: '/authentication'},
                    {text: 'Monitoring', link: '/monitoring'},
                    {text: 'Tuning Guide', link: '/tuning-guide'},
                ],
            },
            {
                text: 'Deep Dive',
                items: [
                    {
                        text: 'Performance',
                        items: [
                            {text: 'CQL', link: '/performance/cql'},
                            {text: 'FHIR Search', link: '/performance/fhir-search'},
                            {text: 'Import', link: '/performance/import'},
                        ]
                    },
                    {text: 'Architecture', link: '/architecture'},
                    {
                        text: 'Implementation',
                        items: [
                            { text: 'Database', link: '/implementation/database' },
                            { text: 'FHIR Data Model', link: '/implementation/fhir-data-model' },
                            { text: 'CQL', link: '/implementation/cql' },
                            { text: 'Frontend', link: '/implementation/frontend' },
                        ]
                    },
                ],
            }
        ],
    }
})
