import request from '@/request'

export type RuntimeEnvironment = 'PREVIEW' | 'PRODUCTION'
export type RuntimeFieldType = 'STRING' | 'TEXT' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN' | 'DATE' | 'DATETIME' | 'ENUM'
export type RuntimeOperation = 'READ' | 'CREATE' | 'UPDATE' | 'DELETE'

export interface RuntimeFieldDefinition {
  key: string
  name: string
  type: RuntimeFieldType
  required: boolean
  maxLength?: number
  options?: string[]
}

export interface RuntimeModelDefinition {
  modelKey: string
  displayName: string
  fields: RuntimeFieldDefinition[]
  publicOperations: RuntimeOperation[]
}

export interface RuntimeModel {
  id: string
  environment: RuntimeEnvironment
  definition: RuntimeModelDefinition
  schemaVersion: number
  status: 'DRAFT' | 'PUBLISHED'
  updateTime?: string
}

export interface RuntimeRecord {
  id: string
  modelKey: string
  data: Record<string, unknown>
  version: number
  createTime?: string
  updateTime?: string
}

export interface RuntimeAuthUser {
  userId: string
  appId: string
  username: string
  displayName?: string
  roles: string[]
}

export const listRuntimeUsers = (appId: string) => request('/app/runtime/user/list', {
  method: 'GET', params: { appId },
})

export const assignRuntimeRole = (appId: string, userId: string, roleKey: string) => request('/app/runtime/user/role/assign', {
  method: 'POST', data: { appId, userId, roleKey },
})

export const ensureRuntimeConfig = (appId: string) => request('/app/runtime/config/ensure', {
  method: 'POST', params: { appId },
})

export const getRuntimeConfig = (appId: string) => request('/app/runtime/config/get', {
  method: 'GET', params: { appId },
})

export const listRuntimeModels = (appId: string, environment: RuntimeEnvironment) => request('/app/runtime/model/list', {
  method: 'GET', params: { appId, environment },
})

export const upsertRuntimeModel = (appId: string, definition: RuntimeModelDefinition) => request('/app/runtime/model/upsert', {
  method: 'POST', data: { appId, definition },
})

export const publishRuntimeModel = (appId: string, modelKey: string) => request('/app/runtime/model/publish', {
  method: 'POST', data: { appId, modelKey },
})

export const queryRuntimeRecords = (appId: string, environment: RuntimeEnvironment, modelKey: string,
                                    pageNum = 1, pageSize = 20) => request('/app/runtime/record/query', {
  method: 'POST', data: { appId, environment, modelKey, pageNum, pageSize },
})

export const createRuntimeRecord = (appId: string, environment: RuntimeEnvironment, modelKey: string,
                                    data: Record<string, unknown>) => request('/app/runtime/record/create', {
  method: 'POST', data: { appId, environment, modelKey, data },
})

export const updateRuntimeRecord = (appId: string, environment: RuntimeEnvironment, modelKey: string,
                                    recordId: string, expectedVersion: number,
                                    data: Record<string, unknown>) => request('/app/runtime/record/update', {
  method: 'POST', data: { appId, environment, modelKey, recordId, expectedVersion, data },
})

export const deleteRuntimeRecord = (appId: string, environment: RuntimeEnvironment, modelKey: string,
                                    recordId: string, expectedVersion: number) => request('/app/runtime/record/delete', {
  method: 'POST', data: { appId, environment, modelKey, recordId, expectedVersion },
})
