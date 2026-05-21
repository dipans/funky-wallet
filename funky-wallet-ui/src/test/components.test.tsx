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
    // reset store so test-account seeding doesn't affect empty-state test
    useWalletStore.setState({ accounts: [], activeAccount: null })
  })

  it('shows create account prompt when no account is active', () => {
    render(<Dashboard />, { wrapper })
    expect(screen.getByText('No account yet.')).toBeTruthy()
    expect(screen.getByRole('button', { name: /create account/i })).toBeTruthy()
  })
})

describe('Activity', () => {
  it('renders filter buttons', () => {
    render(<Activity />, { wrapper })
    expect(screen.getByRole('button', { name: 'ALL' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'PENDING' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'CONFIRMED' })).toBeTruthy()
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
