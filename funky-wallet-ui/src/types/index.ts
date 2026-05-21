export type Network = 'ETHEREUM' | 'SOLANA' | 'BITCOIN'

export type TransactionStatus = 'PENDING' | 'CONFIRMED' | 'RECEIVED' | 'FAILED'

export type Environment  = 'MAINNET' | 'TESTNET' | 'DEVNET' | 'LOCAL'
export type NetworkType  = 'EVM' | 'SOLANA' | 'BITCOIN'

export interface Account {
  id: string
  address: string
  publicKey: string
  network: Network           // keep for backwards compat with signing
  networkType: NetworkType
  chainId: number
  chainName: string
  environment: Environment
  createdAt: string
}

export interface Balance {
  address: string
  network: Network
  amount: string
  symbol: string
  updatedAt: string
}

export interface Transaction {
  id: string
  hash: string
  fromAddress: string
  toAddress: string
  amount: string
  symbol: string
  network: Network
  status: TransactionStatus
  createdAt: string
  confirmedAt?: string
}

export interface CreateAccountResponse {
  account: Account
  mnemonic: string
}

export interface ApiError {
  status: number
  error: string
  message: string
  timestamp: string
}
