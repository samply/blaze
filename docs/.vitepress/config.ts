import { defineConfig } from 'vitepress'

// https://vitepress.dev/reference/site-config
export default defineConfig({
  title: "Blaze",
  description: "Blaze FHIR Server Documentation",
  themeConfig: {
    nav: [
      { text: 'Home', link: '/' },
    ],
    outline: false,
    sidebar: [
      {
        text: 'Navigation',
        items: [
          { text: 'Home', link: '/README' },
          { text: 'Deployment', link: '/deployment/README' },
          { text: 'FHIR RESTful API', link: '/api' },
          { text: 'Importing Data', link: '/importing-data' },
          { text: 'Sync Data', link: '/data-sync' },
          { text: 'Conformance', link: '/conformance' },
          { text: 'Performance', link: '/performance' },
          { text: 'Tuning Guide', link: '/tuning-guide' },
          { text: 'Tooling', link: '/tooling' },
          { text: 'CQL Queries', link: '/cql-queries' },
          { text: 'Authentication', link: '/authentication' },
          { text: 'Architecture', link: '/architecture' },
          { text: 'Implementation', link: '/implementation/README' },
        ]
      }
    ],

    socialLinks: [
      { icon: 'github', link: 'https://github.com/samply/blaze' }
    ]
  }
})
