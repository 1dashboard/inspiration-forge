package io.github.onedashboard.inspirationforge.controller;

import io.github.onedashboard.inspirationforge.constant.AppConstant;
import io.github.onedashboard.inspirationforge.mapper.AppMapper;
import io.github.onedashboard.inspirationforge.model.entity.App;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 静态资源访问
 */
@RestController
@RequestMapping("/static")
@Slf4j
public class StaticResourceController {

    private static final String PREVIEW_DEBUG_BRIDGE = """
            <script data-yu-preview-debug>
            (() => {
              if (window.__YU_PREVIEW_DEBUG__) return;
              window.__YU_PREVIEW_DEBUG__ = true;
              const send = (kind, level, message, file) => {
                try {
                  window.parent.postMessage({
                    channel: 'yu-preview-debug', kind, level,
                    message: String(message || ''), file: file || '', timestamp: Date.now()
                  }, '*');
                } catch (_) {}
              };
              const stringify = (value) => {
                if (typeof value === 'string') return value;
                if (value instanceof Error) return value.stack || value.message;
                try {
                  const seen = new WeakSet();
                  return JSON.stringify(value, (_, item) => {
                    if (item && typeof item === 'object') {
                      if (seen.has(item)) return '[Circular]';
                      seen.add(item);
                    }
                    return item;
                  });
                } catch (_) { return String(value); }
              };
              ['log', 'info', 'warn', 'error'].forEach((level) => {
                const original = console[level].bind(console);
                console[level] = (...args) => {
                  original(...args);
                  const error = args.find((item) => item instanceof Error);
                  send('console', level, args.map(stringify).join(' '), error && error.stack);
                };
              });
              window.addEventListener('error', (event) => {
                send('console', 'error', event.message || '脚本运行错误', event.filename);
              });
              window.addEventListener('unhandledrejection', (event) => {
                const reason = event.reason;
                send('console', 'error', 'Unhandled Promise: ' + stringify(reason), reason && reason.stack);
              });
              if (window.fetch) {
                const originalFetch = window.fetch.bind(window);
                window.fetch = async (...args) => {
                  const started = performance.now();
                  const request = args[0];
                  const url = typeof request === 'string' ? request : request && request.url;
                  const method = (args[1] && args[1].method) || (request && request.method) || 'GET';
                  try {
                    const response = await originalFetch(...args);
                    send('network', response.ok ? 'info' : 'error',
                      method + ' ' + response.status + ' ' + url + ' · ' + Math.round(performance.now() - started) + ' ms', url);
                    return response;
                  } catch (error) {
                    send('network', 'error', method + ' FAILED ' + url + ' · ' + stringify(error), url);
                    throw error;
                  }
                };
              }
              const originalOpen = XMLHttpRequest.prototype.open;
              const originalSend = XMLHttpRequest.prototype.send;
              XMLHttpRequest.prototype.open = function(method, url, ...rest) {
                this.__yuRequest = { method, url: String(url), started: 0 };
                return originalOpen.call(this, method, url, ...rest);
              };
              XMLHttpRequest.prototype.send = function(...args) {
                if (this.__yuRequest) this.__yuRequest.started = performance.now();
                this.addEventListener('loadend', () => {
                  const meta = this.__yuRequest || { method: 'GET', url: '', started: performance.now() };
                  send('network', this.status >= 400 || this.status === 0 ? 'error' : 'info',
                    meta.method + ' ' + this.status + ' ' + meta.url + ' · ' + Math.round(performance.now() - meta.started) + ' ms', meta.url);
                }, { once: true });
                return originalSend.apply(this, args);
              };
              window.addEventListener('load', () => setTimeout(() => {
                performance.getEntriesByType('resource').forEach((entry) => {
                  const status = entry.responseStatus ? String(entry.responseStatus) : 'RESOURCE';
                  send('network', entry.responseStatus >= 400 ? 'error' : 'info',
                    'GET ' + status + ' ' + entry.name + ' · ' + Math.round(entry.duration) + ' ms', entry.name);
                });
              }, 0), { once: true });
            })();
            </script>
            """;

    @jakarta.annotation.Resource
    private AppMapper appMapper;

