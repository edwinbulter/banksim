// @ts-check
const eslint = require('@eslint/js');
const { defineConfig } = require('eslint/config');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

module.exports = defineConfig([
  {
    // Gegenereerd uit openapi.yaml (npm run generate:api)
    ignores: ['src/app/api/**'],
  },
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.recommended,
      tseslint.configs.stylistic,
      angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      // Geld nooit als floating point (TO §5): bedragen gaan via big.js (src/app/shared/bedrag.ts).
      'no-restricted-globals': ['error',
        { name: 'parseFloat', message: 'Gebruik big.js via shared/bedrag.ts' },
        { name: 'Number', message: 'Gebruik big.js via shared/bedrag.ts' }],
      'no-restricted-properties': ['error',
        { object: 'Number', property: 'parseFloat', message: 'Gebruik big.js via shared/bedrag.ts' }],
      '@angular-eslint/directive-selector': [
        'error',
        {
          type: 'attribute',
          prefix: 'app',
          style: 'camelCase',
        },
      ],
      '@angular-eslint/component-selector': [
        'error',
        {
          type: 'element',
          prefix: 'app',
          style: 'kebab-case',
        },
      ],
    },
  },
  {
    files: ['**/*.html'],
    extends: [angular.configs.templateRecommended, angular.configs.templateAccessibility],
    rules: {},
  },
]);
