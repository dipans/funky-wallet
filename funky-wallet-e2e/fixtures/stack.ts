import { test as base, expect } from '@playwright/test'
import { api } from '../utils/api'
import { isGethReachable, DEV_ACCOUNTS } from '../utils/geth'

// Custom fixture that verifies the full stack is healthy before each test
export const test = base.extend({
  page: async ({ page }, use) => {
    // Verify API
    const health = await api.health()
    if (health.status !== 'UP') throw new Error('wallet-api-service is not healthy')

    // Verify Geth
    const gethOk = await isGethReachable()
    if (!gethOk) throw new Error('Geth node is not reachable at GETH_RPC_URL')

    // Verify test account exists in DB
    const account = await api.getAccount(DEV_ACCOUNTS.primary.address)
    if (!account) throw new Error('Test account not seeded in DB')

    await use(page)
  },
})

export { expect }