    // 部署目录用于已发布作品，生成目录用于尚未部署的实时预览。
    private static final String DEPLOY_ROOT_DIR = AppConstant.CODE_DEPLOY_ROOT_DIR;
    private static final String OUTPUT_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /**
     * 提供静态资源访问，支持目录重定向
     * 访问格式：http://localhost:8123/api/static/{deployKey}[/{fileName}]
     */
    @GetMapping("/{deployKey}/**")
    public ResponseEntity<Resource> serveStaticResource(
            @PathVariable String deployKey,
            HttpServletRequest request) {
        try {
            App app = appMapper.selectOneByQuery(com.mybatisflex.core.query.QueryWrapper.create()
                    .eq("deployKey", deployKey));
            if (app != null && "PAUSED".equals(app.getDeployStatus())) {
                return ResponseEntity.notFound().build();
            }
            // 获取资源路径
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            String routePrefix = "/static/" + deployKey;
            if (resourcePath == null) {
                String requestUri = request.getRequestURI();
                String contextPath = request.getContextPath();
                resourcePath = requestUri.substring(Math.min(requestUri.length(), contextPath.length() + routePrefix.length()));
            } else if (resourcePath.startsWith(routePrefix)) {
                resourcePath = resourcePath.substring(routePrefix.length());
            } else {
                return ResponseEntity.notFound().build();
            }
            // 如果是目录访问（不带斜杠），重定向到带斜杠的URL
            if (resourcePath.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }
            // 默认返回 index.html
            if (resourcePath.equals("/")) {
                resourcePath = "/index.html";
            }
            // 构建文件路径
            File baseDir = new File(DEPLOY_ROOT_DIR, deployKey).getCanonicalFile();
            File file = new File(baseDir, resourcePath).getCanonicalFile();
            if (!file.toPath().startsWith(baseDir.toPath())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            if (!file.exists()) {
                baseDir = new File(OUTPUT_ROOT_DIR, deployKey).getCanonicalFile();
                File outputBaseDir = baseDir;
                if (deployKey.startsWith("vue_project_") && new File(baseDir, "dist").isDirectory()) {
                    outputBaseDir = new File(baseDir, "dist").getCanonicalFile();
                }
                file = new File(outputBaseDir, resourcePath).getCanonicalFile();
                if (!file.toPath().startsWith(outputBaseDir.toPath())) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
                }
            }
            // 检查文件是否存在
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            // 返回文件资源
            Resource resource;
            if ("1".equals(request.getParameter("debug")) && file.getName().endsWith(".html")) {
                String html = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                String instrumented = injectPreviewDebugBridge(html);
                resource = new ByteArrayResource(instrumented.getBytes(StandardCharsets.UTF_8));
            } else {
                resource = new FileSystemResource(file);
            }
            return ResponseEntity.ok()
                    .header("Content-Type", getContentTypeWithCharset(file.getPath()))
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Referrer-Policy", "no-referrer")
                    .header("Content-Security-Policy",
                            "default-src 'self' https: data: blob:; "
                                    + "script-src 'self' 'unsafe-inline' 'unsafe-eval' https:; "
                                    + "style-src 'self' 'unsafe-inline' https:; "
                                    + "img-src 'self' https: data: blob:; "
                                    + "connect-src 'self'; object-src 'none'; base-uri 'none';")
                    .body(resource);
        } catch (Exception e) {
            log.error("静态资源访问失败，deployKey: {}, uri: {}", deployKey, request.getRequestURI(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String injectPreviewDebugBridge(String html) {
        int headEnd = html.toLowerCase().indexOf("<head>");
        if (headEnd >= 0) {
            int insertAt = headEnd + "<head>".length();
            return html.substring(0, insertAt) + PREVIEW_DEBUG_BRIDGE + html.substring(insertAt);
        }
        return PREVIEW_DEBUG_BRIDGE + html;
    }

    /**
     * 根据文件扩展名返回带字符编码的 Content-Type
     */
    private String getContentTypeWithCharset(String filePath) {
        if (filePath.endsWith(".html")) return "text/html; charset=UTF-8";
        if (filePath.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filePath.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (filePath.endsWith(".png")) return "image/png";
        if (filePath.endsWith(".jpg")) return "image/jpeg";
        return "application/octet-stream";
    }
}
