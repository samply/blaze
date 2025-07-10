import js from '@eslint/js';
import ts from 'typescript-eslint';
import svelte from 'eslint-plugin-svelte';
import prettier from 'eslint-config-prettier/flat';
import globals from 'globals';

export default ts.config(
  js.configs.recommended,
  ...ts.configs.recommended,
  ...svelte.configs['flat/recommended'],
  prettier,
  ...svelte.configs['flat/prettier'],
  {
    languageOptions: {
      globals: {
        ...globals.browser,
        ...globals.node
      }
    }
  },
  {
    files: ['**/*.svelte'],

    languageOptions: {
      parserOptions: {
        parser: ts.parser
      }
    }
  },
  {
    ignores: ['build/', '.svelte-kit/', 'dist/']
  },
  {
    rules: {
      'svelte/no-useless-mustaches': 'warn',
      'svelte/require-each-key': 'warn'
    }
  }
);
