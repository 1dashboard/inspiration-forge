<script setup lang="ts">
import BasicLayout from '@/layouts/BasicLayout.vue'

const appTheme = {
  token: {
    colorPrimary: '#176b5b',
    colorInfo: '#176b5b',
    colorLink: '#176b5b',
    borderRadius: 6,
    fontFamily:
      "Inter, ui-sans-serif, -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif",
  },
}
</script>

<template>
  <a-config-provider :theme="appTheme">
    <BasicLayout />
  </a-config-provider>
</template>

<style>
:root {
  --color-primary: #176b5b;
  --color-text: #14211f;
  --color-muted: #667a75;
  --color-border: #dce7e4;
  --color-surface: #ffffff;
  --color-canvas: #eef5f4;
}

* {
  box-sizing: border-box;
}

html {
  overflow-x: hidden;
  background: var(--color-canvas);
}

body {
  margin: 0;
  padding: 0;
  overflow-x: hidden;
  color: var(--color-text);
  background: var(--color-canvas);
  font-family:
    Inter, ui-sans-serif, -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei',
    sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

button,
input,
textarea {
  font: inherit;
  letter-spacing: 0;
}

a {
  text-underline-offset: 3px;
}

::selection {
  color: #103b32;
  background: #cfece5;
}

::-webkit-scrollbar {
  width: 10px;
  height: 10px;
}

::-webkit-scrollbar-thumb {
  background: #c9ced8;
  border: 3px solid transparent;
  border-radius: 8px;
  background-clip: padding-box;
}

::-webkit-scrollbar-track {
  background: transparent;
}
</style>
