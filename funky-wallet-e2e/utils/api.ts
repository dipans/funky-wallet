const BASE = process.env.API_URL ?? 'http://localhost:8080/api/v1'

async function get<T>(path: string): Promise<T> {
  const r = await fetch(`${BASE}${path}`)
  if (!r.ok) throw new Error(`GET ${path} → ${r.status}: ${await r.text()}`)
  return r.json() as Promise<T>
}

async function post<T>(path: string, body: unknown): Promise<T> {
  const r = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!r.ok) throw new Error(`POST ${path} → ${r.status}: ${await r.text()}`)
  return r.json() as Promise<T>
}

export interface Account {
  id: string
  address: string
  publicKey: string
  network: string
  networkType: string
  chainId: number
  chainName: string
  environment: string
  chainDetails?: Record<string, string>
  createdAt: string
}

export interface Transaction {
  id: string
  hash: string
  fromAddress: string
  toAddress: string
  amount: string
  symbol: string
  network: string
  status: 'PENDING' | 'CONFIRMED' | 'FAILED' | 'RECEIVED'
  createdAt: string
  confirmedAt?: string
}

export interface Balance {
  address: string
  network: string
  amount: string
  symbol: string
}

export interface PagedResponse<T> {
  content: T[]
  totalElements: number
  page: number
  size: number
  totalPages: number
}

export interface CreateAccountResponse {
  account: Account
  mnemonic: string
}

export const api = {
  health: () => get<{ status: string }>('/health'),

  createAccount: (payload: {
    network: string
    chainId: number
    chainName: string
    networkType: string
  }) => post<CreateAccountResponse>('/accounts', payload),

  getAccount: (address: string) => get<Account>(`/accounts/${address}`),
  listAccounts: () => get<Account[]>('/accounts'),

  getBalance: (address: string) => get<Balance>(`/accounts/${address}/balance`),

  sendTransaction: (payload: {
    fromAddress: string
    toAddress: string
    amount: string
    network: string
  }) => post<Transaction>('/transactions', payload),

  getTransaction: (id: string) => get<Transaction>(`/transactions/${id}`),

  listTransactions: (address: string) =>
    get<PagedResponse<Transaction>>(`/transactions?address=${address}`),
}

/** Polls listTransactions until a RECEIVED entry appears for the given address. */
export async function waitForReceived(
  address: string,
  { timeoutMs = 90_000, pollMs = 5_000 } = {},
): Promise<Transaction> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const result = await api.listTransactions(address)
    const received = result.content.find((tx) => tx.status === 'RECEIVED')
    if (received) return received
    await new Promise((r) => setTimeout(r, pollMs))
  }
  throw new Error(`No RECEIVED transaction for ${address} within ${timeoutMs}ms`)
}

export async function waitForConfirmed(
  txId: string,
  { timeoutMs = 60_000, pollMs = 3_000 } = {}
): Promise<Transaction> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const tx = await api.getTransaction(txId)
    if (tx.status === 'CONFIRMED') return tx
    if (tx.status === 'FAILED') throw new Error(`Transaction ${txId} FAILED`)
    await new Promise((r) => setTimeout(r, pollMs))
  }
  throw new Error(`Transaction ${txId} did not confirm within ${timeoutMs}ms`)
}
