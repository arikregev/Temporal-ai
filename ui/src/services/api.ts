import axios from 'axios'

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8089/api'

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

export interface QueryRequest {
  query: string
  team?: string
}

export interface QueryResponse {
  source: 'KNOWLEDGE_BASE' | 'QUERY_LAYER' | 'LLM'
  answer: string
  data: any
  confidence: number
}

export interface FindingExplanation {
  explanation: string
  stepsToReproduce: string
  codePointers: string
  impact: string
  recommendedFix: string
}

export interface KnowledgeBaseEntry {
  kbId: string
  question: string
  answer: string
  team?: string
  usageCount: number
  isActive: boolean
}

export interface PolicyRequest {
  policy: string
}

export interface CompiledPolicy {
  originalPolicy: string
  action: string
  condition: string
  scope: string
  ruleCode: string
}

export const queryApi = {
  processQuery: (request: QueryRequest) =>
    api.post<QueryResponse>('/query', request).then(res => res.data),
}

export const explanationApi = {
  explainFinding: (findingId: string) =>
    api.get<FindingExplanation>(`/explanation/finding/${findingId}`).then(res => res.data),
}

export const knowledgeBaseApi = {
  list: (team?: string, isActive?: boolean) =>
    api.get<KnowledgeBaseEntry[]>('/knowledge', {
      params: { team, isActive },
    }).then(res => res.data),
  
  create: (entry: {
    question: string
    answer: string
    createdBy: string
    team?: string
    project?: string
    contextTags?: string[]
  }) =>
    api.post<KnowledgeBaseEntry>('/knowledge', entry).then(res => res.data),
  
  search: (query: string, team?: string, limit?: number) =>
    api.post('/knowledge/search', { query, team, limit }).then(res => res.data),
  
  delete: (kbId: string) =>
    api.delete(`/knowledge/${kbId}`).then(res => res.data),
}

export const policyApi = {
  compile: (policy: string) =>
    api.post<CompiledPolicy>('/policy/compile', { policy }).then(res => res.data),
}

export default api

