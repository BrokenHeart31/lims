// ESLint 9 扁平配置（T-004）
// 与 AGENTS.md 第 5 章对齐：Vue3 Composition API + TS strict、禁 any、生产代码禁 console.log
import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import pluginVue from 'eslint-plugin-vue'
import vueParser from 'vue-eslint-parser'
import globals from 'globals'

export default tseslint.config(
  {
    // 全局忽略目录
    ignores: ['dist/**', 'node_modules/**', 'coverage/**'],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    // Vue SFC 使用专用解析器，script 部分再交给 TypeScript parser
    files: ['**/*.vue'],
    languageOptions: {
      parser: vueParser,
      parserOptions: {
        parser: tseslint.parser,
        ecmaVersion: 'latest',
        sourceType: 'module',
        extraFileExtensions: ['.vue'],
        globals: {
          ...globals.browser,
        },
      },
    },
  },
  {
    files: ['**/*.{ts,vue}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.browser,
      },
    },
    rules: {
      // 生产代码禁止 console.log（与 AGENTS 第 5 章一致），允许 warn/error
      'no-console': ['warn', { allow: ['warn', 'error'] }],
      // 模板中组件名多词（vue 官方推荐），此处对业务页面放宽
      'vue/multi-word-component-names': 'off',
      // TS 未使用变量交由 tsconfig noUnusedLocals 管控，避免重复报错
      '@typescript-eslint/no-unused-vars': 'off',
    },
  },
  {
    // vite/node 侧配置文件
    files: ['vite.config.ts', 'eslint.config.js'],
    languageOptions: {
      globals: {
        ...globals.node,
      },
    },
  },
)
