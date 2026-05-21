import axios, { AxiosError } from 'axios'
import type { Account, Balance, Transaction, CreateAccountResponse, ApiError, Network, NetworkType } from '../types'

const client = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
})

// Populated by ApiTokenProvider on mount
let _getToken: (() => Promise<string>) | null = null
export function setTokenGetter(fn: () => Promise<string>) { _getToken = fn }

client.interceptors.request.use(async (config) => {
  if (_getToken) {
    try {
      const token = await _getToken()
      config.headers.Authorization = `Bearer ${token}`
    } catch {
      // token fetch failed — request proceeds without auth (backend will reject)
    }
  }
  return config
})

client.interceptors.response.use(
  (r) => r,
  (err: AxiosError<ApiError>) => {
    const detail = err.response?.data
    return Promise.reject(detail ?? { message: 'Network error', status: 0, error: 'Unknown', timestamp: new Date().toISOString() })
  }
)

export interface CreateAccountPayload {
  network: Network
  chainId: number
  chainName: string
  networkType: NetworkType
}

export const accountApi = {
  create: (payload: CreateAccountPayload): Promise<CreateAccountResponse> =>
    client.post<CreateAccountResponse>('/accounts', payload).then((r) => r.data),

  list: (): Promise<Account[]> =>
    client.get<Account[]>('/accounts').then((r) => r.data),

  get: (address: string): Promise<Account> =>
    client.get<Account>(`/accounts/${address}`).then((r) => r.data),

  getBalance: (address: string): Promise<Balance> =>
    client.get<Balance>(`/accounts/${address}/balance`).then((r) => r.data),
}

export const transactionApi = {
  send: (payload: { fromAddress: string; toAddress: string; amount: string; network: Network }): Promise<Transaction> =>
    client.post<Transaction>('/transactions', payload).then((r) => r.data),

  list: (address?: string): Promise<Transaction[]> =>
    client.get<{ content: Transaction[] }>('/transactions', { params: address ? { address } : undefined }).then((r) => r.data.content),

  get: (id: string): Promise<Transaction> =>
    client.get<Transaction>(`/transactions/${id}`).then((r) => r.data),
}
