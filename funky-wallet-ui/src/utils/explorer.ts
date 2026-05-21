import type { Network } from '../types'

const EXPLORER: Record<Network, { base: string; txPath: string; addressPath: string }> = {
  ETHEREUM: {
    base: 'https://hoodi.etherscan.io',
    txPath: '/tx/',
    addressPath: '/address/',
  },
  SOLANA: {
    base: 'https://explorer.solana.com',
    txPath: '/tx/',
    addressPath: '/address/',
  },
  BITCOIN: {
    base: 'https://blockstream.info/testnet',
    txPath: '/tx/',
    addressPath: '/address/',
  },
}

const SOLANA_SUFFIX = '?cluster=devnet'

export function explorerTxUrl(hash: string, network: Network): string {
  const e = EXPLORER[network]
  const suffix = network === 'SOLANA' ? SOLANA_SUFFIX : ''
  return `${e.base}${e.txPath}${hash}${suffix}`
}

export function explorerAddressUrl(address: string, network: Network): string {
  const e = EXPLORER[network]
  const suffix = network === 'SOLANA' ? SOLANA_SUFFIX : ''
  return `${e.base}${e.addressPath}${address}${suffix}`
}
