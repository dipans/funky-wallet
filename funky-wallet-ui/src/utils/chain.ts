import type { Environment, NetworkType } from '../types'

export interface ChainInfo {
  chainId: number
  name: string
  symbol: string
  networkType: NetworkType
  environment: Environment
  explorerBase: string
}

export const SUPPORTED_CHAINS: ChainInfo[] = [
  { chainId: 1,        name: 'Ethereum Mainnet', symbol: 'ETH',  networkType: 'EVM',     environment: 'MAINNET', explorerBase: 'https://etherscan.io' },
  { chainId: 560048,   name: 'Ethereum Hoodi',  symbol: 'ETH',  networkType: 'EVM',     environment: 'TESTNET', explorerBase: 'https://hoodi.etherscan.io' },
  { chainId: 137,      name: 'Polygon',         symbol: 'MATIC', networkType: 'EVM',    environment: 'MAINNET', explorerBase: 'https://polygonscan.com' },
  { chainId: 1337,     name: 'Ethereum Local',  symbol: 'ETH',  networkType: 'EVM',     environment: 'LOCAL',   explorerBase: '' },
  { chainId: 900001,   name: 'Solana',          symbol: 'SOL',  networkType: 'SOLANA',  environment: 'DEVNET',  explorerBase: 'https://explorer.solana.com' },
  { chainId: 900002,   name: 'Bitcoin',         symbol: 'BTC',  networkType: 'BITCOIN', environment: 'TESTNET', explorerBase: 'https://blockstream.info/testnet' },
]

export const ENV_COLORS: Record<Environment, { bg: string; text: string }> = {
  MAINNET: { bg: 'rgba(255, 77,  77,  0.15)', text: '#ff4d4d' },
  TESTNET: { bg: 'rgba(200, 241, 53,  0.15)', text: '#c8f135' },
  DEVNET:  { bg: 'rgba(77,  184, 255, 0.15)', text: '#4db8ff' },
  LOCAL:   { bg: 'rgba(160, 160, 160, 0.15)', text: '#aaaaaa' },
}

export function getChain(chainId: number): ChainInfo | undefined {
  return SUPPORTED_CHAINS.find((c) => c.chainId === chainId)
}

export function chainsByNetworkType(type: NetworkType): ChainInfo[] {
  return SUPPORTED_CHAINS.filter((c) => c.networkType === type)
}
