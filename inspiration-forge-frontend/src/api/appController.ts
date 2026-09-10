// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /app/add */
export async function addApp(body: API.AppAddRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseLong>('/app/add', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

export async function toggleAppRag(
  params: { appId: string | number; enabled: boolean },
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean>('/rag/toggle', {
    method: 'POST',
    params,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/admin/delete */
export async function deleteAppByAdmin(body: API.DeleteRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/app/admin/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/admin/get/vo */
export async function getAppVoByIdByAdmin(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getAppVOByIdByAdminParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseAppVO>('/app/admin/get/vo', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/admin/list/page/vo */
export async function listAppVoByPageByAdmin(
  body: API.AppQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageAppVO>('/app/admin/list/page/vo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/admin/update */
export async function updateAppByAdmin(
  body: API.AppAdminUpdateRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/app/admin/update', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/chat/gen/code */
export async function chatToGenCode(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.chatToGenCodeParams,
  options?: { [key: string]: any }
) {
  return request<API.ServerSentEventString[]>('/app/chat/gen/code', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

export async function startCodeGeneration(
  body: { appId: string | number; message: string; plan?: unknown },
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean>('/app/chat/start', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/delete */
export async function deleteApp(body: API.DeleteRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/app/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

export async function batchDeleteApps(
  body: { ids: Array<string | number> },
  options?: { [key: string]: any },
) {
  return request<{ code?: number; data?: number; message?: string }>('/app/batch/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/deploy */
export async function deployApp(body: API.AppDeployRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseString>('/app/deploy', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

export async function startDeployment(params: { appId: number | string }, options?: { [key: string]: any }) {
  return request<API.BaseResponseString>('/app/deploy/start', { method: 'POST', params, ...(options || {}) })
}

export async function stopDeployment(params: { appId: number | string }, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/app/deploy/stop', { method: 'POST', params, ...(options || {}) })
}

export async function listAppVersions(params: { appId: number | string }, options?: { [key: string]: any }) {
  return request<{ code?: number; data?: any[]; message?: string }>('/app/version/list', {
    method: 'GET',
    params,
    ...(options || {}),
  })
}

export async function rollbackAppVersion(
  params: { appId: number | string; versionId: number | string },
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean>('/app/version/rollback', {
    method: 'POST',
    params,
    ...(options || {}),
  })
}

export async function diffAppVersion(
  params: {
    appId: number | string
    versionId?: number | string
    baseVersionId?: number | string
    targetVersionId?: number | string
  },
  options?: { [key: string]: any },
) {
  return request<{ code?: number; data?: any[]; message?: string }>('/app/version/diff', {
    method: 'GET',
    params,
    ...(options || {}),
  })
}

export async function restoreAppVersionFile(
  body: { appId: number | string; versionId: number | string; filePath: string },
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean>('/app/version/file/restore', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}

export async function getLatestGenerationTask(
  params: { appId: number | string },
  options?: { [key: string]: any },
) {
  return request<{ code?: number; data?: any; message?: string }>('/app/chat/task/latest', {
    method: 'GET',
    params,
    ...(options || {}),
  })
}

export async function createGenerationPlan(
  body: { appId: number | string; prompt: string },
  options?: { [key: string]: any },
) {
  return request<{ code?: number; data?: any; message?: string }>('/app/chat/plan', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}

export async function checkAppBuild(
  params: { appId: number | string },
  options?: { [key: string]: any },
) {
  return request<{ code?: number; data?: any; message?: string }>('/app/build/check', {
    method: 'POST',
    params,
    ...(options || {}),
  })
}

export async function saveAppCodeFile(
  body: { appId: number | string; filePath: string; content: string },
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean>('/app/code/file', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}

export async function listAppCodeFiles(params: { appId: number | string }, options?: { [key: string]: any }) {
  return request<{ code?: number; data?: any[]; message?: string }>('/app/code/files', {
    method: 'GET',
    params,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/download/${param0} */
export async function downloadAppCode(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.downloadAppCodeParams,
  options?: { [key: string]: any }
) {
  const { appId: param0, ...queryParams } = params
  return request<any>(`/app/download/${param0}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/get/vo */
export async function getAppVoById(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getAppVOByIdParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseAppVO>('/app/get/vo', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/good/list/page/vo */
export async function listGoodAppVoByPage(
  body: API.AppQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageAppVO>('/app/good/list/page/vo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/my/list/page/vo */
export async function listMyAppVoByPage(
  body: API.AppQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageAppVO>('/app/my/list/page/vo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/update */
export async function updateApp(body: API.AppUpdateRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/app/update', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
