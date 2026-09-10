package io.github.onedashboard.inspirationforge.runtime.service;

import io.github.onedashboard.inspirationforge.exception.BusinessException;
import io.github.onedashboard.inspirationforge.exception.ErrorCode;
import io.github.onedashboard.inspirationforge.exception.ThrowUtils;
import io.github.onedashboard.inspirationforge.mapper.AppMapper;
import io.github.onedashboard.inspirationforge.model.entity.App;
import io.github.onedashboard.inspirationforge.model.enums.CodeGenTypeEnum;
import io.github.onedashboard.inspirationforge.runtime.model.vo.RuntimeConfigVO;
import io.github.onedashboard.inspirationforge.security.ProjectPathGuard;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class RuntimeSdkManager {

    @Resource
    private AppMapper appMapper;

    @Resource
    private ProjectPathGuard projectPathGuard;

    public void sync(Long appId, RuntimeConfigVO config) {
        App app = appMapper.selectOneById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        ThrowUtils.throwIf(!CodeGenTypeEnum.VUE_PROJECT.getValue().equals(app.getCodeGenType()),
                ErrorCode.PARAMS_ERROR, "托管数据库当前仅支持 Vue 工程模式");
        Path sdkPath = projectPathGuard.resolveGeneratedPath(appId, app.getCodeGenType(),
                "src/lib/yuRuntime.js");
        try {
            Files.createDirectories(sdkPath.getParent());
            Files.writeString(sdkPath, source(config.getRuntimeKey()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "生成运行时 SDK 失败");
        }
    }

    String source(String runtimeKey) {
        return """
                const runtimeKey = '%s'

                const runtimeEnvironment = () => {
                  const explicit = window.__YU_RUNTIME_ENV__
                  if (explicit === 'preview' || explicit === 'production') return explicit
                  const path = window.location.pathname || ''
                  // Platform previews run inside an iframe. Keep them isolated from
                  // production even when a dev-server proxy rewrites the pathname.
                  if (path.includes('/api/static/') || window.parent !== window) return 'preview'
                  // A generated project opened from the local Vite server is a preview.
                  // Production is opt-in through __YU_RUNTIME_ENV__ or a non-local host.
                  const host = window.location.hostname
                  return host === 'localhost' || host === '127.0.0.1' || host === '::1'
                    ? 'preview' : 'production'
                }

                const apiOrigin = () => window.__YU_RUNTIME_API_ORIGIN__ || window.location.origin
                const baseUrl = () => `${apiOrigin()}/api/runtime/v1/${runtimeKey}/${runtimeEnvironment()}`
                const authUrl = (path) => `${baseUrl()}${path}`
                const tokenKey = `yu-runtime-token:${runtimeKey}`
                const getToken = () => window.localStorage.getItem(tokenKey)
                const setToken = (token) => token ? window.localStorage.setItem(tokenKey, token) : window.localStorage.removeItem(tokenKey)

                const request = async (path, options = {}) => {
                  const response = await fetch(`${baseUrl()}${path}`, {
                    credentials: 'include',
                    headers: { 'Content-Type': 'application/json', ...(getToken() ? { Authorization: `Bearer ${getToken()}` } : {}), ...(options.headers || {}) },
                    ...options,
                  })
                  const payload = await response.json().catch(() => null)
                  if (!response.ok || !payload || payload.code !== 0) {
                    throw new Error(payload?.message || `数据请求失败 (${response.status})`)
                  }
                  return payload.data
                }

                const modelPath = (modelKey) => `/records/${encodeURIComponent(modelKey)}`

                export const yuRuntime = {
                  auth: {
                    register: async (username, password, displayName) => { const data = await fetch(authUrl('/auth/register'), { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password, displayName }) }).then(async r => { const p = await r.json(); if (p.code !== 0) throw new Error(p.message); return p.data }); setToken(data.token); return data },
                    login: async (username, password) => { const data = await fetch(authUrl('/auth/login'), { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }) }).then(async r => { const p = await r.json(); if (p.code !== 0) throw new Error(p.message); return p.data }); setToken(data.token); return data },
                    me: () => request('/auth/me'),
                    logout: async () => { try { await request('/auth/logout', { method: 'POST' }) } finally { setToken(null) } },
                    token: getToken,
                  },
                  models: () => request('/models'),
                  query: async (modelKey, params = {}) => {
                    const page = await request(`${modelPath(modelKey)}/query`, {
                      method: 'POST', body: JSON.stringify({ pageNum: 1, pageSize: 20, ...params }),
                    })
                    return { ...page, list: page?.list || page?.records || [] }
                  },
                  get: (modelKey, recordId) => request(`${modelPath(modelKey)}/${recordId}`),
                  create: (modelKey, data) => request(modelPath(modelKey), {
                    method: 'POST', body: JSON.stringify({ data }),
                  }),
                  update: (modelKey, recordId, data, expectedVersion) =>
                    request(`${modelPath(modelKey)}/${recordId}`, {
                      method: 'PATCH', body: JSON.stringify({ data, expectedVersion }),
                    }),
                  remove: (modelKey, recordId, expectedVersion) =>
                    request(`${modelPath(modelKey)}/${recordId}?expectedVersion=${expectedVersion}`, {
                      method: 'DELETE',
                    }),
                }

                const flattenRecord = (record) => record && record.data && typeof record.data === 'object'
                  ? { ...record.data, id: record.id, version: record.version,
                      createTime: record.createTime, updateTime: record.updateTime }
                  : record

                yuRuntime.data = {
                  query: async (modelKey, params = {}) => {
                    const page = await yuRuntime.query(modelKey, {
                      ...params,
                      pageNum: params.pageNum || params.page || 1,
                    })
                    return { ...page, records: (page?.records || []).map(flattenRecord) }
                  },
                  get: async (modelKey, recordId) => flattenRecord(await yuRuntime.get(modelKey, recordId)),
                  add: async (modelKey, data) => flattenRecord(await yuRuntime.create(modelKey, data)),
                  create: async (modelKey, data) => flattenRecord(await yuRuntime.create(modelKey, data)),
                  update: async (modelKey, recordId, data, expectedVersion) => {
                    const version = expectedVersion || (await yuRuntime.get(modelKey, recordId))?.version
                    return flattenRecord(await yuRuntime.update(modelKey, recordId, data, version))
                  },
                  delete: async (modelKey, recordId, expectedVersion) => {
                    const version = expectedVersion || (await yuRuntime.get(modelKey, recordId))?.version
                    return yuRuntime.remove(modelKey, recordId, version)
                  },
                  remove: async (modelKey, recordId, expectedVersion) => {
                    const version = expectedVersion || (await yuRuntime.get(modelKey, recordId))?.version
                    return yuRuntime.remove(modelKey, recordId, version)
                  },
                }

                export default yuRuntime
                """.formatted(runtimeKey);
    }
}
