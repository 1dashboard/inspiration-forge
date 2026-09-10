<template>
  <footer class="footer">
    <div class="footer-content">
      <span>灵感工坊 · 让想法成为应用</span>
      <a
        href="https://github.com/1dashboard/inspiration-forge"
        target="_blank"
        rel="noopener noreferrer"
      >
        Inspiration Forge 开源项目
      </a>
    </div>
  </footer>
</template>

<style scoped>
.footer {
  background: #ffffff;
  border-top: 1px solid #e4ecea;
}

.footer-content {
  width: min(100% - 48px, 1200px);
  min-height: 72px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  color: #84948f;
  font-size: 12px;
}

.footer-content a {
  color: #526b65;
}

@media (max-width: 680px) {
  .footer-content {
    width: calc(100% - 24px);
    min-height: 64px;
  }
}
</style>
