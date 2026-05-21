import {
  createPublicClient,
  createWalletClient,
  http,
  parseEther,
  formatEther,
  type Chain,
} from 'viem'
import { privateKeyToAccount } from 'viem/accounts'

const GETH_RPC = process.env.GETH_RPC_URL ?? 'http://localhost:8545'
const CHAIN_ID = parseInt(process.env.GETH_CHAIN_ID ?? '1337')

// Local Geth chain definition
const localGeth = {
  id: CHAIN_ID,
  name: 'Local Geth',
  nativeCurrency: { name: 'Ether', symbol: 'ETH', decimals: 18 },
  rpcUrls: { default: { http: [GETH_RPC] } },
} satisfies Chain

// Pre-funded accounts from geth-dev/genesis.json
// These are standard Hardhat/Foundry derivation from "test test test... junk"
export const DEV_ACCOUNTS = {
  primary: {
    address: '0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266' as `0x${string}`,
    privateKey: '0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80' as `0x${string}`,
  },
  secondary: {
    address: '0x70997970C51812dc3A010C7d01b50e0d17dc79C8' as `0x${string}`,
    privateKey: '0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d' as `0x${string}`,
  },
  tertiary: {
    address: '0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC' as `0x${string}`,
    privateKey: '0x5de4111afa1a4b94908f83103eb1f1706367c2e68ca870fc3fb9a804cdab365a' as `0x${string}`,
  },
}

export const publicClient = createPublicClient({
  chain: localGeth,
  transport: http(GETH_RPC),
})

export async function getEthBalance(address: `0x${string}`): Promise<string> {
  const wei = await publicClient.getBalance({ address })
  return formatEther(wei)
}

export async function sendEth(
  from: { address: `0x${string}`; privateKey: `0x${string}` },
  to: `0x${string}`,
  amountEth: string
): Promise<`0x${string}`> {
  const account = privateKeyToAccount(from.privateKey)
  const client = createWalletClient({ account, chain: localGeth, transport: http(GETH_RPC) })
  return client.sendTransaction({ to, value: parseEther(amountEth) })
}

export async function waitForGethTx(hash: `0x${string}`): Promise<void> {
  await publicClient.waitForTransactionReceipt({ hash, timeout: 30_000 })
}

export async function isGethReachable(): Promise<boolean> {
  try {
    await publicClient.getBlockNumber()
    return true
  } catch {
    return false
  }
}
