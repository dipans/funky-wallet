/**
 * E2E — Solana account lifecycle against Solana devnet.
 *
 * Prerequisites:
 *   - wallet-api-service   :8080
 *   - solana-chain-adapter :9091  (points to devnet)
 *   - Solana devnet reachable (https://api.devnet.solana.com)
 *
 * Two describe groups:
 *   A) Account creation — creates a fresh account, verifies address format + chainDetails.
 *      No on-chain funds needed.
 *
 *   B) Block watcher — uses SOLANA_TEST_ADDRESS (must exist in DB and have devnet SOL).
 *      Defaults to the address seeded during local dev testing.
 *      The airdrop test is conditional: skipped if devnet rate-limits the request.
 *
 * UI assertions (accounts list, Activity page) require the full e2e Docker stack
 * with VITE_AUTH_DISABLED=true. In local dev those are skipped at the API level.
 */

import { test, expect } from '../fixtures/solana-stack'
import { api, waitForReceived } from '../utils/api'
import { airdropSol, getSolBalance, waitForSolBalance } from '../utils/solana'

// Pre-seeded Solana address that has devnet SOL and a RECEIVED tx in the DB.
// Override via SOLANA_TEST_ADDRESS env var when running a fresh stack.
const FUNDED_ADDRESS =
  process.env.SOLANA_TEST_ADDRESS ?? '2hKLTVJxDQGVSEqno58jdnMum93Br9xYH4QagpNTKE6r'

// ── A. Account creation ───────────────────────────────────────────────────────

test.describe('Solana — account creation', () => {

  test('1. create account — valid base58 address + chainDetails populated', async () => {
    const result = await api.createAccount({
      network: 'SOLANA',
      chainId: 0,
      chainName: 'Solana Devnet',
      networkType: 'SOLANA',
    })

    const addr = result.account.address

    // Valid base58 Solana public key (32–44 chars)
    expect(addr).toMatch(/^[1-9A-HJ-NP-Za-km-z]{32,44}$/)

    // Mnemonic returned once — 12 words
    expect(result.mnemonic.trim().split(/\s+/).length).toBe(12)

    // chainDetails stored in TEXT column — nonceAccount + nonceAuthority
    const details = result.account.chainDetails
    expect(details).toBeDefined()
    expect(details?.nonceAccount).toMatch(/^[1-9A-HJ-NP-Za-km-z]{32,44}$/)
    expect(details?.nonceAuthority).toBe(addr) // user wallet = nonce authority initially

    // Network metadata
    expect(result.account.networkType).toBe('SOLANA')
    expect(result.account.environment).toBe('DEVNET')

    console.log(`✓ address:         ${addr}`)
    console.log(`  nonceAccount:    ${details?.nonceAccount}`)
    console.log(`  nonceAuthority:  ${details?.nonceAuthority}`)
    console.log(`  mnemonic word 1: ${result.mnemonic.split(' ')[0]} ...`)
  })

  test('2. new account starts with zero balance', async () => {
    const result = await api.createAccount({
      network: 'SOLANA',
      chainId: 0,
      chainName: 'Solana Devnet',
      networkType: 'SOLANA',
    })
    const balance = await api.getBalance(result.account.address)
    expect(balance.symbol).toBe('SOL')
    expect(parseFloat(balance.amount)).toBe(0)
    console.log(`✓ Initial balance: ${balance.amount} SOL`)
  })
})

// ── B. Block watcher + funded account ────────────────────────────────────────

test.describe.serial('Solana — block watcher', () => {

  test('3. funded address has SOL on devnet and via chain adapter', async () => {
    // On-chain via devnet RPC
    const onChain = await getSolBalance(FUNDED_ADDRESS)
    expect(onChain).toBeGreaterThan(0)
    console.log(`✓ On-chain balance: ${onChain} SOL (${FUNDED_ADDRESS.slice(0, 8)}...)`)

    // Chain adapter balance endpoint
    const apiBalance = await api.getBalance(FUNDED_ADDRESS)
    expect(apiBalance.symbol).toBe('SOL')
    expect(parseFloat(apiBalance.amount)).toBeGreaterThan(0)
    console.log(`✓ API balance:      ${apiBalance.amount} SOL`)
  })

  test('4. block watcher recorded incoming SOL as RECEIVED', async () => {
    // The block watcher should already have picked up the devnet faucet transfer.
    // If not yet present, wait up to 90s (covers one or two watcher cycles).
    const received = await waitForReceived(FUNDED_ADDRESS, { timeoutMs: 90_000 })

    expect(received.status).toBe('RECEIVED')
    expect(received.symbol).toBe('SOL')
    expect(received.toAddress).toBe(FUNDED_ADDRESS)
    expect(parseFloat(received.amount)).toBeGreaterThan(0)
    expect(received.network).toBe('SOLANA')
    expect(received.confirmedAt).toBeDefined()

    console.log(`✓ RECEIVED: ${received.amount} SOL`)
    console.log(`  sig:         ${received.hash?.slice(0, 20)}...`)
    console.log(`  confirmedAt: ${received.confirmedAt}`)
  })

  test('5. airdrop to a fresh address — balance and RECEIVED tx appear (skipped if rate-limited)', async () => {
    test.setTimeout(180_000)

    // Create a fresh address to airdrop to (avoids conflicts with existing state)
    const result = await api.createAccount({
      network: 'SOLANA',
      chainId: 0,
      chainName: 'Solana Devnet',
      networkType: 'SOLANA',
    })
    const freshAddress = result.account.address

    let sig: string
    try {
      sig = await airdropSol(freshAddress, 1)
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err)
      if (msg.includes('rate') || msg.includes('limit') || msg.includes('faucet') || msg.includes('Internal')) {
        test.skip(true, `Devnet airdrop rate-limited: ${msg}`)
        return
      }
      throw err
    }

    console.log(`✓ Airdrop sig: ${sig.slice(0, 20)}...`)

    // On-chain balance increases
    const balance = await waitForSolBalance(freshAddress, 1, { timeoutMs: 60_000 })
    expect(balance).toBeGreaterThanOrEqual(1)
    console.log(`✓ On-chain balance: ${balance} SOL`)

    // Block watcher detects and records as RECEIVED
    const received = await waitForReceived(freshAddress, { timeoutMs: 90_000 })
    expect(received.status).toBe('RECEIVED')
    expect(received.symbol).toBe('SOL')
    expect(parseFloat(received.amount)).toBeGreaterThanOrEqual(1)
    console.log(`✓ RECEIVED recorded by block watcher: ${received.amount} SOL`)
  })
})
