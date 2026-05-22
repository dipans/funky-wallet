import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useWalletStore } from '../store/walletStore'
import Dashboard from '../components/wallet/Dashboard'
import Activity from '../components/wallet/Activity'
import CreateAccount from '../components/auth/CreateAccount'
import SendTransaction from '../components/wallet/SendTransaction'
import Settings from '../components/wallet/Settings'
import { explorerTxUrl, explorerAddressUrl } from '../utils/explorer'

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return (
    <QueryClientProvider client={qc}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  )
}

describe('Dashboard', () => {
  beforeEach(() => {
    useWalletStore.setState({ accounts: [], activeAccount: null })
  })

  it('renders Portfolio heading and network cards', () => {
    render(<Dashboard />, { wrapper })
    expect(screen.getByRole('heading', { name: 'Portfolio' })).toBeTruthy()
    // Three selectable network cards
    expect(screen.getByRole('button', { name: /ethereum/i })).toBeTruthy()
    expect(screen.getByRole('button', { name: /solana/i })).toBeTruthy()
    expect(screen.getByRole('button', { name: /bitcoin/i })).toBeTruthy()
  })

  it('renders Send and New Account action buttons', () => {
    render(<Dashboard />, { wrapper })
    expect(screen.getByRole('button', { name: /send/i })).toBeTruthy()
    expect(screen.getByRole('button', { name: /new account/i })).toBeTruthy()
  })
})

describe('Activity', () => {
  it('renders filter buttons including RECEIVED', () => {
    render(<Activity />, { wrapper })
    expect(screen.getByRole('button', { name: 'ALL' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'PENDING' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'CONFIRMED' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'RECEIVED' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'FAILED' })).toBeTruthy()
  })
})

describe('CreateAccount', () => {
  it('renders blockchain family selector buttons', () => {
    render(<CreateAccount />, { wrapper })
    expect(screen.getByRole('button', { name: 'EVM' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'SOLANA' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'BITCOIN' })).toBeTruthy()
  })
})

describe('SendTransaction', () => {
  it('send button is disabled when fields are empty', () => {
    render(<SendTransaction />, { wrapper })
    const btn = screen.getByRole('button', { name: /send transaction/i })
    expect(btn.hasAttribute('disabled')).toBe(true)
  })
})

describe('Settings', () => {
  it('renders network option buttons', () => {
    render(<Settings />, { wrapper })
    expect(screen.getByRole('button', { name: 'ETHEREUM' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'SOLANA' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'BITCOIN' })).toBeTruthy()
  })
})

describe('explorer', () => {
  it('generates correct Hoodi tx URL', () => {
    expect(explorerTxUrl('0xABC', 'ETHEREUM')).toBe('https://hoodi.etherscan.io/tx/0xABC')
  })
  it('generates correct Solana devnet tx URL', () => {
    expect(explorerTxUrl('sig123', 'SOLANA')).toBe('https://explorer.solana.com/tx/sig123?cluster=devnet')
  })
  it('generates correct Bitcoin testnet address URL', () => {
    expect(explorerAddressUrl('tb1qABC', 'BITCOIN')).toBe('https://blockstream.info/testnet/address/tb1qABC')
  })
})
