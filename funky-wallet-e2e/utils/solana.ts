/**
 * Solana devnet utilities using raw JSON-RPC — no @solana/web3.js dependency
 * (avoids the ESM/CJS conflict with rpc-websockets).
 */

const SOLANA_RPC = process.env.SOLANA_RPC_URL ?? 'https://api.devnet.solana.com'
const LAMPORTS_PER_SOL = 1_000_000_000

async function rpc(method: string, params: unknown[]): Promise<unknown> {
  const res = await fetch(SOLANA_RPC, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ jsonrpc: '2.0', id: 1, method, params }),
  })
  const body = await res.json() as { result?: unknown; error?: { message: string } }
  if (body.error) throw new Error(`Solana RPC ${method}: ${body.error.message}`)
  return body.result
}

export async function getSolBalance(address: string): Promise<number> {
  const result = await rpc('getBalance', [address, { commitment: 'confirmed' }]) as { value: number }
  return result.value / LAMPORTS_PER_SOL
}

/** Requests devnet airdrop and polls until confirmed. */
export async function airdropSol(address: string, amountSol = 1): Promise<string> {
  const sig = await rpc('requestAirdrop', [address, amountSol * LAMPORTS_PER_SOL]) as string
  await waitForSolanaTx(sig, { timeoutMs: 60_000 })
  return sig
}

export async function isSolanaDevnetReachable(): Promise<boolean> {
  try {
    await rpc('getVersion', [])
    return true
  } catch {
    return false
  }
}

/** Polls until the signature reaches confirmed or finalized status. */
export async function waitForSolanaTx(
  signature: string,
  { timeoutMs = 60_000, pollMs = 3_000 } = {},
): Promise<void> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const result = await rpc('getSignatureStatuses', [[signature]]) as {
      value: Array<{ confirmationStatus?: string } | null>
    }
    const conf = result.value[0]?.confirmationStatus
    if (conf === 'confirmed' || conf === 'finalized') return
    await new Promise((r) => setTimeout(r, pollMs))
  }
  throw new Error(`Solana tx ${signature} did not confirm within ${timeoutMs}ms`)
}

/** Polls on-chain balance until it reaches or exceeds expectedSol. */
export async function waitForSolBalance(
  address: string,
  expectedSol: number,
  { timeoutMs = 60_000, pollMs = 4_000 } = {},
): Promise<number> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const balance = await getSolBalance(address)
    if (balance >= expectedSol) return balance
    await new Promise((r) => setTimeout(r, pollMs))
  }
  throw new Error(`${address} balance did not reach ${expectedSol} SOL within ${timeoutMs}ms`)
}
