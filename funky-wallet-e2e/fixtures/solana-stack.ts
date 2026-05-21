import { test as base, expect } from '@playwright/test'
import { api } from '../utils/api'
import { isSolanaDevnetReachable } from '../utils/solana'

const SOLANA_ADAPTER = process.env.SOLANA_ADAPTER_URL ?? 'http://localhost:9091'

async function isSolanaAdapterHealthy(): Promise<boolean> {
  try {
    const r = await fetch(`${SOLANA_ADAPTER}/health`)
    if (!r.ok) return false
    const body = await r.json() as { status: string }
    return body.status === 'UP'
  } catch {
    return false
  }
}

export const test = base.extend({
  page: async ({ page }, use) => {
    const health = await api.health()
    if (health.status !== 'UP') throw new Error('wallet-api-service is not healthy')

    const adapterOk = await isSolanaAdapterHealthy()
    if (!adapterOk) throw new Error('solana-chain-adapter is not healthy at ' + SOLANA_ADAPTER)

    const devnetOk = await isSolanaDevnetReachable()
    if (!devnetOk) throw new Error('Solana devnet is not reachable')

    await use(page)
  },
})

export { expect }
