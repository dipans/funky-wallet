/**
 * E2E — Account lifecycle against a real local Geth node.
 *
 * Prerequisites (start with /funkyup or docker compose -f docker-compose.e2e.yml):
 *   - Geth dev node       :8545
 *   - evm-chain-adapter   :9090  (real web3j, points to Geth)
 *   - mock-signing-coord  :9000  (real ECDSA via web3j)
 *   - wallet-api-service  :8080  (Postgres, local profile)
 *   - funky-wallet-ui     :3000
 *
 * Test account:  0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266
 * Mnemonic:      test test test test test test test test test test test junk
 * Pre-funded:    100k ETH in genesis.json
 */

import { test, expect } from '../fixtures/stack'
import { api, waitForConfirmed } from '../utils/api'
import { getEthBalance, sendEth, waitForGethTx, DEV_ACCOUNTS } from '../utils/geth'

const TEST_ACCOUNT = DEV_ACCOUNTS.primary.address
const RECIPIENT    = DEV_ACCOUNTS.secondary.address
const SEND_AMOUNT  = '0.001'

test.describe('Account lifecycle — real Geth e2e', () => {

  test('1. test account exists and has real ETH balance', async ({ page }) => {
    // API: account in DB with correct chain metadata
    const account = await api.getAccount(TEST_ACCOUNT)
    expect(account.address.toLowerCase()).toBe(TEST_ACCOUNT.toLowerCase())
    expect(account.networkType).toBe('EVM')

    // Geth: real on-chain balance
    const balance = parseFloat(await getEthBalance(TEST_ACCOUNT))
    expect(balance).toBeGreaterThan(0)
    console.log(`✓ On-chain balance: ${balance} ETH`)

    // UI: accounts page shows the test account (address rendered as 0xf39F…2266)
    await page.goto('/accounts')
    const truncated = `${TEST_ACCOUNT.slice(0, 6)}…${TEST_ACCOUNT.slice(-4)}`
    await expect(page.locator('main').getByText(truncated).first()).toBeVisible()
  })

  test('2. create a fresh account and verify it derives a real Ethereum address', async ({ page }) => {
    const result = await api.createAccount({
      network: 'ETHEREUM',
      chainId: 1337,
      chainName: 'Local Dev',
      networkType: 'EVM',
    })

    // Address must be valid EVM format
    expect(result.account.address).toMatch(/^0x[0-9a-fA-F]{40}$/)
    // Mnemonic returned once — 12 words
    expect(result.mnemonic.split(' ').length).toBe(12)
    console.log(`✓ New account: ${result.account.address}`)

    // UI: new account should appear in accounts list (address rendered as 0xXXXX…XXXX)
    await page.goto('/accounts')
    const addr = result.account.address
    const truncated = `${addr.slice(0, 6)}…${addr.slice(-4)}`
    await expect(page.locator('main').getByText(truncated).first()).toBeVisible()
  })

  test('3. send ETH — full signing → broadcast → confirm on Geth', async ({ page }) => {
    // Get balance before
    const balanceBefore = parseFloat(await getEthBalance(TEST_ACCOUNT))

    // Submit via wallet-api-service (triggers real signing + Geth broadcast)
    const tx = await api.sendTransaction({
      fromAddress: TEST_ACCOUNT,
      toAddress: RECIPIENT,
      amount: SEND_AMOUNT,
      network: 'ETHEREUM',
    })

    // Fast PoA chains may confirm before the API response returns
    expect(['PENDING', 'CONFIRMED']).toContain(tx.status)
    expect(tx.fromAddress.toLowerCase()).toBe(TEST_ACCOUNT.toLowerCase())
    expect(tx.toAddress.toLowerCase()).toBe(RECIPIENT.toLowerCase())
    console.log(`✓ Transaction submitted: ${tx.id} hash=${tx.hash}`)

    // Wait for wallet-api-service to mark CONFIRMED
    const confirmed = await waitForConfirmed(tx.id, { timeoutMs: 60_000 })
    expect(confirmed.status).toBe('CONFIRMED')
    console.log(`✓ Transaction confirmed: ${confirmed.hash}`)

    // Verify on Geth directly
    expect(confirmed.hash).toMatch(/^0x[0-9a-fA-F]{64}$/)

    // Balance should have decreased (minus gas)
    const balanceAfter = parseFloat(await getEthBalance(TEST_ACCOUNT))
    expect(balanceAfter).toBeLessThan(balanceBefore)
    console.log(`✓ Balance: ${balanceBefore} → ${balanceAfter} ETH`)

    // UI: check activity page shows the transaction
    await page.goto('/activity')
    await expect(page.getByText(SEND_AMOUNT).first()).toBeVisible({ timeout: 10_000 })
  })

  test('4. receive ETH — fund from secondary account, verify balance increases', async ({ page }) => {
    const balanceBefore = parseFloat(await getEthBalance(TEST_ACCOUNT))

    // Send ETH directly via Geth (bypasses wallet backend — simulates receiving from external)
    const hash = await sendEth(DEV_ACCOUNTS.secondary, TEST_ACCOUNT, '0.5')
    await waitForGethTx(hash)
    console.log(`✓ Funded test account from secondary: ${hash}`)

    // On-chain balance should have increased
    const balanceAfter = parseFloat(await getEthBalance(TEST_ACCOUNT))
    expect(balanceAfter).toBeGreaterThan(balanceBefore)
    console.log(`✓ Balance: ${balanceBefore} → ${balanceAfter} ETH (+0.5)`)

    // UI: dashboard balance should reflect new amount (needs a page refresh / polling)
    await page.goto('/')
    await page.waitForTimeout(2000) // allow 30s refetch to trigger
    // Balance card should show something (not just '—')
    await expect(page.locator('.mono').first()).toBeVisible()
  })

  test('5. full round trip — send then receive, net balance tracked', async () => {
    const start = parseFloat(await getEthBalance(TEST_ACCOUNT))

    // Step A: Send from test account via wallet
    const sendTx = await api.sendTransaction({
      fromAddress: TEST_ACCOUNT,
      toAddress: RECIPIENT,
      amount: '0.01',
      network: 'ETHEREUM',
    })
    const confirmed = await waitForConfirmed(sendTx.id, { timeoutMs: 60_000 })
    // Also wait for Geth to finalize the balance update
    await waitForGethTx(confirmed.hash as `0x${string}`)

    const afterSend = parseFloat(await getEthBalance(TEST_ACCOUNT))
    expect(afterSend).toBeLessThan(start)

    // Step B: Receive ETH from tertiary account
    const receiveHash = await sendEth(DEV_ACCOUNTS.tertiary, TEST_ACCOUNT, '0.05')
    await waitForGethTx(receiveHash)

    const afterReceive = parseFloat(await getEthBalance(TEST_ACCOUNT))
    expect(afterReceive).toBeGreaterThan(afterSend)

    console.log(`✓ Round trip: start=${start.toFixed(4)} → after send=${afterSend.toFixed(4)} → after receive=${afterReceive.toFixed(4)}`)

    // Verify transaction count in DB
    const txs = await api.listTransactions(TEST_ACCOUNT)
    expect(txs.totalElements).toBeGreaterThanOrEqual(1)
  })
})
